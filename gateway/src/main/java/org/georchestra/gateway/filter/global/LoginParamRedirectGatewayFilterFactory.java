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

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.TRACE;

import java.util.List;
import java.util.Set;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * {@link GatewayFilterFactory} that redirects unauthenticated requests to
 * {@literal /login} when the query string contains a {@literal login}
 * parameter.
 * <p>
 * This filter applies only to idempotent HTTP methods (GET, HEAD, OPTIONS,
 * TRACE) and ensures that authenticated users proceed without redirection.
 * </p>
 * <p>
 * <b>Usage:</b> Add the following to {@code application.yaml} to enable this
 * filter for specific routes:
 * </p>
 *
 * <pre>
 * <code>
 * spring:
 *   cloud:
 *    gateway:
 *      routes:
 *      - id: routeid
 *        uri: ...
 *        filters:
 *        - LoginParamRedirect
 * </code>
 * </pre>
 */
@Slf4j
public class LoginParamRedirectGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Set<HttpMethod> REDIRECT_METHODS = Set.of(GET, HEAD, OPTIONS, TRACE);
    private RedirectServerAuthenticationEntryPoint redirectServerAuthenticationEntryPoint;

    @Override
    public LoginParamRedirectGatewayFilter apply(Object config) {
        if (redirectServerAuthenticationEntryPoint == null) {
            throw new IllegalStateException(
                    "redirectServerAuthenticationEntryPoint must be set before using this filter");
        }
        return new LoginParamRedirectGatewayFilter(redirectServerAuthenticationEntryPoint);
    }

    public LoginParamRedirectGatewayFilterFactory setRedirectServerAuthenticationEntryPoint(
            RedirectServerAuthenticationEntryPoint redirectServerAuthenticationEntryPoint) {
        this.redirectServerAuthenticationEntryPoint = redirectServerAuthenticationEntryPoint;
        return this;
    }

    /**
     * Gateway filter that applies redirection logic when an unauthenticated request
     * contains a {@code login} query parameter.
     */
    @RequiredArgsConstructor
    public static class LoginParamRedirectGatewayFilter implements GatewayFilter {

        private static final Authentication UNAUTHENTICATED = new AnonymousAuthenticationToken("nobody", "nobody",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        private final @NonNull RedirectServerAuthenticationEntryPoint redirectServerAuthenticationEntryPoint;

        /**
         * Intercepts requests and redirects to {@code /login} if:
         * <ul>
         * <li>The HTTP method is idempotent (GET, HEAD, OPTIONS, TRACE)</li>
         * <li>The request contains a {@code login} query parameter</li>
         * <li>The user is not authenticated</li>
         * </ul>
         * If the user is already authenticated, the request proceeds without
         * redirection.
         *
         * @param exchange the current server exchange
         * @param chain    the gateway filter chain
         * @return a {@link Mono} that completes when the filter chain is executed
         */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            HttpMethod method = exchange.getRequest().getMethod();
            if (REDIRECT_METHODS.contains(method) && containsLoginQueryParam(exchange)) {
                log.info("Applying ?login query param redirect filter for {} {}", method,
                        exchange.getRequest().getURI());
                return redirectToLoginIfNotAuthenticated(exchange, chain);
            }
            return chain.filter(exchange);
        }

        /**
         * Redirects the user to {@code /login} if they are not authenticated.
         *
         * @param exchange the server exchange
         * @param chain    the gateway filter chain
         * @return a {@link Mono} that either redirects to {@code /login} or proceeds
         *         with the request if already authenticated
         */
        private Mono<Void> redirectToLoginIfNotAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain) {
            return getAuthentication()//
                    .filter(Authentication::isAuthenticated)//
                    .switchIfEmpty(Mono.just(UNAUTHENTICATED))//
                    .flatMap(authentication -> {
                        if (authentication instanceof AnonymousAuthenticationToken) {
                            log.info("Redirecting to /login: {}", exchange.getRequest().getURI());
                            // AuthenticationException isn't used in RedirectServerAuthenticationEntryPoint
                            // implementation,
                            // but is required by the method signature
                            return redirectServerAuthenticationEntryPoint.commence(exchange,
                                    new AuthenticationException("Not applicable") {
                                    });
                        }
                        log.info("Already authenticated ({}), proceeding without redirection to /login",
                                authentication.getName());
                        return chain.filter(exchange);
                    });
        }

        /**
         * Retrieves the current authentication context.
         *
         * @return a {@link Mono} containing the {@link Authentication} object, or an
         *         empty Mono if unavailable
         */
        @VisibleForTesting
        public Mono<Authentication> getAuthentication() {
            return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication);
        }

        /**
         * Checks if the request contains a {@code login} query parameter.
         *
         * @param exchange the server exchange
         * @return {@code true} if the query parameter is present, {@code false}
         *         otherwise
         */
        private boolean containsLoginQueryParam(ServerWebExchange exchange) {
            ServerHttpRequest request = exchange.getRequest();
            return request.getQueryParams().containsKey("login");
        }
    }
}
