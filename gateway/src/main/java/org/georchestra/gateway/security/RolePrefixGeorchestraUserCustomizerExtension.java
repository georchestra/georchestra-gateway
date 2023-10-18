/*
 * Copyright (C) 2023 by the geOrchestra PSC
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

import java.util.List;
import java.util.stream.Collectors;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;

import lombok.NonNull;

/**
 * Extension to add the {@literal ROLE_} prefix to resolved user roles.
 * <p>
 * Lowest precedence {@link GeorchestraUserCustomizerExtension} all
 * {@link GeorchestraUser#getRoles() roles} in the final user's effective roles
 * (both coming from the {@link Authentication} and resolved from configuration)
 * have the {@literal ROLE_} prefix.
 */
public class RolePrefixGeorchestraUserCustomizerExtension implements GeorchestraUserCustomizerExtension {

    public @Override int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public GeorchestraUser apply(Authentication t, GeorchestraUser u) {
        List<String> prefixed = u.getRoles().stream().map(RolePrefixGeorchestraUserCustomizerExtension::toRole)
                .collect(Collectors.toList());
        u.setRoles(prefixed);
        return u;
    }

    public static String toRole(@NonNull String authority) {
        return authority.startsWith("ROLE_") ? authority : "ROLE_" + authority;
    }

}
