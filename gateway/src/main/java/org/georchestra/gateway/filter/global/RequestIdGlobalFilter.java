/*
 * Copyright (C) 2022 by the geOrchestra PSC
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
package org.georchestra.gateway.filter.global;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Makes sure both the request and response have the same
 * {@literal X-Request-ID} header.
 * <p>
 * A new value is created for the header if not provided by the client.
 */
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    public @Override int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Makes sure both the request and response have the same
     * {@literal X-Request-ID} header.
     * <p>
     * A new value is created for the header if not provided by the client.
     */
    public @Override Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        final String requestId;
        final ServerHttpRequest request;
        String providedRequestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (null == providedRequestId) {
            requestId = RandomStringUtils.randomNumeric(16);
            request = exchange.getRequest().mutate().header(REQUEST_ID_HEADER, requestId).build();
            exchange = exchange.mutate().request(request).build();
        } else {
            requestId = providedRequestId;
            request = exchange.getRequest();
        }

        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

}