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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security;

import java.net.URI;

import org.georchestra.gateway.model.GeorchestraOrganizations;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.exceptions.DuplicatedUsernameFoundException;
import org.georchestra.gateway.security.exceptions.PendingUserException;
import org.georchestra.gateway.security.ldap.extended.ExtendedGeorchestraUser;
import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.security.model.Organization;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebSession;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * A {@link GlobalFilter} that resolves the {@link GeorchestraUser} from the
 * request's {@link Authentication} so it can be {@link GeorchestraUsers#resolve
 * retrieved} during subsequent filter chain execution.
 * <p>
 * This filter ensures that each request has access to the authenticated user,
 * which can be used to populate security-related headers (e.g.,
 * {@literal sec-*} headers) when forwarding requests to backend services.
 * </p>
 * 
 * <p>
 * If the resolved {@link GeorchestraUser} is an instance of
 * {@link ExtendedGeorchestraUser}, this filter also extracts the associated
 * {@link Organization} and makes it available for downstream processing.
 * </p>
 * 
 * <p>
 * If a {@link DuplicatedEmailFoundException} occurs, the user is redirected to
 * the login page with an error flag, and the session is invalidated.
 * </p>
 *
 * @see GeorchestraUserMapper
 * @see GeorchestraUsers
 * @see GeorchestraOrganizations
 */
@RequiredArgsConstructor
@Slf4j(topic = "org.georchestra.gateway.security")
public class ResolveGeorchestraUserGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

    private final @NonNull GeorchestraUserMapper resolver;

    private final @NonNull ServerAuthenticationFailureHandler authenticationFailureHandler;

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    private static final String DUPLICATE_ACCOUNT_ERROR = "duplicate_account";

    /**
     * Defines the order in which this filter executes relative to other
     * {@link GlobalFilter} implementations.
     * <p>
     * It runs right after {@link RouteToRequestUrlFilter} to ensure that the
     * matched {@link Route} has been determined before resolving the user.
     * </p>
     *
     * @return filter execution order
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Resolves the authenticated {@link GeorchestraUser} from the request context
     * and stores it for downstream processing.
     * <p>
     * If an {@link ExtendedGeorchestraUser} is found, the associated
     * {@link Organization} is also extracted and stored.
     * </p>
     * <p>
     * If a {@link DuplicatedEmailFoundException} is encountered, the user is
     * redirected to the login page with an error message, and the session is
     * invalidated.
     * </p>
     *
     * @param exchange the current server exchange
     * @param chain    the filter chain
     * @return a {@link Mono} that completes when processing is finished
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .doOnNext(principal -> log.debug("Resolving user from {}", principal.getClass().getName()))
                .filter(Authentication.class::isInstance).map(Authentication.class::cast).map(resolver::resolve)
                .map(user -> storeUserAndOrganization(exchange, user.orElse(null))).defaultIfEmpty(exchange)
                .flatMap(chain::filter)
                .onErrorResume(DuplicatedEmailFoundException.class, error -> handleDuplicateEmailError(exchange))
                .onErrorResume(PendingUserException.class, error -> handlePendingUserError(exchange, error))
                .onErrorResume(DuplicatedUsernameFoundException.class,
                        error -> handleDuplicateUsernameError(exchange, error));
    }

    /**
     * Stores the resolved {@link GeorchestraUser} and its associated
     * {@link Organization} (if applicable) in the exchange attributes.
     *
     * @param exchange the current server exchange
     * @param user     the resolved user, or {@code null} if none found
     * @return the updated server exchange
     */
    private ServerWebExchange storeUserAndOrganization(ServerWebExchange exchange, GeorchestraUser user) {
        GeorchestraUsers.store(exchange, user);

        if (user instanceof ExtendedGeorchestraUser extendedUser) {
            Organization org = extendedUser.getOrg();
            if (org != null) {
                GeorchestraOrganizations.store(exchange, org);
            }
        }
        return exchange;
    }

    /**
     * Handles a {@link DuplicatedEmailFoundException} by redirecting the user to
     * the login page with an error message and invalidating the session.
     *
     * @param exchange the current server exchange
     * @return a {@link Mono} signaling the redirect operation
     */
    private Mono<Void> handleDuplicateEmailError(ServerWebExchange exchange) {
        return redirectStrategy.sendRedirect(exchange, URI.create("/login?error=" + DUPLICATE_ACCOUNT_ERROR))
                .then(exchange.getSession().flatMap(WebSession::invalidate));
    }

    private Mono<Void> handlePendingUserError(ServerWebExchange exchange, PendingUserException exception) {
        WebFilterChain noopChain = webExchange -> Mono.empty();
        WebFilterExchange webFilterExchange = new WebFilterExchange(exchange, noopChain);
        return authenticationFailureHandler.onAuthenticationFailure(webFilterExchange, exception)
                .then(exchange.getSession().flatMap(WebSession::invalidate));
    }

    private Mono<Void> handleDuplicateUsernameError(ServerWebExchange exchange,
            DuplicatedUsernameFoundException exception) {
        WebFilterChain noopChain = webExchange -> Mono.empty();
        WebFilterExchange webFilterExchange = new WebFilterExchange(exchange, noopChain);
        return authenticationFailureHandler.onAuthenticationFailure(webFilterExchange, exception)
                .then(exchange.getSession().flatMap(WebSession::invalidate));
    }
}
