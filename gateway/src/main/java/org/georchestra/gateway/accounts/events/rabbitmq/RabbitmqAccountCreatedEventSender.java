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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.accounts.events.rabbitmq;

import java.util.UUID;

import org.georchestra.gateway.accounts.admin.AccountCreated;
import org.georchestra.security.model.GeorchestraUser;
import org.json.JSONObject;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.event.EventListener;

/**
 * Service bean that listens to {@link AccountCreated} events and publish a
 * distributed event through rabbitmq to the {@literal OAUTH2-ACCOUNT-CREATION}
 * queue.
 */
public class RabbitmqAccountCreatedEventSender {

    public static final String OAUTH2_ACCOUNT_CREATION = "OAUTH2-ACCOUNT-CREATION";

    private AmqpTemplate eventTemplate;

    public RabbitmqAccountCreatedEventSender(AmqpTemplate eventTemplate) {
        this.eventTemplate = eventTemplate;
    }

    @EventListener(AccountCreated.class)
    public void on(AccountCreated event) {
        GeorchestraUser user = event.getUser();
        final String oAuth2Provider = user.getOAuth2Provider();
        if (null != oAuth2Provider) {
            String fullName = user.getFirstName() + " " + user.getLastName();
            String localUid = user.getUsername();
            String email = user.getEmail();
            String organization = user.getOrganization();
            String oAuth2Uid = user.getOAuth2Uid();
            sendNewOAuthAccountMessage(fullName, localUid, email, organization, oAuth2Provider, oAuth2Uid);
        }
    }

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
        eventTemplate.convertAndSend("routing-gateway", jsonObj.toString());// send
    }
}