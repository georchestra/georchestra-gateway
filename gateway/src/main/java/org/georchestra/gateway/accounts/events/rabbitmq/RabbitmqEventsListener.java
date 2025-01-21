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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import org.json.JSONObject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitmqEventsListener implements MessageListener {

    public static final String OAUTH2_ACCOUNT_CREATION_RECEIVED = "OAUTH2-ACCOUNT-CREATION-RECEIVED";

    private static Set<String> synReceivedMessageUid = Collections.synchronizedSet(new HashSet<String>());

    public void onMessage(Message message) {
        try {
            String messageBody = new String(message.getBody());
            JSONObject jsonObj = new JSONObject(messageBody);
            String uid = jsonObj.getString("uid");
            String subject = jsonObj.getString("subject");
            if (subject.equals(OAUTH2_ACCOUNT_CREATION_RECEIVED)
                    && !synReceivedMessageUid.stream().anyMatch(s -> s.equals(uid))) {
                String msg = jsonObj.getString("msg");
                synReceivedMessageUid.add(uid);
                log.info(msg);
            }

        } catch (Exception e) {
            log.error("Exception caught when evaluating a message from RabbitMq, it will be silently discarded.", e);
        }
    }

    @VisibleForTesting
    public static Set<String> getSynReceivedMessageUid() {
        return synReceivedMessageUid;
    }
}