/*
 * Copyright (C) 2021 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.oauth2;

import static org.springframework.security.config.Customizer.withDefaults;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoderFactory;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * OAuth2 security configuration for geOrchestra's Gateway.
 * <p>
 * This configuration enables OAuth2 authentication, OpenID Connect integration,
 * and HTTP proxy support for OAuth2 clients. It includes support for OAuth2
 * login, JWT decoding, role mapping, and customized logout handling.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ OAuth2ProxyConfigProperties.class, OpenIdConnectCustomClaimsConfigProperties.class,
        GeorchestraGatewaySecurityConfigProperties.class, ExtendedOAuth2ClientProperties.class })
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class OAuth2Configuration {

    private @Value("${georchestra.gateway.logoutUrl:/?logout}") String georchestraLogoutUrl;

    /**
     * Customizer for enabling OAuth2 authentication in the Spring Security filter
     * chain.
     */
    public static final class OAuth2AuthenticationCustomizer implements ServerHttpSecurityCustomizer {
        @Override
        public void customize(ServerHttpSecurity http) {
            log.info("Enabling authentication support using an OAuth 2.0 and/or OpenID Connect 1.0 Provider");
            http.oauth2Login(withDefaults());
        }
    }

    /**
     * Configures the OIDC logout handler to handle end-session requests properly.
     *
     * @param clientRegistrationRepository The repository of registered OAuth2
     *                                     clients.
     * @param properties                   The extended OAuth2 client properties
     *                                     including logout endpoints.
     * @return A configured {@link ServerLogoutSuccessHandler} that initiates OIDC
     *         logout.
     */
    @Bean
    @Profile("!test")
    ServerLogoutSuccessHandler oidcLogoutSuccessHandler(
            InMemoryReactiveClientRegistrationRepository clientRegistrationRepository,
            ExtendedOAuth2ClientProperties properties) {
        clientRegistrationRepository.forEach(client -> {
            if (client.getProviderDetails().getConfigurationMetadata().isEmpty()
                    && properties.getProvider().get(client.getRegistrationId()) != null
                    && properties.getProvider().get(client.getRegistrationId()).getEndSessionUri() != null) {
                try {
                    Field field = ClientRegistration.ProviderDetails.class.getDeclaredField("configurationMetadata");
                    field.setAccessible(true);
                    field.set(client.getProviderDetails(), Collections.singletonMap("end_session_endpoint",
                            properties.getProvider().get(client.getRegistrationId()).getEndSessionUri()));
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        OidcClientInitiatedServerLogoutSuccessHandler logoutHandler = new OidcClientInitiatedServerLogoutSuccessHandler(
                clientRegistrationRepository);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/login?logout");
        logoutHandler.setLogoutSuccessUrl(URI.create(georchestraLogoutUrl));
        return logoutHandler;
    }

    /**
     * Registers a Spring Security customizer to enable OAuth2 login.
     *
     * @return A {@link ServerHttpSecurityCustomizer} instance.
     */
    @Bean
    ServerHttpSecurityCustomizer oauth2LoginEnablingCustomizer() {
        return new OAuth2AuthenticationCustomizer();
    }

    /**
     * Provides a default OAuth2 user mapper for mapping authentication tokens to
     * geOrchestra users.
     *
     * @return An instance of {@link OAuth2UserMapper}.
     */
    @Bean
    OAuth2UserMapper oAuth2GeorchestraUserUserMapper() {
        return new OAuth2UserMapper();
    }

    /**
     * Provides a custom OpenID Connect user mapper for processing non-standard
     * claims.
     *
     * @param nonStandardClaimsConfig Configuration for custom OIDC claims.
     * @return An instance of {@link OpenIdConnectUserMapper}.
     */
    @Bean
    OpenIdConnectUserMapper openIdConnectGeorchestraUserUserMapper(
            OpenIdConnectCustomClaimsConfigProperties nonStandardClaimsConfig) {
        return new OpenIdConnectUserMapper(nonStandardClaimsConfig);
    }

    /**
     * Configures the OAuth2 access token response client to support an HTTP proxy
     * if enabled.
     *
     * @param oauth2WebClient The WebClient configured for OAuth2 communication.
     * @return A configured instance of
     *         {@link ReactiveOAuth2AccessTokenResponseClient}.
     */
    @Bean
    ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> reactiveOAuth2AccessTokenResponseClient(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {
        WebClientReactiveAuthorizationCodeTokenResponseClient client = new WebClientReactiveAuthorizationCodeTokenResponseClient();
        client.setWebClient(oauth2WebClient);
        return client;
    }

    /**
     * Creates a JWT decoder factory that supports OAuth2 authentication and an
     * optional HTTP proxy.
     *
     * @param oauth2WebClient The WebClient used to fetch JWT keys if needed.
     * @return A {@link ReactiveJwtDecoderFactory} configured for OAuth2
     *         authentication.
     */
    @Bean
    ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {
        return clientRegistration -> token -> {
            try {
                JWT parsedJwt = JWTParser.parse(token);
                MacAlgorithm macAlgorithm = MacAlgorithm.from(parsedJwt.getHeader().getAlgorithm().getName());
                SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm
                        .from(parsedJwt.getHeader().getAlgorithm().getName());
                NimbusReactiveJwtDecoder jwtDecoder;
                if (macAlgorithm != null) {
                    var secretKey = clientRegistration.getClientSecret().getBytes(StandardCharsets.UTF_8);
                    if (secretKey.length < 64) {
                        secretKey = Arrays.copyOf(secretKey, 64);
                    }
                    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, macAlgorithm.getName());
                    jwtDecoder = NimbusReactiveJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm(macAlgorithm)
                            .build();
                } else if (signatureAlgorithm != null) {
                    jwtDecoder = NimbusReactiveJwtDecoder
                            .withJwkSetUri(clientRegistration.getProviderDetails().getJwkSetUri())
                            .jwsAlgorithm(signatureAlgorithm).webClient(oauth2WebClient).build();
                } else {
                    jwtDecoder = NimbusReactiveJwtDecoder
                            .withJwkSetUri(clientRegistration.getProviderDetails().getJwkSetUri())
                            .webClient(oauth2WebClient).build();
                }
                return jwtDecoder.decode(token).map(jwt -> new Jwt(jwt.getTokenValue(), jwt.getIssuedAt(),
                        jwt.getExpiresAt(), jwt.getHeaders(), removeNullClaims(jwt.getClaims())));
            } catch (ParseException exception) {
                throw new BadJwtException("Failed to decode the JWT token", exception);
            }
        };
    }

    /**
     * Removes null claims from JWT tokens to avoid Spring OAuth2 processing issues.
     */
    private Map<String, Object> removeNullClaims(Map<String, Object> claims) {
        return claims.entrySet().stream().filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Provides a default implementation of {@link DefaultReactiveOAuth2UserService}
     * for handling OAuth2 user authentication.
     * <p>
     * This service is responsible for retrieving user details from the OAuth2
     * provider and processing user information. The configured {@link WebClient} is
     * used to make requests to the provider's user info endpoint, allowing it to
     * support an HTTP proxy if configured.
     *
     * @param oauth2WebClient The WebClient instance configured for OAuth2 requests.
     * @return A configured instance of {@link DefaultReactiveOAuth2UserService}.
     */
    @Bean
    DefaultReactiveOAuth2UserService reactiveOAuth2UserService(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        DefaultReactiveOAuth2UserService service = new DefaultReactiveOAuth2UserService();
        service.setWebClient(oauth2WebClient);
        return service;
    }

    /**
     * Provides a customized {@link OidcReactiveOAuth2UserService} for handling
     * OpenID Connect (OIDC) authentication.
     * <p>
     * This service extends the default OAuth2 user service to support OIDC-specific
     * claims and processing. It delegates OAuth2 authentication to the provided
     * {@link DefaultReactiveOAuth2UserService}.
     *
     * @param oauth2Delegate The default OAuth2 user service used for retrieving
     *                       user information.
     * @return A configured instance of {@link OidcReactiveOAuth2UserService}.
     */
    @Bean
    OidcReactiveOAuth2UserService oidcReactiveOAuth2UserService(DefaultReactiveOAuth2UserService oauth2Delegate) {
        OidcReactiveOAuth2UserService oidUserService = new OidcReactiveOAuth2UserService();
        oidUserService.setOauth2UserService(oauth2Delegate);
        return oidUserService;
    }

    /**
     * Configures a WebClient for OAuth2 authentication requests, supporting HTTP
     * proxy settings if enabled.
     *
     * @param proxyConfig The proxy configuration properties.
     * @return A configured {@link WebClient} instance.
     */
    @Bean("oauth2WebClient")
    WebClient oauth2WebClient(OAuth2ProxyConfigProperties proxyConfig) {
        HttpClient httpClient = HttpClient.create();
        if (proxyConfig.isEnabled()) {
            log.info("OAuth2 client will use HTTP proxy {}:{}", proxyConfig.getHost(), proxyConfig.getPort());
            httpClient = httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP).host(proxyConfig.getHost())
                    .port(proxyConfig.getPort()).username(proxyConfig.getUsername())
                    .password(user -> proxyConfig.getPassword()));
        } else {
            log.info("OAuth2 client will use system-defined HTTP proxy settings if available.");
            httpClient = httpClient.proxyWithSystemProperties();
        }
        ExchangeFilterFunction handleJwtContentType = OpenIdHelper.transformJWTClientResponseToJSON();
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(handleJwtContentType).build();
    }
}
