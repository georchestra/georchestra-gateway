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

package org.georchestra.gateway.filter.headers.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.georchestra.commons.security.SecurityHeaders;
import org.georchestra.gateway.filter.headers.HeaderContributor;
import org.georchestra.gateway.model.GeorchestraOrganizations;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.model.HeaderMappings;
import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.security.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test suite for the {@link JsonPayloadHeadersContributorTest}
 * {@link HeaderContributor}
 *
 */
class JsonPayloadHeadersContributorTest {

    JsonPayloadHeadersContributor headerContributor;
    ServerWebExchange exchange;
    HeaderMappings matchedRouteHeadersConfig;

    @BeforeEach
    void init() {
        headerContributor = new JsonPayloadHeadersContributor();
        matchedRouteHeadersConfig = new HeaderMappings();
        GeorchestraTargetConfig matchedRouteConfig = new GeorchestraTargetConfig().headers(matchedRouteHeadersConfig);

        exchange = mock(ServerWebExchange.class);
        Map<String, Object> exchangeAttributes = new HashMap<>();
        when(exchange.getAttributes()).thenReturn(exchangeAttributes);

        GeorchestraTargetConfig.setTarget(exchange, matchedRouteConfig);

        matchedRouteHeadersConfig.disableAll();
        matchedRouteHeadersConfig.setJsonUser(Optional.of(true));
        matchedRouteHeadersConfig.setJsonOrganization(Optional.of(true));
    }

    @Test
    void testUser() throws Exception {
        GeorchestraUser user = new GeorchestraUser();
        user.setId("abc");
        user.setUsername("testuser");
        user.setOrganization("PSC");
        user.setEmail("testuser@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setTelephoneNumber("34144444");
        user.setTitle("Advisor");
        user.setPostalAddress("123 happy street");
        user.setNotes(":)");
        user.setRoles(List.of("ROLE_ADMIN", "ROLE_USER"));

        GeorchestraUsers.store(exchange, user);

        testContributesJsonHeader(user, "sec-user");
    }

    @Test
    void testOrganization() throws Exception {
        Organization org = new Organization();
        org.setId("abc");
        org.setName("PSC");
        org.setShortName("Project Steering Committee");
        org.setCategory("category");
        org.setDescription("desc");
        org.setLastUpdated("123");
        org.setLinkage("http://test.com");
        org.setMembers(List.of("homer", "marge", "lisa", "bart", "maggie"));
        org.setNotes("notes");
        org.setPostalAddress("123 springfield");

        GeorchestraOrganizations.store(exchange, org);

        testContributesJsonHeader(org, "sec-organization");
    }

    private void testContributesJsonHeader(Object object, String headerName)
            throws JsonProcessingException, JsonMappingException {
        Consumer<HttpHeaders> contributor = headerContributor.prepare(exchange);
        assertNotNull(contributor);

        HttpHeaders target = new HttpHeaders();
        contributor.accept(target);

        List<String> val = target.get(headerName);
        assertNotNull(val);
        String base64Ecnoded = val.getFirst();
        String json = SecurityHeaders.decode(base64Ecnoded);
        Object decoded = new ObjectMapper().readValue(json, object.getClass());
        assertEquals(object, decoded);
    }
}
