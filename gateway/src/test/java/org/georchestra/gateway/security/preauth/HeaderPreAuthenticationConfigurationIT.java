package org.georchestra.gateway.security.preauth;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * Integration tests for {@link HeaderPreAuthenticationConfiguration}.
 */
@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles("preauth")
public class HeaderPreAuthenticationConfigurationIT {

    private @Autowired WebTestClient testClient;

    private @Autowired ApplicationContext context;

    private static final Map<String, String> ADMIN_HEADERS = Map.of(//
            "sec-georchestra-preauthenticated", "true", //
            "preauth-username", "pmartin", //
            "preauth-email", "pierre.martin@example.org", //
            "preauth-firstname", "Pierre", //
            "preauth-lastname", "Martin", //
            "preauth-org", "C2C", //
            "Accept", "application/json");
    private static final Map<String, String> ADMIN_HEADERS_CASE_INSENSITIVE = Map.of(//
            "Sec-Georchestra-PreAuthenticated", "true", //
            "Preauth-Username", "pmartin", //
            "Preauth-Email", "pierre.martin@example.org", //
            "Preauth-Firstname", "Pierre", //
            "Preauth-Lastname", "Martin", //
            "Preauth-Org", "C2C", //
            "Accept", "application/json");

    private WebTestClient.RequestHeadersUriSpec<?> prepareWebTestClientHeaders(
            WebTestClient.RequestHeadersUriSpec<?> spec, Map<String, String> headers) {
        headers.forEach((k, v) -> spec.header(k, v));
        return spec;
    }

    @Test
    void test_preauthenticatedHeadersAccess() {
        assertNotNull(context.getBean(PreauthGatewaySecurityCustomizer.class));
        assertNotNull(context.getBean(PreauthenticatedUserMapperExtension.class));

        ResponseSpec exchange = prepareWebTestClientHeaders(testClient.get(), ADMIN_HEADERS).uri("/whoami").exchange();
        BodyContentSpec body = exchange.expectStatus().is2xxSuccessful().expectBody();
        body.jsonPath("$.['GeorchestraUser']").isNotEmpty();
        body.jsonPath(
                "$.['org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken']")
                .isNotEmpty();
    }

    @Test
    void test_preauthenticatedHeadersAccess_case_insensitive() {
        assertNotNull(context.getBean(PreauthGatewaySecurityCustomizer.class));
        assertNotNull(context.getBean(PreauthenticatedUserMapperExtension.class));

        ResponseSpec exchange = prepareWebTestClientHeaders(testClient.get(), ADMIN_HEADERS_CASE_INSENSITIVE)
                .uri("/whoami").exchange();
        BodyContentSpec body = exchange.expectStatus().is2xxSuccessful().expectBody();
        body.jsonPath("$.['GeorchestraUser']").isNotEmpty();
        body.jsonPath(
                "$.['org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken']")
                .isNotEmpty();
    }

    @Test
    void test_preauthenticatedHeadersAccess_isAuthenticated() {
        assertNotNull(context.getBean(PreauthGatewaySecurityCustomizer.class));
        assertNotNull(context.getBean(PreauthenticatedUserMapperExtension.class));

        ResponseSpec exchange = prepareWebTestClientHeaders(testClient.get(), ADMIN_HEADERS).uri("/whoami").exchange();
        BodyContentSpec body = exchange.expectStatus().is2xxSuccessful().expectBody();
        body.jsonPath("$.['GeorchestraUser']").isNotEmpty();
        body.jsonPath(
                "$.['org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken'].authenticated")
                .isEqualTo(true);
    }

}
