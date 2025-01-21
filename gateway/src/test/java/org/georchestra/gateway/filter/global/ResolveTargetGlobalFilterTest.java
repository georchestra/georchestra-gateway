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

package org.georchestra.gateway.filter.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.HeaderMappings;
import org.georchestra.gateway.model.RoleBasedAccessRule;
import org.georchestra.gateway.model.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Test suite for {@link ResolveTargetGlobalFilter}
 *
 */
class ResolveTargetGlobalFilterTest {

    private GatewayConfigProperties config;
    private ResolveTargetGlobalFilter filter;

    private GatewayFilterChain mockChain;
    private MockServerHttpRequest request;
    private MockServerWebExchange exchange;

    final URI matchingURI = URI.create("http://fake.backend.com:8080");
    private Route matchingRoute;

    HeaderMappings defaultHeaders;
    List<RoleBasedAccessRule> defaultRules;

    @BeforeEach
    void setUp() throws Exception {
        config = new GatewayConfigProperties();
        defaultHeaders = new HeaderMappings().enableAll();
        defaultRules = List.of(rule("/global/1"));
        config.setDefaultHeaders(defaultHeaders);
        config.setGlobalAccessRules(defaultRules);

        filter = new ResolveTargetGlobalFilter(config);

        matchingRoute = mock(Route.class);
        when(matchingRoute.getUri()).thenReturn(matchingURI);

        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());
        request = MockServerHttpRequest.get("/test").build();
        exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, matchingRoute);

    }

    @Test
    void filter_SavesResolvedTargetConfig() {
        assertTrue(GeorchestraTargetConfig.getTarget(exchange).isEmpty());
        filter.filter(exchange, mockChain);
        assertTrue(GeorchestraTargetConfig.getTarget(exchange).isPresent());
        verify(mockChain, times(1)).filter(same(exchange));
    }

    @Test
    void resolveTarget_defaultsToGlobal() {
        GeorchestraTargetConfig target = filter.resolveTarget(matchingRoute);
        assertNotNull(target);
        assertSame(defaultHeaders, target.headers());
        assertSame(defaultRules, target.accessRules());
    }

    @Test
    void resolveTarget_applies_global_headers_if_service_doesnt_define_them() {
        Service serviceWithNoHeaderMappings = service(matchingURI, (HeaderMappings) null);
        RoleBasedAccessRule serviceSpecificRule = rule("/rule/path");
        serviceWithNoHeaderMappings.setAccessRules(List.of(serviceSpecificRule));

        Service service2 = service(URI.create("https://backend.service.2"), new HeaderMappings());
        config.setServices(Map.of("service1", serviceWithNoHeaderMappings, "service2", service2));

        GeorchestraTargetConfig target = filter.resolveTarget(matchingRoute);
        assertSame(defaultHeaders, target.headers());
        assertEquals(List.of(serviceSpecificRule), target.accessRules());
    }

    @Test
    void resolveTarget_applies_global_access_rules_if_service_doesnt_define_them() {
        Service serviceWithNoAccessRules = service(matchingURI);

        Service service2 = service(URI.create("https://backend.service.2"), new HeaderMappings());
        config.setServices(Map.of("service1", serviceWithNoAccessRules, "service2", service2));

        GeorchestraTargetConfig target = filter.resolveTarget(matchingRoute);
        assertEquals(defaultRules, target.accessRules());
    }

    @Test
    void resolveTarget_applies_default_headers() {
        Service serviceWithEmptyHeaders = service(matchingURI);
        serviceWithEmptyHeaders.setHeaders(new HeaderMappings());

        GeorchestraTargetConfig target = filter.resolveTarget(matchingRoute);
        assertEquals(defaultHeaders, target.headers());
    }

    @Test
    void resolveTarget_service_headers_merged_with_default_headers() {

        this.defaultHeaders = new HeaderMappings().disableAll().userid(true);
        config.setDefaultHeaders(defaultHeaders);

        HeaderMappings headerMappings = new HeaderMappings().jsonUser(true).jsonOrganization(true);
        Service serviceWithCustomHeaders = service(matchingURI, headerMappings);

        config.setServices(Map.of("service", serviceWithCustomHeaders));

        GeorchestraTargetConfig target = filter.resolveTarget(matchingRoute);

        var expected = new HeaderMappings().disableAll().userid(true).jsonUser(true).jsonOrganization(true);

        assertThat(target.headers().getJsonUser()).as("expected from service").isEqualTo(expected.getJsonUser());
        assertThat(target.headers().getJsonOrganization()).as("expected from service")
                .isEqualTo(expected.getJsonOrganization());

        assertThat(target.headers().getUserid()).as("expected from defaults").isEqualTo(expected.getUserid());

        assertThat(target.headers()).isEqualTo(expected);
    }

    private Service service(URI targetURI) {
        return service(targetURI, null);
    }

    private Service service(URI targetURI, HeaderMappings headers) {
        Service service = new Service();
        service.setTarget(targetURI);
        service.setHeaders(headers);
        return service;
    }

    private RoleBasedAccessRule rule(String... uris) {
        return new RoleBasedAccessRule().setInterceptUrl(Arrays.asList(uris));
    }
}
