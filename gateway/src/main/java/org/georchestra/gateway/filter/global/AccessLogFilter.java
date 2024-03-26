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

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ServerWebExchange;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j(topic = "org.georchestra.gateway.accesslog")
public class AccessLogFilter implements GlobalFilter {

    private final @NonNull AccessLogFilterConfig config;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (config.matches(exchange.getRequest().getURI())) {
            exchange.getResponse().beforeCommit(() -> {
                return log(exchange);
            });
        }

        return chain.filter(exchange);
    }

    private static final AnonymousAuthenticationToken ANNON = new AnonymousAuthenticationToken("anonymous", "anonymous",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

    /**
     * @param exchange
     */
    private Mono<Void> log(ServerWebExchange exchange) {
        if (!log.isInfoEnabled())
            return Mono.empty();

        return exchange.getPrincipal().switchIfEmpty(Mono.just(ANNON)).doOnNext(p -> {
            doLog(p, exchange);
        }).then();
    }

    private void doLog(Principal principal, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        URI uri = request.getURI();

        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        URI routeUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);

        String requestId = request.getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER);

        InetSocketAddress addr = request.getRemoteAddress();
        String remoteAddress = addr == null ? "unknown" : addr.toString();

        MDC.put("route-id", route.getId());
        MDC.put("route-uri", String.valueOf(routeUri));
        MDC.put(RequestIdGlobalFilter.REQUEST_ID_HEADER, requestId);
        MDC.put("remoteAddress", remoteAddress);
        MDC.put("auth-user", principal.getName());
        if (principal instanceof Authentication && principal != ANNON) {
            String roles = ((Authentication) principal).getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(", "));
            MDC.put("auth-roles", roles);
        }

        log.info("{} {} {} ", request.getMethodValue(), response.getRawStatusCode(), uri);
    }
}
