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
package org.georchestra.gateway.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Captures a {@code redirect} query parameter passed directly to the
 * {@code /oauth2/authorization/*} endpoint and stores it as a saved request in
 * the session when it is allowed by {@code loginRedirectAllowList}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuth2RedirectParamWebFilter implements WebFilter {

    private final List<String> loginRedirectAllowList;

    public OAuth2RedirectParamWebFilter(
            @Value("${georchestra.gateway.loginRedirectAllowList:}") String[] loginRedirectAllowList) {
        this.loginRedirectAllowList = Arrays.asList(loginRedirectAllowList);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/oauth2/authorization/")) {
            return chain.filter(exchange);
        }

        List<String> redirectParams = exchange.getRequest().getQueryParams().get("redirect");
        if (redirectParams == null || redirectParams.isEmpty()) {
            return chain.filter(exchange);
        }

        String redirect = redirectParams.get(0);
        if (!isSafeRedirect(redirect)) {
            return chain.filter(exchange);
        }

        return exchange.getSession()
                .doOnNext(session -> session.getAttributes().put("SPRING_SECURITY_SAVED_REQUEST", redirect))
                .then(chain.filter(exchange));
    }

    private boolean isSafeRedirect(String url) {
        return loginRedirectAllowList.stream().anyMatch(url::startsWith);
    }
}