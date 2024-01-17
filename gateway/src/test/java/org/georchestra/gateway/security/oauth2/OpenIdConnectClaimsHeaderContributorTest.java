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
package org.georchestra.gateway.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.georchestra.gateway.model.GeorchestraUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ServerWebExchange;

class OpenIdConnectClaimsHeaderContributorTest {

    private OpenIdConnectClaimsHeaderContributor headerContrib;
    private ServerWebExchange exchange;
    private HttpHeaders headers;

    @BeforeEach
    void setup() {
        headers = new HttpHeaders();
        headerContrib = new OpenIdConnectClaimsHeaderContributor();
        exchange = mock(ServerWebExchange.class);
        Map<String, Object> exchangeAttributes = new HashMap<>();
        when(exchange.getAttributes()).thenReturn(exchangeAttributes);
    }

    @Test
    void testNoAuthentication() {
        GeorchestraUsers.store(exchange, (Authentication) null);
        headerContrib.prepare(exchange).accept(headers);
        assertThat(headers).doesNotContainKey("authorizedParty");
    }

    @Test
    void testNotAnOidcAuthentication() {
        Authentication auth = new UsernamePasswordAuthenticationToken("test", "test");
        GeorchestraUsers.store(exchange, auth);
        headerContrib.prepare(exchange).accept(headers);
        assertThat(headers).doesNotContainKey("authorizedParty");
    }

    @Test
    void testOauth2AuthenticationNotOidc() {
        Authentication auth = mock(OAuth2AuthenticationToken.class);
        OAuth2User oauth2User = mock(OAuth2User.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);
        GeorchestraUsers.store(exchange, auth);

        headerContrib.prepare(exchange).accept(headers);
        assertThat(headers).doesNotContainKey("authorizedParty");
    }

    @Test
    void testOidcAuthenticated() {
        Authentication auth = mock(OAuth2AuthenticationToken.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getAccessTokenHash()).thenReturn("test-token-hash");
        when(oidcUser.getAuthorizedParty()).thenReturn("test-azp");
        when(auth.getPrincipal()).thenReturn(oidcUser);

        GeorchestraUsers.store(exchange, auth);
        headerContrib.prepare(exchange).accept(headers);
        assertThat(headers).containsEntry("accessTokenHash", List.of("test-token-hash"));
        assertThat(headers).containsEntry("authorizedParty", List.of("test-azp"));
    }
}
