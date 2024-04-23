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
package org.georchestra.gateway.security;

import org.apache.commons.lang3.tuple.Pair;
import org.georchestra.gateway.model.GeorchestraOrganizations;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.ldap.extended.ExtendedGeorchestraUser;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.georchestra.security.model.Organization;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

/**
 * A {@link GlobalFilter} that resolves the {@link GeorchestraUser} from the
 * request's {@link Authentication} so it can be {@link GeorchestraUsers#resolve
 * retrieved} down the road during a server web exchange filter chain execution.
 * <p>
 * The resolved per-request {@link GeorchestraUser user} object can then, for
 * example, be used to append the necessary {@literal sec-*} headers that relate
 * to user information to proxied http requests.
 * 
 * @see GeorchestraUserMapper
 */
@RequiredArgsConstructor
@Slf4j(topic = "org.georchestra.gateway.security")
public class ResolveGeorchestraUserGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

    private final @NonNull GeorchestraUserMapper resolver;

    private ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    private static String DUPLICATE_ACCOUNT = "duplicate_account";

    /**
     * @return a lower precedence than {@link RouteToRequestUrlFilter}'s, in order
     *         to make sure the matched {@link Route} has been set as a
     *         {@link ServerWebExchange#getAttributes attribute} when
     *         {@link #filter} is called.
     */
    public @Override int getOrder() {
        return ORDER;
    }

    /**
     * Resolves the matched {@link Route} and its corresponding
     * {@link GeorchestraTargetConfig}, if possible, and proceeds with the filter
     * chain.
     */
    public @Override Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()//
                .doOnNext(p -> log.debug("resolving user from {}", p.getClass().getName()))//
                .filter(Authentication.class::isInstance)//
                .map(Authentication.class::cast)//
                .map(resolver::resolve)//
                .map(user -> {
                    GeorchestraUser usr = user.orElse(null);
                    GeorchestraUsers.store(exchange, usr);
                    if (usr != null && usr instanceof ExtendedGeorchestraUser) {
                        ExtendedGeorchestraUser eu = (ExtendedGeorchestraUser) usr;
                        Organization org = eu.getOrg();
                        GeorchestraOrganizations.store(exchange, org);
                    }
                    return exchange;
                })//
                .defaultIfEmpty(exchange)//
                .flatMap(chain::filter)//
                .onErrorResume(DuplicatedEmailFoundException.class,
                        exp -> this.redirectStrategy
                                .sendRedirect(exchange, URI.create("/login?error=" + DUPLICATE_ACCOUNT))
                                .then(exchange.getSession().flatMap(WebSession::invalidate)));
    }

}