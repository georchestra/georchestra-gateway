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

import java.time.Duration;

import org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken;
import org.springframework.boot.actuate.data.redis.RedisReactiveHealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;

/**
 * Configures Redis-backed reactive session management for the geOrchestra
 * Gateway.
 * <p>
 * This configuration enables Spring Session to use Redis as the session store
 * for reactive web sessions. When enabled, user sessions are stored in Redis,
 * allowing them to be shared across multiple gateway instances and persisted
 * across application restarts.
 * </p>
 * <p>
 * The configuration creates a reactive Redis connection factory for session
 * storage.
 * </p>
 *
 * @see RedisSessionConfigurationProperties
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableRedisWebSession
@EnableConfigurationProperties(RedisSessionConfigurationProperties.class)
public class RedisSessionConfiguration {

    /**
     * Configures a reactive Redis connection factory with connection pooling.
     * <p>
     * This method initializes a {@link LettuceConnectionFactory} using the Redis
     * connection properties defined in {@link RedisSessionConfigurationProperties}.
     * The Lettuce client is configured with:
     * <ul>
     * <li>Connection pooling for better performance</li>
     * <li>Optimized socket options for reduced latency</li>
     * <li>Automatic reconnection on connection loss</li>
     * </ul>
     * </p>
     * <p>
     * Marked as {@link Primary} to ensure this bean is used for session storage
     * when multiple Redis connection factories are present (e.g., when Spring Cloud
     * Gateway's Redis route definition store is also enabled).
     * </p>
     *
     * @param config the Redis session configuration properties
     * @return a configured {@link ReactiveRedisConnectionFactory} instance
     */
    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory(RedisSessionConfigurationProperties config) {
        log.info("Configuring Redis session storage: {}:{} with connection pooling", config.getHost(),
                config.getPort());

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(config.getHost());
        redisConfig.setPort(config.getPort());
        redisConfig.setDatabase(config.getDatabase());

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            redisConfig.setPassword(config.getPassword());
        }

        // Configure socket options for better performance
        SocketOptions socketOptions = SocketOptions.builder().connectTimeout(Duration.ofMillis(config.getTimeout()))
                .keepAlive(true).tcpNoDelay(true).build();

        // Configure client options with auto-reconnect
        ClientOptions clientOptions = ClientOptions.builder().socketOptions(socketOptions).autoReconnect(true).build();

        // Use connection pooling for better performance
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(config.getTimeout())).clientOptions(clientOptions).build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        factory.setValidateConnection(false); // Disable validation for better performance
        factory.setShareNativeConnection(true); // Share connections
        factory.setEagerInitialization(true); // Initialize connection pool on startup

        return factory;
    }

    /**
     * Configures JSON-based Spring Data Redis serializer for better performance.
     * <p>
     * Uses Jackson for JSON serialization instead of Java serialization, which is
     * faster and produces smaller payloads. This significantly improves session
     * read/write performance.
     * </p>
     * <p>
     * The ObjectMapper is configured with Spring Security's Jackson2 modules to
     * properly serialize/deserialize Spring Security types, including custom
     * authentication tokens.
     * </p>
     *
     * @return a configured {@link RedisSerializer} using Jackson JSON serialization
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Enable default typing for polymorphic deserialization
        objectMapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        // Configure visibility to use fields for serialization
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        // Register Spring Security's Jackson modules for proper Security type handling
        objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

        // Register mixin for custom GeorchestraUserNamePasswordAuthenticationToken
        objectMapper.addMixIn(GeorchestraUserNamePasswordAuthenticationToken.class,
                GeorchestraUserNamePasswordAuthenticationTokenMixin.class);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    /**
     * Configures a health indicator for monitoring the Redis connection status.
     * <p>
     * This health indicator integrates with Spring Boot Actuator, allowing
     * real-time monitoring of the Redis connection through health endpoints.
     * </p>
     *
     * @param connectionFactory the reactive Redis connection factory
     * @return a configured {@link RedisReactiveHealthIndicator} instance
     */
    @Bean
    public RedisReactiveHealthIndicator redisHealthIndicator(ReactiveRedisConnectionFactory connectionFactory) {
        return new RedisReactiveHealthIndicator(connectionFactory);
    }
}
