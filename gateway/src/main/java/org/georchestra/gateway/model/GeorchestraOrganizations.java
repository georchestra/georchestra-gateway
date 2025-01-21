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

import java.util.Optional;

import org.georchestra.security.model.Organization;
import org.springframework.web.server.ServerWebExchange;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GeorchestraOrganizations {

    static final String GEORCHESTRA_ORGANIZATION_KEY = GeorchestraOrganizations.class.getCanonicalName();

    public static Optional<Organization> resolve(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttributes().get(GEORCHESTRA_ORGANIZATION_KEY))
                .map(Organization.class::cast);
    }

    public static void store(ServerWebExchange exchange, Organization org) {
        exchange.getAttributes().put(GEORCHESTRA_ORGANIZATION_KEY, org);
    }
}