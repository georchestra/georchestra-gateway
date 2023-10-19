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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.oauth2;

import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.georchestra.gateway.security.oauth2.OAuth2ConfigurationProperties.OAuth2ProxyConfigProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayReactiveOAuth2AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthorizationCodeReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoderFactory;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ OAuth2ConfigurationProperties.class })
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class OAuth2Configuration {

    public static final class OAuth2AuthenticationCustomizer implements ServerHttpSecurityCustomizer {
        private WebClient oauth2WebClient;

        public OAuth2AuthenticationCustomizer(WebClient oauth2WebClient) {
            this.oauth2WebClient = oauth2WebClient;
        }

        public @Override void customize(ServerHttpSecurity http) {
            log.info("Enabling authentication support using an OAuth 2.0 and/or OpenID Connect 1.0 Provider");

//            http.oauth2Client();
            http.oauth2Client(c -> {
                c.authenticationManager(getClientAuthenticationManager());
            });
            http.oauth2Login();
        }

        private ReactiveAuthenticationManager getClientAuthenticationManager() {
            WebClientReactiveAuthorizationCodeTokenResponseClient accessTokenResponseClient = new WebClientReactiveAuthorizationCodeTokenResponseClient();
            accessTokenResponseClient.setWebClient(oauth2WebClient);
            return new OAuth2AuthorizationCodeReactiveAuthenticationManager(accessTokenResponseClient);
        }
    }

    @Bean
    ServerHttpSecurityCustomizer oauth2LoginEnablingCustomizer(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {
        return new OAuth2AuthenticationCustomizer(oauth2WebClient);
    }

    @Bean
    OAuth2UserMapper oAuth2GeorchestraUserUserMapper(OAuth2ConfigurationProperties config) {
        return new OAuth2UserMapper(config);
    }

    @Bean
    OpenIdConnectUserMapper openIdConnectGeorchestraUserUserMapper(OAuth2ConfigurationProperties config) {
        return new OpenIdConnectUserMapper(config);
    }

    /**
     * Configures the OAuth2 client to use the HTTP proxy if enabled, by means of
     * {@linkplain #oauth2WebClient}
     * <p>
     * {@link OAuth2LoginSpec ServerHttpSecurity$OAuth2LoginSpec#createDefault()}
     * will return a {@link ReactiveAuthenticationManager} by first looking up a
     * {@link ReactiveOAuth2AccessTokenResponseClient
     * ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>}
     * in the application context, and creating a default one if none is found.
     * <p>
     * We provide such bean here to have it configured with an {@link WebClient HTTP
     * client} that will use the {@link OAuth2ProxyConfigProperties configured} HTTP
     * proxy.
     */
    @Bean
    public ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> reactiveOAuth2AccessTokenResponseClient(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        WebClientReactiveAuthorizationCodeTokenResponseClient client = new WebClientReactiveAuthorizationCodeTokenResponseClient();
        client.setWebClient(oauth2WebClient);
        return client;
    }

    /**
     * Override the {@link ReactiveClientRegistrationRepository} set up on
     * {@link GatewayReactiveOAuth2AutoConfiguration}
     * 
     * @return
     */
    @Primary
    @Bean
    public ReactiveOAuth2AuthorizedClientManager gatewayOverrideReactiveOAuth2AuthorizedClientManager(

            ReactiveClientRegistrationRepository clientRegistrationRepository,

            ServerOAuth2AuthorizedClientRepository authorizedClientRepository,

            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        WebClientReactiveRefreshTokenTokenResponseClient refreshTokenTokenResponseClient = new WebClientReactiveRefreshTokenTokenResponseClient();
        refreshTokenTokenResponseClient.setWebClient(oauth2WebClient);

        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider;

        authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder//
                .builder()//
                .authorizationCode()//
                .refreshToken(configurer -> configurer.accessTokenResponseClient(refreshTokenTokenResponseClient))//
                .build();

        DefaultReactiveOAuth2AuthorizedClientManager authorizedClientManager;
        authorizedClientManager = new DefaultReactiveOAuth2AuthorizedClientManager(//
                clientRegistrationRepository, //
                authorizedClientRepository);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    /**
     * Custom JWT decoder factory to use the web client that can be set up to go
     * through an HTTP proxy
     */
    @Bean
    public ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {
        return (clientRegistration) -> {
            ProviderDetails providerDetails = clientRegistration.getProviderDetails();
            String jwkSetUri = providerDetails.getJwkSetUri();
            return NimbusReactiveJwtDecoder//
                    .withJwkSetUri(jwkSetUri)//
                    .jwsAlgorithm(SignatureAlgorithm.RS256)//
                    .webClient(oauth2WebClient)//
                    .build();
        };
    }

    @Bean
    public DefaultReactiveOAuth2UserService reactiveOAuth2UserService(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        DefaultReactiveOAuth2UserService service = new DefaultReactiveOAuth2UserService();
        service.setWebClient(oauth2WebClient);
        return service;
    };

    @Bean
    public OidcReactiveOAuth2UserService oidcReactiveOAuth2UserService(
            DefaultReactiveOAuth2UserService oauth2Delegate) {
        OidcReactiveOAuth2UserService oidUserService = new OidcReactiveOAuth2UserService();
        oidUserService.setOauth2UserService(oauth2Delegate);
        return oidUserService;
    };

    /**
     * {@link WebClient} to use when performing HTTP POST requests to the OAuth2
     * service providers, that can be configured to use an HTTP proxy through the
     * {@link OAuth2ProxyConfigProperties} configuration properties.
     *
     * @param proxyConfig defines the HTTP proxy settings specific for the OAuth2
     *                    client. If not
     *                    {@link OAuth2ProxyConfigProperties#isEnabled() enabled},
     *                    the {@code WebClient} will use the proxy configured
     *                    through System properties ({@literal http(s).proxyHost}
     *                    and {@literal http(s).proxyPort}), if any.
     */
//    @Primary
    @Bean("oauth2WebClient")
    public WebClient oauth2WebClient(OAuth2ConfigurationProperties config) {
        return new ProxyAwareWebClient(config.getProxy());
    }
}
