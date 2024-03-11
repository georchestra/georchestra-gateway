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
package org.georchestra.gateway.security.ldap.basic;

import java.util.List;
import java.util.stream.Collectors;

import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapAuthenticationConfiguration;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

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
 * @see ExtendedLdapAuthenticationConfiguration
 * @see GeorchestraGatewaySecurityConfigProperties
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
@Slf4j(topic = "org.georchestra.gateway.security.ldap.basic")
public class BasicLdapAuthenticationConfiguration {

    @Bean
    public BasicLdapAuthenticatedUserMapper ldapAuthenticatedUserMapper(List<LdapServerConfig> enabledConfigs) {
        return enabledConfigs.isEmpty() ? null : new BasicLdapAuthenticatedUserMapper();
    }

    @Bean
    List<LdapServerConfig> enabledSimpleLdapConfigs(GeorchestraGatewaySecurityConfigProperties config) {
        return config.simpleEnabled();
    }

    @Bean
    List<BasicLdapAuthenticationProvider> ldapAuthenticationProviders(List<LdapServerConfig> configs) {
        return configs.stream().map(this::createLdapProvider).collect(Collectors.toList());
    }

    private BasicLdapAuthenticationProvider createLdapProvider(LdapServerConfig config) {
        log.info("Creating LDAP AuthenticationProvider {} with URL {}", config.getName(), config.getUrl());

        try {
            LdapAuthenticationProvider provider = new LdapAuthenticatorProviderBuilder()//
                    .url(config.getUrl())//
                    .baseDn(config.getBaseDn())//
                    .userSearchBase(config.getUsersRdn())//
                    .userSearchFilter(config.getUsersSearchFilter())//
                    .rolesSearchBase(config.getRolesRdn())//
                    .rolesSearchFilter(config.getRolesSearchFilter())//
                    .adminDn(config.getAdminDn().orElse(null))//
                    .adminPassword(config.getAdminPassword().orElse(null))//
                    .returningAttributes(config.getReturningAttributes()).build();
            return new BasicLdapAuthenticationProvider(config.getName(), provider);
        } catch (RuntimeException e) {
            throw new BeanCreationException(
                    "Error creating LDAP Authentication Provider for config " + config + ": " + e.getMessage(), e);
        }
    }
}
