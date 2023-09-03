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

import lombok.extern.slf4j.Slf4j;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.security.UserMapperImpl;
import org.georchestra.ds.security.UsersApiImpl;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.ds.users.UserRule;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import reactor.core.publisher.Mono;
import org.georchestra.security.model.GeorchestraUser;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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

    /**
     * Relies on available {@link ServerHttpSecurityCustomizer} extensions to
     * configure the different aspects of the {@link ServerHttpSecurity} used to
     * {@link ServerHttpSecurity#build build} the {@link SecurityWebFilterChain}.
     */

    @Autowired(required = false)
    ServerLogoutSuccessHandler oidcLogoutSuccessHandler;

    @Autowired(required = false)
    private AccountDao accountDao;

    @Autowired(required = false)
    private RoleDao roleDao;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            List<org.georchestra.gateway.security.ServerHttpSecurityCustomizer> customizers) throws Exception {

        log.info("Initializing security filter chain...");
        // disable csrf and cors or the websocket connection gets a 403 Forbidden.
        // Revisit.
        log.info("CSRF and CORS disabled. Revisit how they interfer with Websockets proxying.");
        http.csrf().disable().cors().disable();

        http.formLogin()
                .authenticationFailureHandler(new ExtendedRedirectServerAuthenticationFailureHandler("login?error"))
                .loginPage("/login");

        sortedCustomizers(customizers).forEach(customizer -> {
            log.debug("Applying security customizer {}", customizer.getName());
            customizer.customize(http);
        });

        log.info("Security filter chain initialized");

        if (oidcLogoutSuccessHandler != null) {
            return http.formLogin().loginPage("/login").and().logout().logoutUrl("/logout")
                    .logoutSuccessHandler(oidcLogoutSuccessHandler).and().build();
        } else {
            return http.formLogin().loginPage("/login").and().logout().logoutUrl("/logout").and().build();
        }
    }

    private Stream<ServerHttpSecurityCustomizer> sortedCustomizers(List<ServerHttpSecurityCustomizer> customizers) {
        return customizers.stream().sorted((c1, c2) -> Integer.compare(c1.getOrder(), c2.getOrder()));
    }

    public @Bean GeorchestraUserMapper georchestraUserResolver(List<GeorchestraUserMapperExtension> resolvers,
            List<GeorchestraUserCustomizerExtension> customizers) {
        return new GeorchestraUserMapper(resolvers, customizers);
    }

    public @Bean ResolveGeorchestraUserGlobalFilter resolveGeorchestraUserGlobalFilter(GeorchestraUserMapper resolver) {
        return new ResolveGeorchestraUserGlobalFilter(resolver);
    }

    public @Bean ResolveApacheGeorchestraUserFilter resolveApacheGeorchestraUserFilter() {
        return new ResolveApacheGeorchestraUserFilter();
    }

    /**
     * Extension to make {@link GeorchestraUserMapper} append user roles based on
     * {@link GatewayConfigProperties#getRolesMappings()}
     */
    public @Bean RolesMappingsUserCustomizer rolesMappingsUserCustomizer(GatewayConfigProperties config) {
        Map<String, List<String>> rolesMappings = config.getRolesMappings();
        log.info("Creating {}", RolesMappingsUserCustomizer.class.getSimpleName());
        return new RolesMappingsUserCustomizer(rolesMappings);
    }

}
