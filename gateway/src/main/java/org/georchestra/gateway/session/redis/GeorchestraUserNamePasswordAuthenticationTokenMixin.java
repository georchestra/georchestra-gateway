/*
 * Copyright (C) 2026 by the geOrchestra PSC
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
package org.georchestra.gateway.session.redis;

import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin for
 * {@link org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken}
 * to enable JSON serialization and deserialization.
 * <p>
 * This mixin provides the necessary Jackson annotations to properly serialize
 * and deserialize the custom authentication token when storing sessions in
 * Redis.
 * </p>
 *
 * @see org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class GeorchestraUserNamePasswordAuthenticationTokenMixin {

    /**
     * Constructor for Jackson deserialization.
     *
     * @param configName the LDAP configuration name
     * @param orig       the original authentication object
     */
    @JsonCreator
    GeorchestraUserNamePasswordAuthenticationTokenMixin(
            @JsonProperty(value = "configName", required = true) String configName,
            @JsonProperty(value = "orig", required = true) Authentication orig) {
    }
}
