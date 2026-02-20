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

package org.georchestra.gateway.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Optional;

import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.security.ldap.extended.ExtendedGeorchestraUser;
import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Test suite for {@link ResolveGeorchestraUserGlobalFilter}
 *
 */
class ResolveGeorchestraUserGlobalFilterTest {

    private ResolveGeorchestraUserGlobalFilter filter;
    private GeorchestraUserMapper mockMapper;
    private ServerAuthenticationFailureHandler mockFailureHandler;

    private GatewayFilterChain mockChain;
    private MockServerHttpRequest request;
    private MockServerWebExchange exchange;

    /**
     * @throws java.lang.Exception
     */
    @BeforeEach
    void setUp() throws Exception {
        mockMapper = mock(GeorchestraUserMapper.class);
        mockFailureHandler = mock(ServerAuthenticationFailureHandler.class);
        filter = new ResolveGeorchestraUserGlobalFilter(mockMapper, mockFailureHandler);
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());
        request = MockServerHttpRequest.get("/test").build();
        exchange = MockServerWebExchange.from(request);

    }

    @Test
    void testFilter_NoAuthenticatedUser() {
        Mono<Void> ret = filter.filter(exchange, mockChain);
        assertNotNull(ret);
        ret.block();
        verify(mockChain, times(1)).filter(same(exchange));
        verify(mockMapper, never()).resolve(any());
    }

    @Test
    void testFilter_PrincipalIsNotAnAuthentication() {
        Mono<Principal> principal = Mono.just(mock(Principal.class));
        ServerWebExchange exchange = this.exchange.mutate().principal(principal).build();

        filter.filter(exchange, mockChain).block();

        verify(mockChain, times(1)).filter(same(exchange));
        verify(mockMapper, never()).resolve(any());
    }

    @Test
    void testFilter_NoUseResolved() {
        Mono<Principal> principal = Mono.just(mock(Authentication.class));
        ServerWebExchange exchange = this.exchange.mutate().principal(principal).build();

        filter.filter(exchange, mockChain).block();

        verify(mockChain, times(1)).filter(same(exchange));
        verify(mockMapper, times(1)).resolve(any());

        assertTrue(GeorchestraUsers.resolve(exchange).isEmpty());
    }

    @Test
    void testFilter_UseResolved() {
        Authentication auth1 = mock(Authentication.class);
        GeorchestraUser user1 = mock(GeorchestraUser.class);
        when(mockMapper.resolve(same(auth1))).thenReturn(Optional.of(user1));

        ServerWebExchange exchange = this.exchange.mutate().principal(Mono.just(auth1)).build();

        filter.filter(exchange, mockChain).block();

        verify(mockChain, times(1)).filter(same(exchange));
        verify(mockMapper, times(1)).resolve(any());

        Optional<GeorchestraUser> resolved = GeorchestraUsers.resolve(exchange);
        assertSame(user1, resolved.orElseThrow());
    }

    @Test
    void testFilter_UseResolvedWithoutOrganization() {
        Authentication auth1 = mock(Authentication.class);
        ExtendedGeorchestraUser user1 = mock(ExtendedGeorchestraUser.class);
        when(mockMapper.resolve(same(auth1))).thenReturn(Optional.of(user1));
        when(user1.getOrg()).thenReturn(null);

        ServerWebExchange exchange = this.exchange.mutate().principal(Mono.just(auth1)).build();

        filter.filter(exchange, mockChain).block();

        verify(mockChain, times(1)).filter(same(exchange));
        verify(mockMapper, times(1)).resolve(any());

        Optional<GeorchestraUser> resolved = GeorchestraUsers.resolve(exchange);
        assertSame(user1, resolved.orElseThrow());
    }
}
