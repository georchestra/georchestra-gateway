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

import static org.georchestra.commons.security.SecurityHeaders.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.georchestra.gateway.filter.headers.HeaderContributor;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.model.HeaderMappings;
import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Test suite for the {@link GeorchestraUserHeadersContributor}
 * {@link HeaderContributor}
 *
 */
class GeorchestraUserHeadersContributorTest {

    GeorchestraUserHeadersContributor headerContributor;
    ServerWebExchange exchange;
    HeaderMappings matchedRouteHeadersConfig;

    @BeforeEach
    void init() {
        headerContributor = new GeorchestraUserHeadersContributor();
        matchedRouteHeadersConfig = new HeaderMappings();
        GeorchestraTargetConfig matchedRouteConfig = new GeorchestraTargetConfig().headers(matchedRouteHeadersConfig);

        exchange = mock(ServerWebExchange.class);
        Map<String, Object> exchangeAttributes = new HashMap<>();
        when(exchange.getAttributes()).thenReturn(exchangeAttributes);

        GeorchestraTargetConfig.setTarget(exchange, matchedRouteConfig);
    }

    @Test
    void testNoMatchedRouteConfig() {
        GeorchestraTargetConfig.setTarget(exchange, null);
        assertTrue(GeorchestraTargetConfig.getTarget(exchange).isEmpty());

        Consumer<HttpHeaders> contributor = headerContributor.prepare(exchange);
        assertNotNull(contributor);

        HttpHeaders target = new HttpHeaders();
        contributor.accept(target);
        assertTrue(target.isEmpty());
    }

    @Test
    void testNoUser() {
        Consumer<HttpHeaders> contributor = headerContributor.prepare(exchange);
        assertNotNull(contributor);

        HttpHeaders target = new HttpHeaders();
        contributor.accept(target);
        assertTrue(target.isEmpty());
    }

    @Test
    void testContributesHeadersFromUser() {
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
        user.setLdapWarn(false);
        user.setLdapRemainingDays("");

        GeorchestraUsers.store(exchange, user);

        matchedRouteHeadersConfig.enableAll();

        Consumer<HttpHeaders> contributor = headerContributor.prepare(exchange);
        assertNotNull(contributor);

        HttpHeaders target = new HttpHeaders();
        contributor.accept(target);

        assertEquals(List.of(user.getId()), target.get(SEC_USERID));
        assertEquals(List.of(user.getUsername()), target.get(SEC_USERNAME));
        assertEquals(List.of(user.getFirstName()), target.get(SEC_FIRSTNAME));
        assertEquals(List.of(user.getLastName()), target.get(SEC_LASTNAME));
        assertEquals(List.of(user.getOrganization()), target.get(SEC_ORG));
        assertEquals(List.of(user.getEmail()), target.get(SEC_EMAIL));
        assertEquals(List.of(user.getTelephoneNumber()), target.get(SEC_TEL));
        assertEquals(List.of(user.getPostalAddress()), target.get(SEC_ADDRESS));
        assertEquals(List.of(user.getTitle()), target.get(SEC_TITLE));
        assertEquals(List.of(user.getNotes()), target.get(SEC_NOTES));
        assertEquals(List.of(String.valueOf(user.getIsExternalAuth())), target.get(SEC_EXTERNAL_AUTHENTICATION));

        String roles = user.getRoles().stream().collect(Collectors.joining(";"));
        assertEquals(List.of(roles), target.get(SEC_ROLES));
    }
}
