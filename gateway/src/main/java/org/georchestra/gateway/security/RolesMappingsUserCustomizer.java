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

package org.georchestra.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.security.core.Authentication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link GeorchestraUserCustomizerExtension} that expands the set of role
 * names assigned to a user by the authentication provider based on role mapping
 * rules.
 * <p>
 * This implementation allows assigning additional roles dynamically by defining
 * mapping patterns in the gateway configuration.
 * </p>
 * 
 * <p>
 * Role mappings are stored as a set of regular expression patterns. When a
 * user's authenticated role matches a pattern, the corresponding mapped roles
 * are added to the user's role set.
 * </p>
 * 
 * <p>
 * Example role mapping configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * rolesMappings:
 *   "ADMIN*": ["SUPERUSER"]
 *   "USER": ["READ_ONLY"]
 * </code>
 * </pre>
 * 
 * <p>
 * In the above example, any role starting with {@code ADMIN} will be assigned
 * the {@code SUPERUSER} role, and users with the {@code USER} role will
 * automatically receive the {@code READ_ONLY} role.
 * </p>
 * 
 * <p>
 * This component also employs caching for performance optimization, avoiding
 * redundant computations when resolving additional roles for a given
 * authentication.
 * </p>
 * 
 * @see GeorchestraUserMapper
 */
@Slf4j
public class RolesMappingsUserCustomizer implements GeorchestraUserCustomizerExtension {

    /**
     * Represents a role mapping rule, associating a regex-based pattern with a set
     * of additional roles.
     */
    @RequiredArgsConstructor
    private static class Matcher {
        private final @NonNull Pattern pattern;
        private final @NonNull @Getter List<String> extraRoles;

        /**
         * Checks if the given role matches the pattern.
         *
         * @param role the role name to check
         * @return {@code true} if the role matches the pattern, otherwise {@code false}
         */
        public boolean matches(String role) {
            return pattern.matcher(role).matches();
        }

        @Override
        public String toString() {
            return "%s -> %s".formatted(pattern.pattern(), extraRoles);
        }
    }

    @VisibleForTesting
    final List<Matcher> rolesMappings;

    private final Cache<String, List<String>> byRoleNameCache = CacheBuilder.newBuilder().maximumSize(1_000).build();

    /**
     * Constructs an instance of {@link RolesMappingsUserCustomizer} with the
     * provided role mappings.
     * 
     * @param rolesMappings a map where keys represent role name patterns and values
     *                      are lists of additional roles to be assigned when the
     *                      pattern matches
     */
    public RolesMappingsUserCustomizer(@NonNull Map<String, List<String>> rolesMappings) {
        this.rolesMappings = convertKeysToPatterns(rolesMappings);
    }

    /**
     * Converts role mapping keys into regex-based {@link Matcher} objects.
     *
     * @param mappings a map where each key is a role name pattern, and the
     *                 corresponding value is a list of additional roles
     * @return a list of compiled role matchers
     */
    private @NonNull List<Matcher> convertKeysToPatterns(Map<String, List<String>> mappings) {
        return mappings.entrySet().stream().map(entry -> new Matcher(compilePattern(entry.getKey()), entry.getValue()))
                .peek(matcher -> log.info("Loaded role mapping {}", matcher)).toList();
    }

    /**
     * Converts a role mapping key into a regex pattern.
     * <p>
     * Supports wildcard-based matching where:
     * </p>
     * <ul>
     * <li>{@code *} is converted to {@code .*} (match any characters).</li>
     * <li>{@code .} is escaped to match a literal period.</li>
     * </ul>
     *
     * @param role the role name pattern
     * @return the compiled {@link Pattern}
     */
    static Pattern compilePattern(String role) {
        String regex = role.replace(".", "\\.").replace("*", ".*");
        return Pattern.compile(regex);
    }

    /**
     * Applies additional role mappings to the authenticated user.
     * <p>
     * This method scans the user's current roles, determines any additional roles
     * based on predefined mappings, and updates the user object accordingly.
     * </p>
     * 
     * @param authToken  the original authentication token
     * @param mappedUser the user retrieved from authentication
     * @return the updated user with any additional roles assigned
     */
    @Override
    public GeorchestraUser apply(Authentication authToken, GeorchestraUser mappedUser) {
        Set<String> additionalRoles = computeAdditionalRoles(mappedUser.getRoles());

        if (!additionalRoles.isEmpty()) {
            additionalRoles.addAll(mappedUser.getRoles());
            mappedUser.setRoles(new ArrayList<>(additionalRoles)); // Ensure mutability
        }

        return mappedUser;
    }

    /**
     * Computes additional roles for the user based on their existing roles.
     * 
     * @param authenticatedRoles the roles assigned by the authentication provider
     * @return a set of additional roles derived from mapping rules
     */
    private Set<String> computeAdditionalRoles(List<String> authenticatedRoles) {
        final ConcurrentMap<String, List<String>> cache = byRoleNameCache.asMap();
        return authenticatedRoles.stream().map(role -> cache.computeIfAbsent(role, this::resolveAdditionalRoles))
                .flatMap(List::stream).collect(Collectors.toSet());
    }

    /**
     * Resolves additional roles for a given authenticated role by evaluating the
     * configured role mappings.
     *
     * @param authenticatedRole the role assigned by the authentication provider
     * @return a list of additional roles assigned based on mappings
     */
    private List<String> resolveAdditionalRoles(@NonNull String authenticatedRole) {
        List<String> roles = rolesMappings.stream().filter(matcher -> matcher.matches(authenticatedRole))
                .map(Matcher::getExtraRoles).flatMap(List::stream).toList();

        log.info("Computed additional roles for {}: {}", authenticatedRole, roles);
        return roles;
    }
}
