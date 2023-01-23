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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.Generated;

/**
 * Model object representing the externalized configuration properties used to
 * set up URI based access rules and HTTP request headers appended to proxied
 * requests to back-end services.
 *
 */
@Data
@Generated
@ConfigurationProperties("georchestra.gateway")
public class GatewayConfigProperties {

    private Map<String, List<String>> rolesMappings = Map.of();

    /**
     * Configures the global security headers to append to all proxied http requests
     */
    private HeaderMappings defaultHeaders;

    /**
     * Incoming request URI pattern matching for requests that don't match any of
     * the service-specific rules under
     * {@literal georchestra.gateway.services.[service].access-rules}
     */
    private List<RoleBasedAccessRule> globalAccessRules;

    /**
     * Maps a logical service name to its back-end service URL and security settings
     */
    private Map<String, Service> services = Collections.emptyMap();

}
