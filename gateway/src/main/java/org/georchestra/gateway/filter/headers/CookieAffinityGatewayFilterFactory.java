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
package org.georchestra.gateway.filter.headers;

import org.georchestra.gateway.filter.global.ResolveTargetGlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import reactor.core.publisher.Mono;

/**
 * {@link AbstractGatewayFilterFactory} that modifies the path of a specific
 * HTTP response cookie, enabling cookie-based session affinity between
 * different backend services.
 * <p>
 * This filter allows rewriting the cookie's path from a specified {@code from}
 * path to a different {@code to} path. The original domain, security settings,
 * and expiration remain unchanged.
 * </p>
 * <p>
 * Sample usage in {@code application.yaml} to apply this filter on specific
 * routes:
 * </p>
 * 
 * <pre>
 * <code>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       - id: some-service
 *         uri: http://backend-service
 *         filters:
 *         - name: CookieAffinity
 *           args:
 *             name: JSESSIONID
 *             from: /serviceA
 *             to: /serviceB
 * </code>
 * </pre>
 */
public class CookieAffinityGatewayFilterFactory
        extends AbstractGatewayFilterFactory<CookieAffinityGatewayFilterFactory.CookieAffinity> {

    /**
     * Creates a new instance of the cookie affinity filter factory.
     */
    public CookieAffinityGatewayFilterFactory() {
        super(CookieAffinityGatewayFilterFactory.CookieAffinity.class);
    }

    /**
     * Creates a {@link GatewayFilter} that applies the cookie path transformation.
     *
     * @param config the filter configuration
     * @return the configured {@link GatewayFilter}
     */
    @Override
    public GatewayFilter apply(final CookieAffinityGatewayFilterFactory.CookieAffinity config) {
        return new CookieAffinityGatewayFilter(config);
    }

    /**
     * Configuration class for {@link CookieAffinityGatewayFilterFactory}. Defines
     * the cookie name and path mapping.
     */
    @Validated
    public static class CookieAffinity {

        /**
         * The name of the cookie to modify.
         */
        private @NotEmpty @Getter @Setter String name;

        /**
         * The original path of the cookie.
         */
        private @NotEmpty @Getter @Setter String from;

        /**
         * The new path to which the cookie should be rewritten.
         */
        private @NotEmpty @Getter @Setter String to;
    }

    /**
     * {@link GatewayFilter} implementation that modifies the path of a specific
     * cookie in the response headers.
     */
    @RequiredArgsConstructor
    private static class CookieAffinityGatewayFilter implements GatewayFilter, Ordered {

        private final CookieAffinity config;

        /**
         * Processes the response to update the path of the specified cookie.
         *
         * @param exchange the current server exchange
         * @param chain    the gateway filter chain
         * @return a {@link Mono} that proceeds with the filter chain execution
         */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                exchange.getResponse().getHeaders().getValuesAsList("Set-Cookie").stream()
                        .flatMap(c -> java.net.HttpCookie.parse(c).stream())
                        .filter(cookie -> cookie.getName().equals(config.getName())
                                && cookie.getPath().equals(config.getFrom()))
                        .forEach(cookie -> {
                            ResponseCookie responseCookie = ResponseCookie.from(cookie.getName(), cookie.getValue())
                                    .domain(cookie.getDomain()).httpOnly(cookie.isHttpOnly()).secure(cookie.getSecure())
                                    .maxAge(cookie.getMaxAge()).path(config.getTo()).build();
                            exchange.getResponse().addCookie(responseCookie);
                        });
            }));
        }

        /**
         * Specifies the execution order of this filter to run immediately after
         * {@link ResolveTargetGlobalFilter}.
         *
         * @return the execution order of this filter
         */
        @Override
        public int getOrder() {
            return ResolveTargetGlobalFilter.ORDER + 1;
        }
    }
}
