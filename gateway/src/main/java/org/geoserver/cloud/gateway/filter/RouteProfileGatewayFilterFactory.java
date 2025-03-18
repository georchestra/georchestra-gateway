/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.gateway.filter;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

/**
 * A Gateway filter factory that conditionally enables or disables routes based
 * on the presence or absence of a specified Spring profile.
 * <p>
 * This filter is useful for dynamically controlling route availability
 * depending on the application's active profiles. If the configured profile is
 * active, the request is allowed to proceed; otherwise, the request is rejected
 * with the specified HTTP status code.
 * <p>
 * Profiles can be negated using the {@code !} prefix. If a profile is prefixed
 * with {@code !}, the route will be disabled if the profile is active.
 */
public class RouteProfileGatewayFilterFactory
        extends AbstractGatewayFilterFactory<RouteProfileGatewayFilterFactory.Config> {

    private static final List<String> SHORTCUT_FIELD_ORDER = Collections
            .unmodifiableList(Arrays.asList(Config.PROFILE_KEY, Config.HTTPSTATUS_KEY));

    @Autowired
    private Environment environment;

    public RouteProfileGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return SHORTCUT_FIELD_ORDER;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new RouteProfileGatewayFilter(environment, config);
    }

    /**
     * A filter that conditionally allows requests based on the presence of a
     * specified Spring profile.
     * <p>
     * If the required profile is active, the request proceeds. If the profile is
     * negated (e.g., {@code !profileName}), the request is blocked if the profile
     * is active.
     */
    @RequiredArgsConstructor
    private static class RouteProfileGatewayFilter implements GatewayFilter {

        private final @NonNull Environment environment;
        private final @NonNull Config config;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            final List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            String profile = config.getProfile();
            if (StringUtils.hasText(profile)) {
                final boolean exclude = profile.startsWith("!");
                profile = exclude ? profile.substring(1) : profile;

                boolean profileMatch = activeProfiles.contains(profile);
                boolean proceed = (profileMatch && !exclude) || (!profileMatch && exclude);
                if (proceed) {
                    // continue...
                    return chain.filter(exchange);
                }
            }

            int status = config.getStatusCode();
            exchange.getResponse().setRawStatusCode(status);
            return exchange.getResponse().setComplete();
        }

        @Override
        public String toString() {
            return filterToStringCreator(this).append(Config.PROFILE_KEY, config.getProfile())
                    .append(Config.HTTPSTATUS_KEY, config.getStatusCode()).toString();
        }
    }

    /**
     * Configuration class for {@link RouteProfileGatewayFilterFactory}.
     * <p>
     * Defines the profile condition and HTTP status code to return if the condition
     * is not met.
     */
    @Data
    @Accessors(chain = true)
    @Validated
    public static class Config {

        /**
         * The profile name that must be active for the request to proceed.
         * <p>
         * If prefixed with {@code !}, the request is blocked if the profile is active.
         */
        public static final String PROFILE_KEY = "profile";

        /**
         * The HTTP status code to return when the request is blocked due to profile
         * conditions.
         * <p>
         * Defaults to {@link HttpStatus#NOT_FOUND} (404).
         */
        public static final String HTTPSTATUS_KEY = "statusCode";

        @NotEmpty
        private String profile;

        private int statusCode = HttpStatus.NOT_FOUND.value();
    }
}
