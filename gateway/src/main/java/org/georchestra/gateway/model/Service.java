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

import java.net.URI;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import lombok.Generated;

/**
 * Model object used to configure which authenticated user's roles can reach a
 * given backend service URIs, and which HTTP request headers to append to the
 * proxied requests.
 *
 */
@Data
@Generated
public class Service {
    /**
     * Back end service URL the Gateway will use to proxy incoming requests to,
     * based on the {@link #getAccessRules() access rules}
     * {@link RoleBasedAccessRule#getInterceptUrl() intercept-URLs}
     */
    private URI target;

    /**
     * Service-specific security headers configuration
     */
    private HeaderMappings headers;

    /**
     * List of Ant-pattern based access rules for the given back-end service
     */
    private List<RoleBasedAccessRule> accessRules = List.of();

    public Optional<HeaderMappings> headers() {
        return Optional.ofNullable(headers);
    }
}
