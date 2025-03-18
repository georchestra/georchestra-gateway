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
package org.georchestra.gateway.security.accessrules;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import java.util.Objects;

import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.RoleBasedAccessRule;
import org.georchestra.gateway.model.Service;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec.Access;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ServerHttpSecurityCustomizer} responsible for applying
 * {@link RoleBasedAccessRule role-based access rules} at application startup.
 * <p>
 * The access rules can be configured as:
 * <ul>
 * <li>{@link GatewayConfigProperties#getGlobalAccessRules() Global rules},
 * which apply to all services.</li>
 * <li>Service-specific rules defined in
 * {@link GatewayConfigProperties#getServices()}, which override the global
 * rules for particular services.</li>
 * </ul>
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

        AuthorizeExchangeSpec authorizeExchange = http.authorizeExchange(withDefaults()).authorizeExchange();

        // Apply service-specific rules before global rules.
        // This ensures that service-specific paths take precedence over general rules.
        config.getServices().forEach((name, service) -> {
            log.info("Applying access rules for backend service '{}' at {}", name, service.getTarget());
            apply(name, authorizeExchange, service.getAccessRules());
        });

        log.info("Applying global access rules...");
        apply("global", authorizeExchange, config.getGlobalAccessRules());
    }

    /**
     * Applies a set of access rules to the provided {@link AuthorizeExchangeSpec}.
     *
     * @param serviceName       the name of the service being configured
     * @param authorizeExchange the authorization configuration object
     * @param accessRules       the access rules to apply
     */
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

    /**
     * Applies a {@link RoleBasedAccessRule} to the provided
     * {@link AuthorizeExchangeSpec}, determining how access should be granted or
     * restricted for specific URL patterns.
     * <p>
     * This method evaluates the given access rule and configures the security
     * filter chain accordingly by applying one of the following strategies:
     * </p>
     * <ul>
     * <li>If {@link RoleBasedAccessRule#isForbidden()} is {@code true}, access is
     * completely denied.</li>
     * <li>If {@link RoleBasedAccessRule#isAnonymous()} is {@code true}, the URLs
     * are publicly accessible.</li>
     * <li>If {@link RoleBasedAccessRule#getAllowedRoles()} is empty, access is
     * granted to any authenticated user.</li>
     * <li>Otherwise, access is restricted to users with at least one of the
     * specified roles.</li>
     * </ul>
     * <p>
     * The URL patterns to which the rule applies are derived from
     * {@link RoleBasedAccessRule#getInterceptUrl()}.
     * </p>
     *
     * @param authorizeExchange the authorization configuration object where rules
     *                          are applied
     * @param rule              the access rule defining the URL patterns and access
     *                          conditions
     * @throws NullPointerException     if the rule or its intercept URLs are null
     * @throws IllegalArgumentException if the rule does not define any URL patterns
     */
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
            List<String> roles = resolveRoles(antPatterns, allowedRoles);
            log.debug("Granting access to roles {} for {}", roles, antPatterns);
            hasAnyAuthority(access, roles);
        }
    }

    /**
     * Resolves the Ant-style URL patterns for a given access rule.
     *
     * @param rule the access rule containing the URL patterns
     * @return the list of resolved URL patterns
     */
    private List<String> resolveAntPatterns(RoleBasedAccessRule rule) {
        List<String> antPatterns = rule.getInterceptUrl();
        Objects.requireNonNull(antPatterns, "intercept-urls is null");
        antPatterns.forEach(Objects::requireNonNull);
        if (antPatterns.isEmpty()) {
            throw new IllegalArgumentException("No ant-pattern(s) defined for rule " + rule);
        }
        return antPatterns;
    }

    /**
     * Configures URL-based authorization for a given set of patterns.
     *
     * @param authorizeExchange the security configuration object
     * @param antPatterns       the URL patterns to authorize
     * @return the access configuration for the specified patterns
     */
    @VisibleForTesting
    Access authorizeExchange(AuthorizeExchangeSpec authorizeExchange, List<String> antPatterns) {
        return authorizeExchange.pathMatchers(antPatterns.toArray(String[]::new));
    }

    /**
     * Resolves the role names, ensuring they have the required prefix.
     *
     * @param antPatterns  the URL patterns being configured
     * @param allowedRoles the roles that should be granted access
     * @return the list of role names with appropriate prefixes
     */
    private List<String> resolveRoles(List<String> antPatterns, List<String> allowedRoles) {
        return allowedRoles.stream().map(this::ensureRolePrefix).toList();
    }

    /**
     * Requires that the user be authenticated to access the configured path.
     *
     * @param access the access configuration object
     */
    @VisibleForTesting
    void requireAuthenticatedUser(Access access) {
        access.authenticated();
    }

    /**
     * Grants access only if the user has at least one of the specified roles.
     *
     * @param access the access configuration object
     * @param roles  the list of roles required for access
     */
    @VisibleForTesting
    void hasAnyAuthority(Access access, List<String> roles) {
        access.access(
                GeorchestraUserRolesAuthorizationManager.hasAnyAuthority(userMapper, roles.toArray(String[]::new)));
    }

    /**
     * Grants unrestricted access to the configured path.
     *
     * @param access the access configuration object
     */
    @VisibleForTesting
    void permitAll(Access access) {
        access.permitAll();
    }

    /**
     * Denies access to all users for the configured path.
     *
     * @param access the access configuration object
     */
    @VisibleForTesting
    void denyAll(Access access) {
        access.denyAll();
    }

    /**
     * Ensures that the given role name has the required {@code ROLE_} prefix.
     *
     * @param roleName the role name to check
     * @return the role name with the {@code ROLE_} prefix if it was missing
     */
    private String ensureRolePrefix(@NonNull String roleName) {
        return roleName.startsWith("ROLE_") ? roleName : ("ROLE_" + roleName);
    }
}
