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
package org.georchestra.gateway.autoconfigure.session;

import org.georchestra.gateway.session.redis.RedisSessionConfiguration;
import org.georchestra.gateway.session.redis.RedisSessionConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

/**
 * Auto-configuration for enabling Redis-backed reactive session storage when
 * configured.
 * <p>
 * This configuration enables Redis session management when the following
 * conditions are met:
 * <ul>
 * <li>Spring Session Data Redis classes are available on the classpath
 * ({@link ConditionalOnClass}).</li>
 * <li>The property {@code georchestra.gateway.session.redis.enabled} is set to
 * {@code true}.</li>
 * </ul>
 * </p>
 *
 * <p>
 * When enabled, user sessions will be stored in Redis rather than in-memory,
 * enabling session sharing across multiple gateway instances and session
 * persistence across application restarts.
 * </p>
 *
 * <p>
 * This class imports {@link RedisSessionConfiguration}, which defines the Redis
 * connection factory and session-related beans, and enables Redis WebSession
 * support through {@link EnableRedisWebSession}.
 * </p>
 *
 * @see RedisSessionConfiguration
 * @see RedisSessionConfigurationProperties
 * @see EnableRedisWebSession
 */
@AutoConfiguration
@ConditionalOnClass(EnableRedisWebSession.class)
@ConditionalOnProperty(name = RedisSessionConfigurationProperties.ENABLED, havingValue = "true", matchIfMissing = false)
@EnableRedisWebSession
@Import(RedisSessionConfiguration.class)
public class RedisSessionAutoConfiguration {

}
