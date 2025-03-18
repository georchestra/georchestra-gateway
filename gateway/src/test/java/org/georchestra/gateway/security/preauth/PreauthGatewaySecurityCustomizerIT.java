package org.georchestra.gateway.security.preauth;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.georchestra.commons.security.SecurityHeaders.SEC_EXTERNAL_AUTHENTICATION;
import static org.georchestra.commons.security.SecurityHeaders.SEC_ROLES;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import lombok.extern.slf4j.Slf4j;

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
        String propertyName = "georchestra.gateway.services.%s.target".formatted("test");
        registry.add(propertyName, () -> proxiedURI);
        registry.add("spring.cloud.gateway.routes[0].id", () -> "test");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> proxiedURI);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/test");
    }

    private @Autowired WebTestClient testClient;

    @Test
    void testProxifiedRequestNoPreauthHeaders() {
        mockService.stubFor(get(urlMatching("/test"))//
                .willReturn(ok()));

        testClient.get().uri("/test").exchange().expectStatus().is2xxSuccessful();

        List<LoggedRequest> requests = mockService.findAll(getRequestedFor(urlEqualTo("/test")));
        requests.forEach(req -> {
            assertTrue(req.getHeaders().keys().stream().filter(h -> h.startsWith("preauth-")).toList().isEmpty());

        });
    }

    @Test
    void testProxifiedRequestPreauthSentButSanitized() {
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
            assertTrue(req.getHeaders().keys().stream().filter(h -> h.startsWith("preauth-")).toList().isEmpty());
            // but still the regular sec-* ones
            assertFalse(req.getHeader(SEC_ROLES).isEmpty());
        });
    }

    @Test
    void testProxifiedRequestWithExternalAuthenticationHeaderAttribute() {
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