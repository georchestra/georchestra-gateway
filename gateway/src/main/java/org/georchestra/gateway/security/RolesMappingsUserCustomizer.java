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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.georchestra.security.model.GeorchestraUser;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Authenticated user customizer extension to expand the set of role names
 * assigned to a user by the actual authentication provider
 */
public class RolesMappingsUserCustomizer implements GeorchestraUserCustomizerExtension {

    @RequiredArgsConstructor
    private static class Matcher {
        private final @NonNull Pattern pattern;
        private final @NonNull @Getter List<String> extraRoles;

        public boolean matches(String input) {
            return pattern.matcher(input).matches();
        }

        public @Override String toString() {
            return String.format("%s -> %s", pattern.pattern(), extraRoles);
        }
    }

    private final List<Matcher> usernameMappings;
    private final List<Matcher> roleMappings;

    public RolesMappingsUserCustomizer(@NonNull Map<String, List<String>> usernameMappings,
            @NonNull Map<String, List<String>> rolesMappings) {
        this.usernameMappings = keysToRegularExpressions(usernameMappings);
        this.roleMappings = keysToRegularExpressions(rolesMappings);
    }

    @VisibleForTesting
    RolesMappingsUserCustomizer addRoleMappings(String roleGlob, String... additionalRoles) {
        roleMappings.add(matcher(roleGlob, Arrays.asList(additionalRoles)));
        return this;
    }

    @VisibleForTesting
    RolesMappingsUserCustomizer addUsernameMappings(String usernameGlob, String... additionalRoles) {
        usernameMappings.add(matcher(usernameGlob, Arrays.asList(additionalRoles)));
        return this;
    }

    private @NonNull List<Matcher> keysToRegularExpressions(Map<String, List<String>> mappings) {
        return mappings.entrySet()//
                .stream()//
                .map(e -> matcher(e.getKey(), e.getValue()))//
                .collect(Collectors.toList());
    }

    private Matcher matcher(String inputGlob, List<String> additionalRoles) {
        return new Matcher(toPattern(inputGlob), additionalRoles);
    }

    static Pattern toPattern(String glob) {
        String regex = glob.replace(".", "(\\.)").replace("*", "(.*)");
        return Pattern.compile(regex);
    }

    @Override
    public GeorchestraUser apply(GeorchestraUser user) {

        Set<String> additionalRoles = computeAdditionalRoles(user);
        if (!additionalRoles.isEmpty()) {
            List<String> roles = Stream.concat(user.getRoles().stream(), additionalRoles.stream()).distinct()
                    .collect(Collectors.toList());
            user.setRoles(roles);
        }
        return user;
    }

    private Set<String> computeAdditionalRoles(GeorchestraUser user) {
        String username = user.getUsername();
        List<String> roles = user.getRoles();
        Stream<String> usernameMatches = null == username ? Stream.empty()
                : computeAdditionalRoles(username, usernameMappings);
        Stream<String> roleMatches = roles.stream().map(role -> computeAdditionalRoles(role, roleMappings))
                .flatMap(Function.identity());
        return Stream.concat(usernameMatches, roleMatches).collect(Collectors.toSet());
    }

    private Stream<String> computeAdditionalRoles(@NonNull String input, @NonNull List<Matcher> mappings) {

        return mappings.stream().filter(m -> m.matches(input)).map(Matcher::getExtraRoles).flatMap(List::stream);
    }
}
