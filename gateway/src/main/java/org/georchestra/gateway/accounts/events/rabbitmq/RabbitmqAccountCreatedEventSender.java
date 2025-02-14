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
package org.georchestra.gateway.accounts.events.rabbitmq;

import java.util.UUID;

import org.georchestra.gateway.accounts.admin.AccountCreated;
import org.georchestra.security.model.GeorchestraUser;
import org.json.JSONObject;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.event.EventListener;

/**
 * A service bean that listens for {@link AccountCreated} events and publishes a
 * distributed event through RabbitMQ to the {@literal OAUTH2-ACCOUNT-CREATION}
 * queue.
 * <p>
 * This class is responsible for notifying other services when a new user
 * account is created via OAuth2 authentication. It transforms the event data
 * into a JSON message and sends it to the configured RabbitMQ routing key.
 * </p>
 *
 * @see AccountCreated
 * @see AmqpTemplate
 */
public class RabbitmqAccountCreatedEventSender {

    /** The RabbitMQ queue name for OAuth2 account creation events. */
    public static final String OAUTH2_ACCOUNT_CREATION = "OAUTH2-ACCOUNT-CREATION";

    /** The AMQP template for sending messages to the RabbitMQ exchange. */
    private final AmqpTemplate eventTemplate;

    /**
     * Constructs a new {@code RabbitmqAccountCreatedEventSender}.
     *
     * @param eventTemplate the AMQP template used for sending messages
     */
    public RabbitmqAccountCreatedEventSender(AmqpTemplate eventTemplate) {
        this.eventTemplate = eventTemplate;
    }

    /**
     * Handles {@link AccountCreated} events and sends a message to the RabbitMQ
     * queue if the new account was created via an OAuth2 provider.
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
     * Sends a message to RabbitMQ indicating that a new OAuth2 user account has
     * been created.
     * <p>
     * This method constructs a JSON object containing user details and publishes it
     * to the RabbitMQ exchange with the routing key {@code routing-gateway}.
     * </p>
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

        // Publish the message to the RabbitMQ queue
        eventTemplate.convertAndSend("routing-gateway", jsonObj.toString());
    }
}
