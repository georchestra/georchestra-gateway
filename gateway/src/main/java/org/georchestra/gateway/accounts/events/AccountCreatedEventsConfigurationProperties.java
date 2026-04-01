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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.Generated;

/**
 * Configuration properties for RabbitMQ event dispatching related to account
 * creation.
 * <p>
 * These properties define how geOrchestra should publish events to RabbitMQ
 * when a new LDAP account is created following a user's first successful login
 * via OAuth2 authentication.
 * </p>
 * <p>
 * The properties are prefixed with
 * {@code georchestra.gateway.security.events.rabbitmq} and can be configured in
 * the application's configuration file (e.g., {@code application.yml}).
 * </p>
 *
 * <p>
 * <b>Example Configuration:</b>
 * </p>
 * 
 * <pre>
 * georchestra:
 *   gateway:
 *     security:
 *       events:
 *         accountcreated:
 *           url: true
 * </pre>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Data
@Generated
@Validated
@ConfigurationProperties(prefix = AccountCreatedEventsConfigurationProperties.PREFIX)
public class AccountCreatedEventsConfigurationProperties {

    /** The prefix for accountcretad configuration properties. */
    public static final String PREFIX = "georchestra.gateway.security.events.accountcreated";

    /** The configuration key to enable or disable event dispatching. */
    public static final String PROPERTY_EXISTS = PREFIX + ".url";

    /**
     * The server endpoint, execute a POST on this url.
     */
    public String url;

}
