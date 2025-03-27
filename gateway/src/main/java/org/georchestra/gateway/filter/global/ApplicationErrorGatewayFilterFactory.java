/*
 * Copyright (C) 2024 by the geOrchestra PSC
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
package org.georchestra.gateway.filter.global;

import java.util.function.Supplier;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that enables custom error pages when a proxied application
 * responds with an error status, applicable only for idempotent HTTP methods
 * (e.g., GET, HEAD, OPTIONS).
 * <p>
 * This {@link GatewayFilterFactory} provides a {@link GatewayFilter} that
 * throws a {@link ResponseStatusException} with the response status code if the
 * proxied service returns a {@code 400...} or {@code 500...} status. The
 * gateway will then apply its custom error handling.
 * </p>
 * <p>
 * <b>Usage:</b> To enable this filter globally, add the following to
 * {@code application.yaml}:
 * </p>
 *
 * <pre>
 * <code>
 * spring:
 *  cloud:
 *    gateway:
 *      default-filters:
 *        - ApplicationError
 * </code>
 * </pre>
 *
 * <p>
 * To enable it only for specific routes, configure the filter in
 * {@code routes.yaml}:
 * </p>
 *
 * <pre>
 * <code>
 *        filters:
 *       - name: ApplicationError
 * </code>
 * </pre>
 */
@Slf4j
public class ApplicationErrorGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    public ApplicationErrorGatewayFilterFactory() {
        super(Object.class);
    }

    @Override
    public GatewayFilter apply(final Object config) {
        return new ServiceErrorGatewayFilter();
    }

    /**
     * Gateway filter that intercepts error responses and triggers a
     * {@link ResponseStatusException} to allow the gateway to render a custom error
     * page.
     */
    private class ServiceErrorGatewayFilter implements GatewayFilter, Ordered {

        /**
         * Returns the order of this filter to ensure it runs at the highest precedence.
         * <p>
         * This is necessary so that
         * {@link ApplicationErrorConveyorHttpResponse#beforeCommit(Supplier)} gets
         * executed properly.
         * </p>
         *
         * @return {@link Ordered#HIGHEST_PRECEDENCE}
         */
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        /**
         * Applies the filter logic by wrapping the response in a decorator that checks
         * for error statuses.
         * <p>
         * If the request method is idempotent and the request accepts
         * {@code text/html}, the response is wrapped in a
         * {@link ApplicationErrorConveyorHttpResponse}, which throws a
         * {@link ResponseStatusException} if an error status code is encountered.
         * </p>
         *
         * @param exchange the current server exchange
         * @param chain    the gateway filter chain
         * @return a {@link Mono} that completes when the filter chain is executed
         */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (canFilter(exchange.getRequest())) {
                exchange = decorate(exchange);
            }
            return chain.filter(exchange);
        }
    }

    /**
     * Wraps the server exchange's response with an
     * {@link ApplicationErrorConveyorHttpResponse} to intercept error statuses.
     *
     * @param exchange the server exchange to decorate
     * @return a new {@link ServerWebExchange} instance with the decorated response
     */
    ServerWebExchange decorate(ServerWebExchange exchange) {
        var response = new ApplicationErrorConveyorHttpResponse(exchange.getResponse());
        exchange = exchange.mutate().response(response).build();
        return exchange;
    }

    /**
     * Determines if the request should be filtered based on method idempotency and
     * accepted content types.
     *
     * @param request the incoming HTTP request
     * @return {@code true} if the request should be filtered, {@code false}
     *         otherwise
     */
    boolean canFilter(ServerHttpRequest request) {
        return methodIsIdempotent(request.getMethod()) && acceptsHtml(request);
    }

    /**
     * Checks if the request method is idempotent (i.e., does not modify state).
     *
     * @param method the HTTP method to check
     * @return {@code true} if the method is idempotent, {@code false} otherwise
     */
    boolean methodIsIdempotent(HttpMethod method) {
        return switch (method.name()) {
        case "GET", "HEAD", "OPTIONS", "TRACE" -> true;
        default -> false;
        };
    }

    /**
     * Determines whether the request accepts HTML responses.
     *
     * @param request the incoming HTTP request
     * @return {@code true} if the request accepts {@code text/html}, {@code false}
     *         otherwise
     */
    boolean acceptsHtml(ServerHttpRequest request) {
        return request.getHeaders().getAccept().stream().anyMatch(MediaType.TEXT_HTML::isCompatibleWith);
    }

    /**
     * A response decorator that throws a {@link ResponseStatusException} in
     * {@link #beforeCommit} if the status code is an error, allowing the gateway to
     * handle the error with a custom response page.
     */
    private static class ApplicationErrorConveyorHttpResponse extends ServerHttpResponseDecorator {

        public ApplicationErrorConveyorHttpResponse(ServerHttpResponse delegate) {
            super(delegate);
        }

        @Override
        public void beforeCommit(Supplier<? extends Mono<Void>> action) {
            Mono<Void> checkStatus = Mono.fromRunnable(this::checkStatusCode);
            Mono<Void> checkedAction = checkStatus.then(Mono.fromRunnable(action::get));
            super.beforeCommit(() -> checkedAction);
        }

        /**
         * Throws a {@link ResponseStatusException} if the response status is in the 4xx
         * or 5xx range, allowing the gateway to apply custom error handling.
         */
        private void checkStatusCode() {
            HttpStatusCode statusCode = getStatusCode();
            log.debug("native status code: {}", statusCode);
            if (statusCode.is4xxClientError() || statusCode.is5xxServerError()) {
                log.debug("Conveying {} response status", statusCode);
                throw new ResponseStatusException(statusCode);
            }
        }
    }
}
