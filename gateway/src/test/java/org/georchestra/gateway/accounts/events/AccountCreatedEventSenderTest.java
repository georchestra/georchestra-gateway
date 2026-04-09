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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.gateway.accounts.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.georchestra.gateway.accounts.admin.AccountCreated;
import org.georchestra.security.model.GeorchestraUser;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import reactor.netty.http.client.HttpClient;

/**
 * Test class for {@link AccountCreatedEventSender}. Tests the event handling
 * and HTTP POST functionality using WireMock.
 */
class AccountCreatedEventSenderTest {

    private WireMockServer wireMockServer;
    private AccountCreatedEventSender eventSender;
    private String targetUrl;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        targetUrl = "http://localhost:" + wireMockServer.port() + "/events";
        eventSender = new AccountCreatedEventSender(targetUrl, HttpClient.create());
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    void testOnAccountCreatedWithOAuth2Provider() throws JSONException {
        // Setup WireMock stub
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/events")).willReturn(WireMock.aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"status\":\"success\"}")));

        // Create test user with OAuth2 provider
        GeorchestraUser user = new GeorchestraUser();
        user.setUsername("testuser");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        user.setOrganization("Example Corp");
        user.setOAuth2Provider("Google");
        user.setOAuth2Uid("google-123456");

        AccountCreated event = new AccountCreated(user);

        // Execute
        eventSender.on(event);

        // Verify - wait for async operation
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/events"))
                        .withHeader("Content-Type", WireMock.equalTo("application/json"))));

        // Verify request body contains expected fields
        String requestBody = wireMockServer.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        JSONObject json = new JSONObject(requestBody);

        assertThat(json.getString("subject")).isEqualTo(AccountCreatedEventSender.OAUTH2_ACCOUNT_CREATION);
        assertThat(json.getString("fullName")).isEqualTo("John Doe");
        assertThat(json.getString("localUid")).isEqualTo("testuser");
        assertThat(json.getString("email")).isEqualTo("john.doe@example.com");
        assertThat(json.getString("organization")).isEqualTo("Example Corp");
        assertThat(json.getString("providerName")).isEqualTo("Google");
        assertThat(json.getString("providerUid")).isEqualTo("google-123456");
        assertThat(json.has("uid")).isTrue();
    }

    @Test
    void testOnAccountCreatedWithoutOAuth2Provider() {
        // Setup WireMock stub (should not be called)
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/events")).willReturn(WireMock.aResponse().withStatus(200)));

        // Create test user without OAuth2 provider
        GeorchestraUser user = new GeorchestraUser();
        user.setUsername("testuser");
        user.setFirstName("Jane");
        user.setLastName("Smith");
        user.setEmail("jane.smith@example.com");
        user.setOrganization("Example Corp");
        // OAuth2Provider is null

        AccountCreated event = new AccountCreated(user);

        // Execute
        eventSender.on(event);

        // Should not have sent any request during a short observation window
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(1)).untilAsserted(
                () -> wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/events"))));
    }

    @Test
    void testSendNewOAuthAccountMessage() {
        // Setup WireMock stub
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/events"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"status\":\"success\"}")));

        // Execute
        eventSender.sendNewOAuthAccountMessage("Alice Wonderland", "awonderland", "alice@example.com", "Wonderland Inc",
                "GitHub", "github-789");

        // Verify
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/events"))));
    }

    @Test
    void testSendNewOAuthAccountMessageWithNullUrl() {
        // Create sender with null URL
        AccountCreatedEventSender nullUrlSender = new AccountCreatedEventSender(null, HttpClient.create());

        // This should log a warning and not throw exception
        nullUrlSender.sendNewOAuthAccountMessage("Test User", "testuser", "test@example.com", "Test Org", "Provider",
                "uid-123");

        // No assertion needed - just verify it doesn't throw
    }

    @Test
    void testSendNewOAuthAccountMessageWithEmptyUrl() {
        // Create sender with empty URL
        AccountCreatedEventSender emptyUrlSender = new AccountCreatedEventSender("", HttpClient.create());

        // This should log a warning and not throw exception
        emptyUrlSender.sendNewOAuthAccountMessage("Test User", "testuser", "test@example.com", "Test Org", "Provider",
                "uid-123");

        // No assertion needed - just verify it doesn't throw
    }

    @Test
    void testSendNewOAuthAccountMessageWithErrorResponse() {
        // Setup WireMock stub with error response
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/events"))
                .willReturn(WireMock.aResponse().withStatus(500).withBody("{\"error\":\"Internal server error\"}")));

        // Execute
        eventSender.sendNewOAuthAccountMessage("Bob Builder", "bbuilder", "bob@example.com", "Builder Inc", "Azure",
                "azure-456");

        // Verify request was made despite error
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/events"))));
    }

    @Test
    void testJsonStructure() {
        // Setup WireMock stub
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/events")).willReturn(WireMock.aResponse().withStatus(200)));

        // Execute
        eventSender.sendNewOAuthAccountMessage("Test User", "testuser", "test@example.com", "Test Org", "TestProvider",
                "test-uid-123");

        // Verify JSON structure
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var serveEvents = wireMockServer.getAllServeEvents();
            assertThat(serveEvents).isNotEmpty();
            String requestBody = serveEvents.getFirst().getRequest().getBodyAsString();
            JSONObject json = new JSONObject(requestBody);

            // Verify all required fields are present
            assertThat(json.has("uid")).isTrue();
            assertThat(json.has("subject")).isTrue();
            assertThat(json.has("fullName")).isTrue();
            assertThat(json.has("localUid")).isTrue();
            assertThat(json.has("email")).isTrue();
            assertThat(json.has("organization")).isTrue();
            assertThat(json.has("providerName")).isTrue();
            assertThat(json.has("providerUid")).isTrue();

            // Verify values
            assertThat(json.getString("subject")).isEqualTo("OAUTH2-ACCOUNT-CREATION");
            assertThat(json.getString("fullName")).isEqualTo("Test User");
            assertThat(json.getString("localUid")).isEqualTo("testuser");
            assertThat(json.getString("email")).isEqualTo("test@example.com");
            assertThat(json.getString("organization")).isEqualTo("Test Org");
            assertThat(json.getString("providerName")).isEqualTo("TestProvider");
            assertThat(json.getString("providerUid")).isEqualTo("test-uid-123");
        });
    }

    @Test
    void testMultipleEvents() {
        // Setup WireMock stub
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/events")).willReturn(WireMock.aResponse().withStatus(200)));

        // Send multiple events
        for (int i = 0; i < 3; i++) {
            GeorchestraUser user = new GeorchestraUser();
            user.setUsername("user" + i);
            user.setFirstName("First" + i);
            user.setLastName("Last" + i);
            user.setEmail("user" + i + "@example.com");
            user.setOrganization("Org" + i);
            user.setOAuth2Provider("Provider" + i);
            user.setOAuth2Uid("uid-" + i);

            AccountCreated event = new AccountCreated(user);
            eventSender.on(event);
        }

        // Verify all three requests were made
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> wireMockServer.verify(3, WireMock.postRequestedFor(WireMock.urlEqualTo("/events"))));
    }
}
