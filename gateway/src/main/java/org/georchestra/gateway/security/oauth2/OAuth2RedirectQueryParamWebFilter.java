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
package org.georchestra.gateway.security.oauth2;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

/**
 * Stores an optional {@code redirect} query parameter from calls to the
 * {@code /oauth2/authorization/*} endpoints so it can be used after a
 * successful OAuth2 login.
 * <p>
 * The parameter value is saved in the
 * {@link org.springframework.web.server.WebSession} under the
 * {@link #REDIRECT_SESSION_ATTRIBUTE} key, and the request is replayed without
 * the {@code redirect} parameter to avoid leaking it to the provider.
 */
public class OAuth2RedirectQueryParamWebFilter implements WebFilter, Ordered {

    public static final String REDIRECT_SESSION_ATTRIBUTE = "GEORCHESTRA_OAUTH2_REDIRECT";

    private static final String REDIRECT_PARAM = "redirect";

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public int getOrder() {
        // Run early in the chain so the redirect param is captured before
        // spring-security's OAuth2 filters handle the request.
        return SecurityWebFiltersOrder.FIRST.getOrder();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.contains("/oauth2/authorization/")) {
            return chain.filter(exchange);
        }

        final String redirectTarget = exchange.getRequest().getQueryParams().getFirst(REDIRECT_PARAM);
        if (!StringUtils.hasText(redirectTarget)) {
            return chain.filter(exchange);
        }

        URI sanitizedUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParam(REDIRECT_PARAM).build(true).toUri();

        return exchange.getSession().flatMap(session -> {
            session.getAttributes().put(REDIRECT_SESSION_ATTRIBUTE, redirectTarget);
            if (!sanitizedUri.equals(exchange.getRequest().getURI())) {
                // Drop the redirect parameter before spring-security takes over.
                return redirectStrategy.sendRedirect(exchange, sanitizedUri);
            }
            return chain.filter(exchange);
        });
    }
}
