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

package org.georchestra.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.georchestra.security.model.GeorchestraUser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticated user customizer extension to expand the set of role names
 * assigned to a user by the actual authentication provider
 */
@Slf4j
public class RolesMappingsUserCustomizer implements GeorchestraUserCustomizerExtension {

    @RequiredArgsConstructor
    private static class Matcher {
        private final @NonNull Pattern pattern;
        private final @NonNull @Getter List<String> extraRoles;

        public boolean matches(String role) {
            return pattern.matcher(role).matches();
        }

        public @Override String toString() {
            return String.format("%s -> %s", pattern.pattern(), extraRoles);
        }
    }

    @VisibleForTesting
    final List<Matcher> rolesMappings;

    private final Cache<String, List<String>> byRoleNameCache = CacheBuilder.newBuilder().maximumSize(1_000).build();

    public RolesMappingsUserCustomizer(@NonNull Map<String, List<String>> rolesMappings) {
        this.rolesMappings = keysToRegularExpressions(rolesMappings);
    }

    private @NonNull List<Matcher> keysToRegularExpressions(Map<String, List<String>> mappings) {
        return mappings.entrySet()//
                .stream()//
                .map(e -> new Matcher(toPattern(e.getKey()), e.getValue()))//
                .peek(m -> log.info("Loaded role mapping {}", m))//
                .collect(Collectors.toList());
    }

    static Pattern toPattern(String role) {
        String regex = role.replace(".", "(\\.)").replace("*", "(.*)");
        return Pattern.compile(regex);
    }

    @Override
    public GeorchestraUser apply(GeorchestraUser user) {

        Set<String> additionalRoles = computeAdditionalRoles(user.getRoles());
        if (!additionalRoles.isEmpty()) {
            additionalRoles.addAll(user.getRoles());
            user.setRoles(new ArrayList<>(additionalRoles));
        }
        return user;
    }

    /**
     * @param authenticatedRoles the role names extracted from the authentication
     *                           provider
     * @return the additional role names for the user
     */
    private Set<String> computeAdditionalRoles(List<String> authenticatedRoles) {
        final ConcurrentMap<String, List<String>> cache = byRoleNameCache.asMap();
        return authenticatedRoles.stream().map(role -> cache.computeIfAbsent(role, this::computeAdditionalRoles))
                .flatMap(List::stream).collect(Collectors.toSet());
    }

    private List<String> computeAdditionalRoles(@NonNull String authenticatedRole) {

        List<String> roles = rolesMappings.stream().filter(m -> m.matches(authenticatedRole))
                .map(Matcher::getExtraRoles).flatMap(List::stream).collect(Collectors.toList());

        log.info("Computed additional roles for {}: {}", authenticatedRole, roles);
        return roles;
    }
}
