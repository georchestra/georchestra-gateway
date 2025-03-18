/*
 * Copyright (C) 2022 by the geOrchestra PSC
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
package org.georchestra.gateway.security.ldap;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import java.util.stream.Stream;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.georchestra.gateway.security.ldap.basic.BasicLdapAuthenticationConfiguration;
import org.georchestra.gateway.security.ldap.basic.BasicLdapAuthenticationProvider;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapAuthenticationConfiguration;
import org.georchestra.gateway.security.ldap.extended.GeorchestraLdapAuthenticationProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerAdapter;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ServerHttpSecurityCustomizer} to enable LDAP-based authentication and
 * authorization across multiple LDAP databases.
 * <p>
 * This configuration sets up the required beans for Spring-based LDAP
 * authentication and authorization, using
 * {@link GeorchestraGatewaySecurityConfigProperties} to get the
 * {@link GeorchestraGatewaySecurityConfigProperties#getUrl() connection URL}
 * and the {@link GeorchestraGatewaySecurityConfigProperties#getBaseDn() base
 * DN}.
 * </p>
 * <p>
 * As a result, the {@link ServerHttpSecurity} will have HTTP Basic
 * authentication enabled, as well as
 * {@link ServerHttpSecurity#formLogin(withDefaults()) form login}.
 * </p>
 * <p>
 * Upon successful authentication, an {@link Authentication} instance will be
 * set in the {@link org.springframework.security.core.context.SecurityContext
 * SecurityContext} with an
 * {@link org.springframework.security.ldap.userdetails.LdapUserDetails
 * LdapUserDetails} as the principal and roles extracted from LDAP as
 * authorities.
 * </p>
 * <p>
 * However, depending on the configured gateway routes, this may not be enough
 * information to convey geOrchestra-specific HTTP request headers to backend
 * services. See {@link ExtendedLdapAuthenticationConfiguration} for further
 * details.
 * </p>
 *
 * @see GeorchestraGatewaySecurityConfigProperties
 * @see BasicLdapAuthenticationConfiguration
 * @see ExtendedLdapAuthenticationConfiguration
 */
@Configuration(proxyBeanMethods = true)
@EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
@Import({ //
        BasicLdapAuthenticationConfiguration.class, //
        ExtendedLdapAuthenticationConfiguration.class })
@Slf4j(topic = "org.georchestra.gateway.security.ldap")
public class LdapAuthenticationConfiguration {

    /**
     * Enables HTTP Basic authentication and form login for LDAP authentication.
     */
    public static final class LDAPAuthenticationCustomizer implements ServerHttpSecurityCustomizer {
        /**
         * Configures HTTP Basic authentication and form login.
         *
         * @param http the {@link ServerHttpSecurity} instance
         */
        public @Override void customize(ServerHttpSecurity http) {
            log.info("Enabling HTTP Basic authentication support for LDAP");
            http.httpBasic(withDefaults()).formLogin(withDefaults());
        }
    }

    /**
     * Registers an LDAP authentication customizer to enable HTTP Basic and form
     * login.
     *
     * @return a {@link ServerHttpSecurityCustomizer} for LDAP authentication
     */
    @Bean
    ServerHttpSecurityCustomizer ldapHttpBasicLoginFormEnablerExtension() {
        return new LDAPAuthenticationCustomizer();
    }

    /**
     * Creates an {@link AuthenticationWebFilter} for LDAP authentication.
     * <p>
     * This filter is triggered when requests match the {@code /auth/login} path.
     * </p>
     *
     * @param ldapAuthenticationManager the {@link ReactiveAuthenticationManager}
     *                                  for LDAP authentication
     * @return an {@link AuthenticationWebFilter} configured for LDAP authentication
     */
    @Bean
    AuthenticationWebFilter ldapAuthenticationWebFilter(ReactiveAuthenticationManager ldapAuthenticationManager) {
        AuthenticationWebFilter ldapAuthFilter = new AuthenticationWebFilter(ldapAuthenticationManager);
        ldapAuthFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/auth/login"));
        return ldapAuthFilter;
    }

    /**
     * Creates an LDAP authentication manager that combines multiple authentication
     * providers.
     * <p>
     * This manager supports both basic and extended LDAP authentication providers.
     * If no providers are available, {@code null} is returned.
     * </p>
     *
     * @param basic    a list of {@link BasicLdapAuthenticationProvider} instances
     * @param extended a list of {@link GeorchestraLdapAuthenticationProvider}
     *                 instances
     * @return a {@link ReactiveAuthenticationManager} if providers are available,
     *         otherwise {@code null}
     */
    @Bean
    ReactiveAuthenticationManager ldapAuthenticationManager(List<BasicLdapAuthenticationProvider> basic,
            List<GeorchestraLdapAuthenticationProvider> extended) {

        List<AuthenticationProvider> flattened = Stream.concat(basic.stream(), extended.stream())
                .map(AuthenticationProvider.class::cast).toList();

        if (flattened.isEmpty()) {
            log.warn("No LDAP authentication providers configured.");
            return null;
        }

        ProviderManager providerManager = new ProviderManager(flattened);
        return new ReactiveAuthenticationManagerAdapter(providerManager);
    }
}
