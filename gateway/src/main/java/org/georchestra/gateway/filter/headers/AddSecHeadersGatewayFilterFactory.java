/*
 * Copyright (C) 2021 by the geOrchestra PSC
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
package org.georchestra.gateway.filter.headers;

import java.util.Arrays;
import java.util.List;

import org.georchestra.gateway.filter.global.ResolveTargetGlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

public class AddSecHeadersGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

    public static final String DISABLE_SECURITY_HEADERS = "%s.DISABLE_SECURITY_HEADERS"
            .formatted(AddSecHeadersGatewayFilterFactory.class.getName());

    private final List<HeaderContributor> providers;

    public AddSecHeadersGatewayFilterFactory(List<HeaderContributor> providers) {
        super(NameConfig.class);
        this.providers = providers;
    }

    public @Override List<String> shortcutFieldOrder() {
        return Arrays.asList(NAME_KEY);
    }

    public @Override GatewayFilter apply(NameConfig config) {
        return new AddSecHeadersGatewayFilter(providers);
    }

    @RequiredArgsConstructor
    private static class AddSecHeadersGatewayFilter implements GatewayFilter, Ordered {

        private final @NonNull List<HeaderContributor> providers;

        public @Override Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (exchange.getAttribute(DISABLE_SECURITY_HEADERS) == null) {
                ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                providers.stream()//
                        .map(provider -> provider.prepare(exchange))//
                        .forEach(requestBuilder::headers);

                ServerHttpRequest request = requestBuilder.build();
                ServerWebExchange updatedExchange = exchange.mutate().request(request).build();
                return chain.filter(updatedExchange);
            }
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return ResolveTargetGlobalFilter.ORDER + 1;
        }
    }

}
