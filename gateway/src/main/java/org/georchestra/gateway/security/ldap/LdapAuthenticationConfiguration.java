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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.ldap;

import java.util.List;
import java.util.stream.Collectors;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ServerHttpSecurityCustomizer} to enable LDAP based authentication and
 * authorization across multiple LDAP databases.
 * <p>
 * This configuration sets up the required beans for spring-based LDAP
 * authentication and authorization, using
 * {@link GeorchestraGatewaySecurityConfigProperties} to get the
 * {@link GeorchestraGatewaySecurityConfigProperties#getUrl() connection URL}
 * and the {@link GeorchestraGatewaySecurityConfigProperties#getBaseDn() base
 * DN}.
 * <p>
 * As a result, the {@link ServerHttpSecurity} will have HTTP-Basic
 * authentication enabled and {@link ServerHttpSecurity#formLogin() form login}
 * set up.
 * <p>
 * Upon successful authentication, the corresponding {@link Authentication} with
 * an {@link LdapUserDetails} as {@link Authentication#getPrincipal() principal}
 * and the roles extracted from LDAP as {@link Authentication#getAuthorities()
 * authorities}, will be set as the security context's
 * {@link SecurityContext#getAuthentication() authentication} property.
 * <p>
 * Note however, this may not be enough information to convey
 * geOrchestra-specific HTTP request headers to backend services, depending on
 * the matching gateway-route configuration. See
 * {@link ExtendedLdapAuthenticationConfiguration} for further details.
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

    public static final class LDAPAuthenticationCustomizer implements ServerHttpSecurityCustomizer {
        public @Override void customize(ServerHttpSecurity http) {
            log.info("Enabling HTTP Basic authentication support for LDAP");
            http.httpBasic().and().formLogin();
        }
    }

    @Bean
    public ServerHttpSecurityCustomizer ldapHttpBasicLoginFormEnablerExtension() {
        return new LDAPAuthenticationCustomizer();
    }

    @Bean
    public AuthenticationWebFilter ldapAuthenticationWebFilter(
            ReactiveAuthenticationManager ldapAuthenticationManager) {

        AuthenticationWebFilter ldapAuthFilter = new AuthenticationWebFilter(ldapAuthenticationManager);
        ldapAuthFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/auth/login"));
        return ldapAuthFilter;
    }

    @Bean
    public ReactiveAuthenticationManager ldapAuthenticationManager(List<BasicLdapAuthenticationProvider> basic,
            List<GeorchestraLdapAuthenticationProvider> extended) {

        List<AuthenticationProvider> flattened = Stream.concat(basic.stream(), extended.stream())
                .map(AuthenticationProvider.class::cast).collect(Collectors.toList());

        if (flattened.isEmpty())
            return null;
        ProviderManager providerManager = new ProviderManager(flattened);
        return new ReactiveAuthenticationManagerAdapter(providerManager);
    }
}
