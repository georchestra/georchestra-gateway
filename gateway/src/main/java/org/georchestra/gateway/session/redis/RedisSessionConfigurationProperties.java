/*
 * Copyright (C) 2026 by the geOrchestra PSC
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
package org.georchestra.gateway.session.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.Generated;

/**
 * Configuration properties for Redis-backed reactive session management.
 * <p>
 * These properties define how geOrchestra Gateway should configure Redis as a
 * session store for reactive web sessions. When enabled, user sessions will be
 * stored in Redis rather than in-memory, enabling session sharing across
 * multiple gateway instances and session persistence across restarts.
 * </p>
 * <p>
 * The properties are prefixed with {@code georchestra.gateway.session.redis}
 * and can be configured in the application's configuration file (e.g.,
 * {@code application.yml}).
 * </p>
 *
 * <p>
 * <b>Example Configuration:</b>
 * </p>
 * 
 * <pre>
 * georchestra:
 *   gateway:
 *     session:
 *       redis:
 *         enabled: true
 *         host: localhost
 *         port: 6379
 *         password: mySecretPassword
 *         database: 0
 * </pre>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Data
@Generated
@Validated
@ConfigurationProperties(prefix = RedisSessionConfigurationProperties.PREFIX)
public class RedisSessionConfigurationProperties {

    /** The prefix for all Redis session-related configuration properties. */
    public static final String PREFIX = "georchestra.gateway.session.redis";

    /** The configuration key to enable or disable Redis session storage. */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * Whether Redis should be used for session storage.
     * <p>
     * When enabled, Spring Session will store web sessions in Redis instead of
     * in-memory, allowing sessions to be shared across multiple gateway instances
     * and persisted across application restarts.
     * </p>
     * <p>
     * Default: {@code false}.
     * </p>
     */
    private boolean enabled = false;

    /**
     * Redis server hostname or IP address.
     * <p>
     * Default: {@code localhost}.
     * </p>
     */
    private String host = "localhost";

    /**
     * Redis server port.
     * <p>
     * Default: {@code 6379}.
     * </p>
     */
    private int port = 6379;

    /**
     * Password for authenticating with the Redis server.
     * <p>
     * Default: {@code null} (no authentication).
     * </p>
     */
    private String password;

    /**
     * Redis database index to use for session storage.
     * <p>
     * Default: {@code 0}.
     * </p>
     */
    private int database = 0;

    /**
     * Connection timeout in milliseconds.
     * <p>
     * Default: {@code 3000} (3 seconds).
     * </p>
     */
    private long timeout = 3000;
}
