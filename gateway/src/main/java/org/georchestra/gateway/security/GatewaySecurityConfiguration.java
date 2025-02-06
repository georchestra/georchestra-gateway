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
package org.georchestra.gateway.security;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.georchestra.gateway.model.GatewayConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.LogoutSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link Configuration} to initialize the Gateway's
 * {@link SecurityWebFilterChain} during application start up, such as
 * establishing path based access rules, configuring authentication providers,
 * etc.
 * <p>
 * Note this configuration does very little by itself. Instead, it relies on
 * available beans implementing the {@link ServerHttpSecurityCustomizer}
 * extension point to tweak the {@link ServerHttpSecurity} as appropriate in a
 * decoupled way.
 * 
 * @see ServerHttpSecurityCustomizer
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties({ GatewayConfigProperties.class })
@Slf4j(topic = "org.georchestra.gateway.security")
public class GatewaySecurityConfiguration {

    @Autowired(required = false)
    ServerLogoutSuccessHandler oidcLogoutSuccessHandler;

    private @Value("${georchestra.gateway.logoutUrl:/?logout}") String georchestraLogoutUrl;

    /**
     * Relies on available {@link ServerHttpSecurityCustomizer} extensions to
     * configure the different aspects of the {@link ServerHttpSecurity} used to
     * {@link ServerHttpSecurity#build build} the {@link SecurityWebFilterChain}.
     * <p>
     * Disables appending default response headers as far as the regular
     * Spring-Security is concerned. This way, we let Spring Cloud Gateway control
     * their behavior. Otherwise the config property
     * {@literal spring.cloud.gateway.filter.secure-headers.disable: x-frame-options}
     * has no effect.
     * <p>
     * Note also {@literal spring.cloud.gateway.default-filters} must contain the
     * {@literal SecureHeaders} filter.
     * <p>
     * Finally, note
     * {@literal spring.cloud.gateway.default-filters: x-frame-options} won't
     * prevent downstream services so provide their own header.
     * <p>
     * The following are the default headers suppressed here:
     * 
     * <pre>
     * <code>
     * Cache-Control: no-cache, no-store, max-age=0, must-revalidate
     * Pragma: no-cache
     * Expires: 0
     * X-Content-Type-Options: nosniff
     * Strict-Transport-Security: max-age=31536000 ; includeSubDomains
     * X-Frame-Options: DENY
     * X-XSS-Protection: 1; mode=block
     * </code>
     * </pre>
     */
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            List<ServerHttpSecurityCustomizer> customizers) throws Exception {

        log.info("Initializing security filter chain...");

        // disable CSRF protection, considering it will be managed
        // by proxified webapps, not the gateway.
        http.csrf().disable();

        // disable default response headers. See comment in the method's javadoc
        http.headers().disable();

        // custom handling for forbidden error
        http.exceptionHandling().accessDeniedHandler(new CustomAccessDeniedHandler());

        http.formLogin()
                .authenticationFailureHandler(new ExtendedRedirectServerAuthenticationFailureHandler("login?error"))
                .loginPage("/login");

        sortedCustomizers(customizers).forEach(customizer -> {
            log.debug("Applying security customizer {}", customizer.getName());
            customizer.customize(http);
        });

        log.info("Security filter chain initialized");

        RedirectServerLogoutSuccessHandler defaultRedirect = new RedirectServerLogoutSuccessHandler();
        defaultRedirect.setLogoutSuccessUrl(URI.create(georchestraLogoutUrl));

        LogoutSpec logoutUrl = http.formLogin().loginPage("/login").and().logout()
                .requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/logout"))
                .logoutSuccessHandler(oidcLogoutSuccessHandler != null ? oidcLogoutSuccessHandler : defaultRedirect);

        return logoutUrl.and().build();
    }

    private Stream<ServerHttpSecurityCustomizer> sortedCustomizers(List<ServerHttpSecurityCustomizer> customizers) {
        return customizers.stream().sorted((c1, c2) -> Integer.compare(c1.getOrder(), c2.getOrder()));
    }

    @Bean
    GeorchestraUserMapper georchestraUserResolver(List<GeorchestraUserMapperExtension> resolvers,
            List<GeorchestraUserCustomizerExtension> customizers) {
        return new GeorchestraUserMapper(resolvers, customizers);
    }

    @Bean
    ResolveGeorchestraUserGlobalFilter resolveGeorchestraUserGlobalFilter(GeorchestraUserMapper resolver) {
        return new ResolveGeorchestraUserGlobalFilter(resolver);
    }

    /**
     * Extension to make {@link GeorchestraUserMapper} append user roles based on
     * {@link GatewayConfigProperties#getRolesMappings()}
     */
    @Bean
    RolesMappingsUserCustomizer rolesMappingsUserCustomizer(GatewayConfigProperties config) {
        Map<String, List<String>> rolesMappings = config.getRolesMappings();
        log.info("Creating {}", RolesMappingsUserCustomizer.class.getSimpleName());
        return new RolesMappingsUserCustomizer(rolesMappings);
    }

}
