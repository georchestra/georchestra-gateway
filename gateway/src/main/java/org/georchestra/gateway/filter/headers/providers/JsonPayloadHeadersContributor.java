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
package org.georchestra.gateway.filter.headers.providers;

import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Consumer;

import org.georchestra.commons.security.SecurityHeaders;
import org.georchestra.ds.security.OrganizationsApiImpl;
import org.georchestra.gateway.filter.headers.HeaderContributor;
import org.georchestra.gateway.model.GeorchestraOrganizations;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.model.HeaderMappings;
import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.security.model.Organization;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Contributes {@literal sec-user} and {@literal sec-organization}
 * Base64-encoded JSON payloads, based on {@link HeaderMappings#getJsonUser()}
 * and {@link HeaderMappings#getJsonOrganization()} matched-route headers
 * configuration.
 * 
 * @see GeorchestraUsers#resolve
 * @see GeorchestraOrganizations#resolve
 * @see GeorchestraTargetConfig
 */
public class JsonPayloadHeadersContributor extends HeaderContributor {

    /**
     * Encoder to create the JSON String value for a {@link GeorchestraUser}
     * obtained from {@link OrganizationsApiImpl}
     */
    private ObjectMapper encoder;

    public JsonPayloadHeadersContributor() {
        this.encoder = new ObjectMapper();
        this.encoder.configure(SerializationFeature.INDENT_OUTPUT, Boolean.FALSE);
        this.encoder.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, Boolean.FALSE);
        this.encoder.setSerializationInclusion(Include.NON_NULL);
    }

    public @Override Consumer<HttpHeaders> prepare(ServerWebExchange exchange) {
        return headers -> GeorchestraTargetConfig.getTarget(exchange)//
                .map(GeorchestraTargetConfig::headers)//
                .ifPresent(mappings -> addJsonPayloads(exchange, mappings, headers));
    }

    private void addJsonPayloads(final ServerWebExchange exchange, final HeaderMappings mappings, HttpHeaders headers) {
        Optional<GeorchestraUser> user = GeorchestraUsers.resolve(exchange);
        Optional<Organization> org = GeorchestraOrganizations.resolve(exchange);
        addJson(headers, "sec-user", mappings.getJsonUser().orElse(false), user);
        addJson(headers, "sec-organization", mappings.getJsonOrganization().orElse(false), org);
    }

    private void addJson(HttpHeaders target, String headerName, boolean enabled, Optional<?> toEncode) {
        if (enabled) {
            toEncode.map(this::encodeJson)//
                    .map(this::encodeBase64)//
                    .ifPresent(encoded -> target.add(headerName, encoded));
        }
    }

    private String encodeJson(Object payloadObject) {
        try {
            return this.encoder.writer().writeValueAsString(payloadObject);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String encodeBase64(String json) {
        return SecurityHeaders.encodeBase64(json);
    }
}
