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
package org.georchestra.gateway.security.accessrules;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.RoleBasedAccessRule;
import org.georchestra.gateway.model.Service;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.RolePrefixGeorchestraUserCustomizerExtension;
import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec.Access;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * {@link ServerHttpSecurityCustomizer} to apply {@link RoleBasedAccessRule ROLE
 * based access rules} at startup.
 * <p>
 * The access rules are configured as
 * {@link GatewayConfigProperties#getGlobalAccessRules() global rules}, and
 * overridden if needed on a per-service basis from
 * {@link GatewayConfigProperties#getServices()}.
 *
 * @see RoleBasedAccessRule
 * @see GatewayConfigProperties#getGlobalAccessRules()
 * @see Service#getAccessRules()
 */
@RequiredArgsConstructor
@Slf4j(topic = "org.georchestra.gateway.config.security.accessrules")
public class AccessRulesCustomizer implements ServerHttpSecurityCustomizer {

    private final @NonNull GatewayConfigProperties config;
    private final @NonNull GeorchestraUserMapper userMapper;

    @Override
    public void customize(ServerHttpSecurity http) {
        log.info("Configuring proxied applications access rules...");

        AuthorizeExchangeSpec authorizeExchange = http.authorizeExchange();

        // apply service-specific rules before global rules, order matters, and
        // otherwise global path matches would be applied before service ones.

        log.info("Applying ?login query param rule...");
        authorizeExchange.matchers(new ServerWebExchangeMatcher() {
            @Override
            public Mono<MatchResult> matches(ServerWebExchange exchange) {
                var hasParam = exchange.getRequest().getQueryParams().containsKey("login");
                return hasParam ? MatchResult.match() : MatchResult.notMatch();
            }
        }).authenticated();

        config.getServices().forEach((name, service) -> {
            log.info("Applying access rules for backend service '{}' at {}", name, service.getTarget());
            apply(name, authorizeExchange, service.getAccessRules());
        });

        log.info("Applying global access rules...");
        apply("global", authorizeExchange, config.getGlobalAccessRules());
    }

    private void apply(String serviceName, AuthorizeExchangeSpec authorizeExchange,
            List<RoleBasedAccessRule> accessRules) {
        if (accessRules == null || accessRules.isEmpty()) {
            log.debug("No {} access rules found.", serviceName);
            return;
        }
        for (RoleBasedAccessRule rule : accessRules) {
            apply(authorizeExchange, rule);
        }
    }

    @VisibleForTesting
    void apply(AuthorizeExchangeSpec authorizeExchange, RoleBasedAccessRule rule) {
        final List<String> antPatterns = resolveAntPatterns(rule);
        final boolean forbidden = rule.isForbidden();
        final boolean anonymous = rule.isAnonymous();
        final List<String> allowedRoles = rule.getAllowedRoles() == null ? List.of() : rule.getAllowedRoles();
        Access access = authorizeExchange(authorizeExchange, antPatterns);
        if (forbidden) {
            log.debug("Denying access to everyone for {}", antPatterns);
            denyAll(access);
        } else if (anonymous) {
            log.debug("Granting anonymous access for {}", antPatterns);
            permitAll(access);
        } else if (allowedRoles.isEmpty()) {
            log.debug("Granting access to any authenticated user for {}", antPatterns);
            requireAuthenticatedUser(access);
        } else {
            List<String> roles = resolveRoles(allowedRoles);
            log.debug("Granting access to roles {} for {}", roles, antPatterns);
            hasAnyRole(access, roles);
        }
    }

    private List<String> resolveAntPatterns(RoleBasedAccessRule rule) {
        List<String> antPatterns = rule.getInterceptUrl();
        Objects.requireNonNull(antPatterns, "intercept-urls is null");
        antPatterns.forEach(Objects::requireNonNull);
        if (antPatterns.isEmpty())
            throw new IllegalArgumentException("No ant-pattern(s) defined for rule " + rule);
        antPatterns.forEach(Objects::requireNonNull);
        return antPatterns;
    }

    @VisibleForTesting
    Access authorizeExchange(AuthorizeExchangeSpec authorizeExchange, List<String> antPatterns) {
        return authorizeExchange.pathMatchers(antPatterns.toArray(String[]::new));
    }

    private List<String> resolveRoles(List<String> allowedRoles) {
        return allowedRoles.stream().map(RolePrefixGeorchestraUserCustomizerExtension::toRole)
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    void requireAuthenticatedUser(Access access) {
        access.authenticated();
    }

    @VisibleForTesting
    void hasAnyRole(Access access, List<String> roles) {
        // Checks against the effective set of rules (in GeorchestraUser.getRoles(),
        // i.e. both provided by the Authorization service and derived from roles
        // mappings)
        access.access(GeorchestraUserRolesAuthorizationManager.hasAnyRole(userMapper, roles.toArray(String[]::new)));
    }

    @VisibleForTesting
    void permitAll(Access access) {
        access.permitAll();
    }

    @VisibleForTesting
    void denyAll(Access access) {
        access.denyAll();
    }
}
