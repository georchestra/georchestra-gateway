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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.georchestra.gateway.autoconfigure.app.CustomErrorAttributes;
import org.georchestra.gateway.autoconfigure.app.ErrorCustomizerAutoConfiguration;
import org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@SpringBootTest(properties = "georchestra.datadir=src/test/resources/test-datadir", webEnvironment = WebEnvironment.MOCK)
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles({ "test" })
class GeorchestraGatewayApplicationTests {

    private @Autowired Environment env;
    private @Autowired RouteLocator routeLocator;

    private @Autowired WebTestClient testClient;

    private @Autowired WhoamiController whoamiController;
    private @Autowired ApplicationContext context;

    @Test
    void contextLoadsFromDatadir() {
        assertEquals("src/test/resources/test-datadir", env.getProperty("georchestra.datadir"));

        assertEquals(
                "optional:file:src/test/resources/test-datadir/default.properties,optional:file:src/test/resources/test-datadir/gateway/gateway.yaml",
                env.getProperty("spring.config.import"));

        Boolean propertyFromTestDatadir = env.getProperty("georchestra.test-datadir", Boolean.class);
        assertNotNull(propertyFromTestDatadir);
        assertTrue(propertyFromTestDatadir,
                "Configuration property expected to load from classpath:/test-datadir/gateway/gateway.yaml not found");
    }

    @Test
    void verifyRoutesLoadedFromDatadir() {
        Map<String, Route> routesById = routeLocator.getRoutes()
                .collect(Collectors.toMap(Route::getId, Function.identity())).block();

        Route testRoute = routesById.get("testRoute");
        assertNotNull(testRoute);
        assertEquals(URI.create("http://test.com:80"), testRoute.getUri());
    }

    @Test
    void makeSureWhoamiDoesNotProvideAnySensitiveInfo() {
        Authentication orig = Mockito.mock(Authentication.class);
        Mockito.when(orig.getCredentials()).thenReturn("123456");
        Authentication auth = new GeorchestraUserNamePasswordAuthenticationToken("test", orig);
        ServerWebExchange exch = Mockito.mock(ServerWebExchange.class);

        Map<String, Object> ret = whoamiController.whoami(auth, exch).block();

        GeorchestraUserNamePasswordAuthenticationToken toTest = (GeorchestraUserNamePasswordAuthenticationToken) ret
                .get("org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken");
        assertNull(toTest.getCredentials());
    }

    /**
     * Make sure a request to an unavailable service for which there's a route
     * definition but results in a DNS lookup error produces an HTTP 503 (Service
     * Unavailable) status code and not a 500 (Internal Server Error) one
     * 
     * @see ErrorCustomizerAutoConfiguration
     */
    @WithMockUser(authorities = "USER")
    @Test
    void errorCustomizerReturnsServiceUnavailableInsteadOfServerError() {
        Map<String, Route> routesById = routeLocator.getRoutes()
                .collect(Collectors.toMap(Route::getId, Function.identity())).block();
        assertThat(context.getBean(CustomErrorAttributes.class)).isNotNull();
        Route testRoute = routesById.get("unknownHostRoute");
        assertNotNull(testRoute);
        assertThat(testRoute.getUri()).isEqualTo(URI.create("http://not.a.valid.host:80"));

        testClient.get().uri("/path/to/unavailable/service")//
                .header("Host", "localhost")//
                .exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
