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

package org.georchestra.gateway.security.accessrules;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import java.net.URI;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration tests for {@link AccessRulesCustomizer} for the access rules in
 * the default {@literal gateway.yml} config file.
 *
 */
@SpringBootTest(classes = GeorchestraGatewayApplication.class, webEnvironment = WebEnvironment.MOCK, properties = {
        "georchestra.datadir=../datadir"//
        , "georchestra.gateway.security.ldap.default.enabled=false"//
})
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles("it")
@Slf4j
class AccessRulesCustomizerIT {

    @RegisterExtension
    static WireMockExtension mockService = WireMockExtension.newInstance()
            .options(new WireMockConfiguration().dynamicPort().dynamicHttpsPort()).build();

    /**
     * Configure the target service mappings to call the {@link #mockService} at its
     * dynamically allocated port
     *
     * @see #mockServiceTarget
     */
    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        log.debug("redirecting target URLs to WireMock dynamic base '{}'",
                mockService.getRuntimeInfo().getHttpBaseUrl());

        mockServiceTarget(registry, "header", "/header");
        mockServiceTarget(registry, "geoserver", "/geoserver");
        mockServiceTarget(registry, "console", "/console");
        mockServiceTarget(registry, "analytics", "/analytics");
        mockServiceTarget(registry, "datafeeder", "/datafeeder");
        mockServiceTarget(registry, "import", "/import");
        mockServiceTarget(registry, "geowebcache", "/geowebcache");
        mockServiceTarget(registry, "geonetwork", "/geonetwork");
        mockServiceTarget(registry, "mapstore", "/mapstore");
    }

    /**
     * Sets a {@literal georchestra.gateway.services.<service>.target} configuration
     * property for the given {@code serviceName} to be mapped to the Wiremock
     * server at the {@code targetBaseURI} uri.
     * <p>
     * For example, if the Wiremock service is at port 7654, for the
     * {@literal header} service with {@literal /header} target URI, the config
     * property would be:
     *
     * <pre>
     * {@code georchestra.gateway.services.header.target=http://localhost:7654/header}
     * </pre>
     *
     * @param registry      the dynamic property source to contribute to the
     *                      application context's environment
     * @param serviceName   the name of the service for a
     *                      {@literal georchestra.gateway.services.<service>.target}
     *                      property
     * @param targetBaseURI the target URI to map the service target to the Wiremock
     *                      instance
     */
    private static void mockServiceTarget(DynamicPropertyRegistry registry, String serviceName, String targetBaseURI) {
        WireMockRuntimeInfo runtimeInfo = mockService.getRuntimeInfo();
        String httpBaseUrl = runtimeInfo.getHttpBaseUrl();
        String proxiedURI = URI.create(httpBaseUrl + "/" + targetBaseURI).normalize().toString();
        String propertyName = "georchestra.gateway.services.%s.target".formatted(serviceName);
        registry.add(propertyName, () -> proxiedURI);
        log.debug("overridden dynamic target {}={}", propertyName, proxiedURI);
    }

    private @Autowired WebTestClient testClient;

    /**
     * <pre>
     * {@code
     * georchestra.gateway.services.header:
     *  access-rules:
     *  - intercept-url: /header/**
     *    anonymous: true
     * }
     * </pre>
     */
    @Test
    void testSimpleMapping_anonymous() {
        mockService.stubFor(get(urlMatching("/header(/.*)?"))//
                .withHeader("sec-proxy", equalTo("true"))//
                .willReturn(ok()));

        testClient.get().uri("/header")//
                .header("Host", "localhost")//
                .exchange().expectStatus().isOk();
        testClient.get().uri("/header/img/logo.png")//
                .header("Host", "localhost")//
                .exchange().expectStatus().isOk();
    }

    /**
     * <pre>
     * {@code
     * georchestra.gateway.services.import:
     *   access-rules:
     *   - intercept-url: /import/**
     *     anonymous: false
     * }
     */
    @Test
    void testService_unauthorized_if_not_logged_in_and_requires_any_authenticated_user() {
        mockService.stubFor(get(urlMatching("/import(/.*)?")).willReturn(noContent()));

        testClient.get().uri("/import")//
                .exchange()//
                .expectStatus().isFound();

        testClient.get().uri("/import/any/thing")//
                .exchange()//
                .expectStatus().isFound();
    }

    /**
     * <pre>
     * {@code
     * georchestra.gateway.services.import:
     *   access-rules:
     *   - intercept-url: /import/**
     *     anonymous: false
     * }
     */
    @WithMockUser(authorities = { "ROLE_DOESNTMATTER" })
    @Test
    void testService_requires_any_authenticated_user() {
        mockService.stubFor(get(urlMatching("/import(/.*)?")).willReturn(ok()));

        testClient.get().uri("/import")//
                .header("Host", "localhost")//
                .exchange()//
                .expectStatus().isOk();

        testClient.get().uri("/import/any/thing")//
                .header("Host", "localhost")//
                .exchange()//
                .expectStatus().isOk();
    }

    /**
     * <pre>
     * {@code
     * georchestra.gateway.services.analytics:
     *   access-rules:
     *   - intercept-url: /analytics/**
     *     allowed-roles: SUPERUSER,ORGADMIN
     * }
     */
    @WithMockUser(authorities = { "ROLE_USER", "ROLE_EDITOR" })
    @Test
    void testService_requires_specific_role_forbidden_for_non_matching_roles() {
        mockService.stubFor(get(urlMatching("/analytics(/.*)?")).willReturn(ok()));

        testClient.get().uri("/analytics")//
                .exchange()//
                .expectStatus().isForbidden();

        testClient.get().uri("/analytics/any/thing")//
                .exchange()//
                .expectStatus().isForbidden();
    }

    /**
     * <pre>
     * {@code
     * georchestra.gateway.services.analytics:
     *   access-rules:
     *   - intercept-url: /analytics/**
     *     allowed-roles: SUPERUSER,ORGADMIN
     * }
     */
    @WithMockUser(authorities = { "ROLE_USER", "ROLE_ORGADMIN" })
    @Test
    void testService_requires_specific_role_allowed_for_matching_roles() {
        mockService.stubFor(get(urlMatching("/analytics(/.*)?")).willReturn(ok()));

        testClient.get().uri("/analytics")//
                .header("Host", "localhost")//
                .exchange()//
                .expectStatus().isOk();

        testClient.get().uri("/analytics/any/thing")//
                .header("Host", "localhost")//
                .exchange()//
                .expectStatus().isOk();
    }

    /**
     * <pre>
     * {@code
     * georchestra.gateway.services.analytics:
     *   access-rules:
     *   - intercept-url: /analytics/**
     *     allowed-roles: SUPERUSER,ORGADMIN
     * }
     */
    @Test
    void testService_unauthorized_if_not_logged_in_and_requires_role() {
        mockService.stubFor(get(urlMatching("/analytics(/.*)?")).willReturn(ok()));

        testClient.get().uri("/analytics")//
                .exchange()//
                .expectStatus().isFound();

        testClient.get().uri("/analytics/any/thing")//
                .exchange()//
                .expectStatus().isFound();
    }

    /**
     * <pre>
     * {@code
     *     georchestra.gateway:
     * 	    global-access-rules:
     * 	    - intercept-url:
     * 	      - /**
     * 	      anonymous: true
     * 	    services:
     * 	      mapstore:
     * 	        target: http://mapstore:8080/mapstore/
     * }
     * </pre>
     */
    @Test
    void testGlobalAccessRule() {
        mockService.stubFor(get(urlMatching("/mapstore(/.*)?")).willReturn(ok()));

        testClient.get().uri("/mapstore")//
                .header("Host", "localhost")//
                .exchange()//
                .expectStatus().isOk();

        testClient.get().uri("/mapstore/any/thing")//
                .header("Host", "localhost")//
                .exchange()//
                .expectStatus().isOk();
    }
}
