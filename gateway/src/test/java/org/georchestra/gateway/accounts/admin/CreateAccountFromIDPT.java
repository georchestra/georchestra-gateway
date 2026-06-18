package org.georchestra.gateway.accounts.admin;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.jsonwebtoken.Jwts;

@SpringBootTest(classes = GeorchestraGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles("createaccount-oauth2")
@Testcontainers(disabledWithoutDocker = true)
@WireMockTest
public class CreateAccountFromIDPT {

    private @Autowired WebTestClient testClient;
    private @Autowired AccountDao accountDao;

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();
    private static WireMockRuntimeInfo wireMockRuntimeInfo;

    @BeforeAll
    static void saveWireMockRuntimeInfo(WireMockRuntimeInfo runtimeInfo) {
        wireMockRuntimeInfo = runtimeInfo;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("testcontainers.georchestra.ldap.host", () -> "127.0.0.1");
        registry.add("testcontainers.georchestra.ldap.port", ldap::getMappedLdapPort);

        // Override the Google provider endpoints to point to WireMock instead of Google.
        // We keep issuer-uri unset so CommonOAuth2Provider.GOOGLE's default
        // ("https://accounts.google.com") is preserved — the JWT iss must match it.
        String mockBaseUrl = wireMockRuntimeInfo.getHttpBaseUrl();
        registry.add("spring.security.oauth2.client.provider.keycloak.authorization-uri",
                () -> mockBaseUrl + "/o/oauth2/auth");
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> mockBaseUrl + "/oauth2/v4/token");
        registry.add("spring.security.oauth2.client.provider.keycloak.user-info-uri",
                () -> mockBaseUrl + "/oauth2/v3/userinfo");
        registry.add("spring.security.oauth2.client.provider.keycloak.user-name-attribute", () -> "sub");
    }

    @Test
    void testCreateAccountFromOidcIDP() throws Exception {
        // ── Step 1 ─────────────────────────────────────────────────────────────────
        // Initiate the OAuth2 flow. Spring Security:
        //  - generates a raw nonce, stores it in the WebSession
        //  - sends the HASH of the nonce to the IDP in the redirect URL
        //  - generates a `state` parameter (also stored in session)

        EntityExchangeResult<Void> authInitResult = testClient.get()
                .uri("/oauth2/authorization/keycloak")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectBody(Void.class)
                .returnResult();

        HttpHeaders responseHeaders = authInitResult.getResponseHeaders();
        String location = responseHeaders.getFirst(HttpHeaders.LOCATION);
        assertThat(location).as("redirect to mock IDP").isNotNull().contains("/o/oauth2/auth");

        // Extract `state` and `nonce` (= H(raw_nonce)) from the redirect URL.
        Map<String, String> redirectParams = parseQueryParams(location);
        String state = redirectParams.get("state");
        String nonceHash = redirectParams.get("nonce"); // already the SHA-256 hash
        assertThat(state).as("state").isNotNull();
        assertThat(nonceHash).as("nonce").isNotNull();

        // Extract the session cookie — must be replayed on the callback request so
        // Spring Security can retrieve the stored AuthorizationRequest (nonce, state…).
        // IMPORTANT: use .cookie("SESSION", value) not .header("Cookie", "SESSION=value").
        // In WebTestClient mock mode, Cookie headers are NOT parsed into cookies;
        // only .cookie() goes directly into the request's cookie map.
        List<String> setCookieHeaders = responseHeaders.get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).as("Set-Cookie").isNotEmpty();
        String sessionId = setCookieHeaders.stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";")[0].substring("SESSION=".length())) // raw value only
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SESSION cookie in response"));

        // ── Step 2 ─────────────────────────────────────────────────────────────────
        // Build the ID token the mock IDP will return.
        // - iss   : must match CommonOAuth2Provider.GOOGLE issuerUri
        // - aud   : must contain the client-id from the registration
        // - nonce : must be the HASH that Spring Security stored in the authorization
        //           request (= what we extracted from the redirect URL)
        // - signed with HS256 using client_secret (padded to 64 bytes, same logic as
        //   OAuth2Configuration.idTokenDecoderFactory)
        String idToken = buildIdToken(nonceHash, "test-user-123", "pmartin@example.org", "Pierre", "Martin", "pmartin");

        // ── Step 3 ─────────────────────────────────────────────────────────────────
        // Stub the token endpoint: gateway POSTs the authorization code here.
        // If the callback times out here, enable the WireMock request log in Step 4:
        //   wmRuntimeInfo.getWireMock().getServeEvents().getServeEvents()
        //       .forEach(e -> System.err.println(e.getRequest().getMethod() + " " + e.getRequest().getUrl()));
        stubFor(post(urlPathEqualTo("/oauth2/v4/token"))
                .willReturn(okJson("""
                        {
                          "access_token": "test-access-token",
                          "token_type":   "Bearer",
                          "expires_in":   3600,
                          "id_token":     "%s"
                        }
                        """.formatted(idToken))));

        // Stub the userinfo endpoint: gateway GETs user attributes here.
        // OidcReactiveOAuth2UserService always calls userinfo when user-info-uri is set.
        stubFor(get(urlPathEqualTo("/oauth2/v3/userinfo"))
                .willReturn(okJson("""
                        {
                          "sub":                "test-user-123",
                          "email":              "pmartin@example.org",
                          "email_verified":     true,
                          "name":               "Pierre Martin",
                          "given_name":         "Pierre",
                          "family_name":        "Martin",
                          "preferred_username": "pmartin"
                        }
                        """)));

        // ── Step 4 ─────────────────────────────────────────────────────────────────
        // Build the callback URI. .encode() percent-encodes the decoded '=' in state
        // → '%3D'. Then .toUri() (not .toUriString()) wraps it as a java.net.URI so
        // WebTestClient.uri(URI) forwards it as-is — no second encoding pass.
        // Using .uri(String) would re-encode '%' → '%25', turning '%3D' into '%253D'.
        URI callbackUri = UriComponentsBuilder.fromPath("/login/oauth2/code/keycloak")
                .queryParam("code", "test-auth-code")
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();

        EntityExchangeResult<Void> callbackResult = testClient.get()
                .uri(callbackUri)
                .cookie("SESSION", sessionId)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectBody(Void.class)
                .returnResult();

        // If Spring Security rotated the session, grab the new session ID.
        String authenticatedSessionId = Optional.ofNullable(
                callbackResult.getResponseHeaders().get(HttpHeaders.SET_COOKIE))
                .stream().flatMap(List::stream)
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";")[0].substring("SESSION=".length()))
                .findFirst()
                .orElse(sessionId);

        // ── Step 5 ─────────────────────────────────────────────────────────────────
        String whoamiBody = testClient.get()
                .uri("/whoami")
                .cookie("SESSION", authenticatedSessionId)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        System.err.println("=== WHOAMI RESPONSE ===\n" + whoamiBody);

        assertThat(whoamiBody)
                .as("Authentication must be non-null: if null, the session was not loaded")
                .doesNotContain("\"Authentication\":null");
        assertThat(whoamiBody)
                .as("GeorchestraUser must be resolved from OIDC claims")
                .doesNotContain("\"GeorchestraUser\":null");

        // ── Step 6 ─────────────────────────────────────────────────────────────────
        // registrationId "keycloak" + "_" + preferred_username → "keycloak_pmartin"
        assertNotNull(accountDao.findByUID("keycloak_pmartin"),
                "Account should have been created in LDAP by CreateAccountUserCustomizer");
    }

    /**
     * Builds a signed HS256 JWT ID token with the required OIDC claims.
     *
     * @param nonceHash the nonce as it appears in the IDP redirect URL (already
     *                  the SHA-256 hash of the raw nonce stored in the session)
     */
    private static String buildIdToken(String nonceHash, String sub, String email,
            String givenName, String familyName, String preferredUsername) {
        // Pad the client secret to 64 bytes — mirrors OAuth2Configuration.idTokenDecoderFactory
        byte[] secretBytes = "client_secret".getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 64) {
            secretBytes = Arrays.copyOf(secretBytes, 64);
        }
        SecretKeySpec key = new SecretKeySpec(secretBytes, "HmacSHA256");

        return Jwts.builder()
                // No issuer-uri configured → OidcIdTokenValidator skips iss validation
                .issuer("https://keycloak.example.com/realms/myrealm")
                .subject(sub)
                .audience().add("keycloak").and()   // must match registration clientId
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("nonce", nonceHash)               // hash that Spring Security will verify
                // Claims mapped by OpenIdConnectUserMapper
                .claim("email", email)
                .claim("given_name", givenName)
                .claim("family_name", familyName)
                .claim("preferred_username", preferredUsername)
                .signWith(key)
                .compact();
    }

    /** Splits a URL query string into a {@code key → first-value} map, percent-decoding each value. */
    private static Map<String, String> parseQueryParams(String url) {
        Map<String, String> params = new HashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams()
                // getQueryParams() returns RAW values (no %XX decoding) — decode explicitly
                .forEach((k, values) -> {
                    if (!values.isEmpty())
                        params.put(k, URLDecoder.decode(values.get(0), StandardCharsets.UTF_8));
                });
        return params;
    }
}
