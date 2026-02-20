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

import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Mono;

/**
 * Redirects the user to a URL stored in the session by
 * {@link OAuth2RedirectQueryParamWebFilter} right after a successful OAuth2
 * authentication. If no redirect was stored, it delegates to the provided
 * success handler (by default the standard filter chain continuation).
 */
public class OAuth2RedirectAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final String redirectSessionAttribute;

    private final ServerAuthenticationSuccessHandler delegate;

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    public OAuth2RedirectAuthenticationSuccessHandler(String redirectSessionAttribute) {
        this(redirectSessionAttribute, new RedirectServerAuthenticationSuccessHandler());
    }

    public OAuth2RedirectAuthenticationSuccessHandler(String redirectSessionAttribute,
            ServerAuthenticationSuccessHandler delegate) {
        this.redirectSessionAttribute = redirectSessionAttribute;
        this.delegate = delegate;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        return webFilterExchange.getExchange().getSession().flatMap(session -> {
            Object value = session.getAttributes().remove(redirectSessionAttribute);
            if (value instanceof String redirect && StringUtils.hasText(redirect)) {
                return redirectStrategy.sendRedirect(webFilterExchange.getExchange(), URI.create(redirect));
            }
            return delegate.onAuthenticationSuccess(webFilterExchange, authentication);
        });
    }
}