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
package org.georchestra.gateway.model;

import java.util.Map;
import java.util.Optional;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.web.server.ServerWebExchange;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GeorchestraUsers {

    static final String GEORCHESTRA_USER_KEY = GeorchestraUsers.class.getCanonicalName();

    public static Optional<GeorchestraUser> resolve(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttributes().get(GEORCHESTRA_USER_KEY)).map(GeorchestraUser.class::cast);
    }

    public static ServerWebExchange store(@NonNull ServerWebExchange exchange, GeorchestraUser user) {
        Map<String, Object> attributes = exchange.getAttributes();
        if (user == null) {
            attributes.remove(GEORCHESTRA_USER_KEY);
        } else {
            attributes.put(GEORCHESTRA_USER_KEY, user);
        }
        return exchange;
    }
}
