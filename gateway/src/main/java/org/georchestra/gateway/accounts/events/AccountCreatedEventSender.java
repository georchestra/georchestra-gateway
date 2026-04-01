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

import java.util.UUID;

import org.georchestra.gateway.accounts.admin.AccountCreated;
import org.georchestra.security.model.GeorchestraUser;
import org.json.JSONObject;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A service bean that listens for {@link AccountCreated} events and post user to url set in properties
 * queue.
 * <p>
 * This class is responsible for notifying other services when a new user
 * account is created via OAuth2 authentication. It transforms the event data
 * into a JSON message and sends it to the URL configured .
 * </p>
 *
 * @see AccountCreated
 */
@Slf4j
@RequiredArgsConstructor
public class AccountCreatedEventSender {

    /** The name for OAuth2 account creation events. */
    public static final String OAUTH2_ACCOUNT_CREATION = "OAUTH2-ACCOUNT-CREATION";

    private final String url;
    private final HttpClient httpClient;

    /**
     * Handles {@link AccountCreated} events and sends a message.
     *
     * @param event the {@link AccountCreated} event containing user details
     */
    @EventListener
    public void on(AccountCreated event) {
        GeorchestraUser user = event.getUser();
        final String oAuth2Provider = user.getOAuth2Provider();

        // Only send events for OAuth2-authenticated users
        if (oAuth2Provider != null) {
            String fullName = user.getFirstName() + " " + user.getLastName();
            String localUid = user.getUsername();
            String email = user.getEmail();
            String organization = user.getOrganization();
            String oAuth2Uid = user.getOAuth2Uid();
            sendNewOAuthAccountMessage(fullName, localUid, email, organization, oAuth2Provider, oAuth2Uid);
        }
    }

    /**
     * Sends a message to the specified URL indicating that a new OAuth2 user account has
     * been created.
     *
     * <p>
     * <b>Example JSON output:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "uid": "550e8400-e29b-41d4-a716-446655440000",
     *   "subject": "OAUTH2-ACCOUNT-CREATION",
     *   "fullName": "John Doe",
     *   "localUid": "jdoe",
     *   "email": "johndoe@example.com",
     *   "organization": "Example Corp",
     *   "providerName": "Google",
     *   "providerUid": "1234567890"
     * }
     * </pre>
     *
     * @param fullName     the full name of the user
     * @param localUid     the local username assigned to the user
     * @param email        the email address of the user
     * @param organization the organization to which the user belongs
     * @param providerName the name of the OAuth2 provider (e.g., Google, GitHub)
     * @param providerUid  the unique identifier assigned by the OAuth2 provider
     */
    public void sendNewOAuthAccountMessage(String fullName, String localUid, String email, String organization,
            String providerName, String providerUid) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("uid", UUID.randomUUID());
        jsonObj.put("subject", OAUTH2_ACCOUNT_CREATION);
        jsonObj.put("fullName", fullName);
        jsonObj.put("localUid", localUid);
        jsonObj.put("email", email);
        jsonObj.put("organization", organization);
        jsonObj.put("providerName", providerName);
        jsonObj.put("providerUid", providerUid);

        // Publish the message to url

        if (url == null || url.isEmpty()) {
            log.warn("No URL configured for account creation events. Skipping event dispatch.");
            return;
        }

        String jsonString = jsonObj.toString();
        log.debug("Sending OAuth2 account creation event to {}: {}", url, jsonString);

        httpClient.headers(h -> {
            h.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        }).post().uri(url).send(ByteBufFlux.fromString(Mono.just(jsonString))).responseSingle((response, body) -> {
            if (response.status().code() == 200) {
                log.info("Successfully sent OAuth2 account creation event for user {} to {}", localUid, url);
                return Mono.just(response.status().code());
            } else {
                log.error("Failed to send OAuth2 account creation event to {}. Status: {}", url, response.status());
                return body.asString().doOnNext(errorBody -> log.error("Error response body: {}", errorBody))
                        .then(Mono.just(response.status().code()));
            }
        }).subscribe(statusCode -> log.debug("HTTP POST completed with status code: {}", statusCode), error -> log
                .error("Error sending OAuth2 account creation event to {}: {}", url, error.getMessage(), error));
    }
}
