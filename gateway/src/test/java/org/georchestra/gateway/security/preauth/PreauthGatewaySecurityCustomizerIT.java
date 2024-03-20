package org.georchestra.gateway.security.preauth;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import lombok.extern.slf4j.Slf4j;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.georchestra.commons.security.SecurityHeaders.SEC_EXTERNAL_AUTHENTICATION;
import static org.georchestra.commons.security.SecurityHeaders.SEC_ROLES;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = GeorchestraGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles("preauth")
@Slf4j
class PreauthGatewaySecurityCustomizerIT {

    @RegisterExtension
    static WireMockExtension mockService = WireMockExtension.newInstance()
            .options(new WireMockConfiguration().dynamicPort().dynamicHttpsPort()).build();

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        log.debug("redirecting target URLs to WireMock dynamic base '{}'",
                mockService.getRuntimeInfo().getHttpBaseUrl());
        WireMockRuntimeInfo runtimeInfo = mockService.getRuntimeInfo();
        String httpBaseUrl = runtimeInfo.getHttpBaseUrl();
        String proxiedURI = URI.create(httpBaseUrl + "/" + "test").normalize().toString();
        String propertyName = String.format("georchestra.gateway.services.%s.target", "test");
        registry.add(propertyName, () -> proxiedURI);
        registry.add("spring.cloud.gateway.routes[0].id", () -> "test");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> proxiedURI);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/test");
    }

    private @Autowired RouteLocator routeLocator;
    private @Autowired WebTestClient testClient;

    public @Test void testProxifiedRequestNoPreauthHeaders() {
        mockService.stubFor(get(urlMatching("/test"))//
                .willReturn(ok()));

        testClient.get().uri("/test").exchange().expectStatus().is2xxSuccessful();

        List<LoggedRequest> requests = mockService.findAll(getRequestedFor(urlEqualTo("/test")));
        requests.forEach(req -> {
            assertTrue(req.getHeaders().keys().stream().filter(h -> h.startsWith("preauth-"))
                    .collect(Collectors.toList()).isEmpty());

        });
    }

    public @Test void testProxifiedRequestPreauthSentButSanitized() {
        mockService.stubFor(get(urlMatching("/test"))//
                .willReturn(ok()));

        testClient.get().uri("/test").headers(h -> { //
            h.set("sec-georchestra-preauthenticated", "true"); //
            h.set("preauth-username", "testadmin"); //
            h.set("preauth-email", "testadmin@example.org"); //
            h.set("preauth-firstname", "Test"); //
            h.set("preauth-lastname", "Admin"); //
            h.set("preauth-org", "PSC"); //
        }).exchange().expectStatus().is2xxSuccessful();

        List<LoggedRequest> requests = mockService.findAll(getRequestedFor(urlEqualTo("/test")));
        requests.forEach(req -> {
            // no 'preauth-*' headers in the received request
            assertTrue(req.getHeaders().keys().stream().filter(h -> h.startsWith("preauth-"))
                    .collect(Collectors.toList()).isEmpty());
            // but still the regular sec-* ones
            assertFalse(req.getHeader(SEC_ROLES).isEmpty());
        });
    }

    public @Test void testProxifiedRequestWithExternalAuthenticationHeaderAttribute() {
        mockService.stubFor(get(urlMatching("/test"))//
                .willReturn(ok()));

        testClient.get().uri("/test").headers(h -> { //
            h.set("sec-georchestra-preauthenticated", "true"); //
            h.set("preauth-username", "testadmin"); //
            h.set("preauth-email", "testadmin@example.org"); //
            h.set("preauth-firstname", "Test"); //
            h.set("preauth-lastname", "Admin"); //
            h.set("preauth-org", "PSC"); //
        }).exchange().expectStatus().is2xxSuccessful();

        List<LoggedRequest> requests = mockService.findAll(getRequestedFor(urlEqualTo("/test")));
        requests.forEach(req -> {
            assertFalse(req.getHeader(SEC_EXTERNAL_AUTHENTICATION).isEmpty());
        });

    }
}