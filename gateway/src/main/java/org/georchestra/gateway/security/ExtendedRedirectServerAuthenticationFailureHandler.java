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

import java.net.URI;

import org.georchestra.gateway.security.exceptions.DuplicatedUsernameFoundException;
import org.georchestra.gateway.security.exceptions.PendingUserException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.util.Assert;

import reactor.core.publisher.Mono;

/**
 * Extended version of {@link RedirectServerAuthenticationFailureHandler} to
 * provide more granular authentication failure handling.
 * <p>
 * This handler inspects the cause of authentication failure and redirects to
 * different locations based on the type of exception encountered.
 * </p>
 * <p>
 * Specifically, it:
 * <ul>
 * <li>Redirects to {@code login?error=invalid_credentials} for bad
 * credentials.</li>
 * <li>Redirects to {@code login?error=expired_password} for expired
 * passwords.</li>
 * <li>Defaults to {@code login?error} for other authentication failures.</li>
 * </ul>
 * </p>
 */
public class ExtendedRedirectServerAuthenticationFailureHandler extends RedirectServerAuthenticationFailureHandler {

    private URI location;

    private static final String INVALID_CREDENTIALS = "invalid_credentials";
    private static final String EXPIRED_PASSWORD = "expired_password";
    private static final String PENDING_USER_ERROR = "pending_user";
    private static final String DUPLICATE_ACCOUNT_ERROR = "duplicate_account";
    private static final String EXPIRED_MESSAGE = "Your password has expired";
    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    /**
     * Constructs an {@code ExtendedRedirectServerAuthenticationFailureHandler} with
     * the default redirection location.
     *
     * @param location the base URI for authentication failure redirection
     */
    public ExtendedRedirectServerAuthenticationFailureHandler(String location) {
        super(location);
        Assert.notNull(location, "location cannot be null");
        this.location = URI.create(location);
    }

    /**
     * Handles authentication failures by determining the specific cause and
     * redirecting accordingly.
     *
     * @param webFilterExchange the current web exchange
     * @param exception         the exception that caused authentication failure
     * @return a {@link Mono} signaling completion after the redirect
     */
    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        this.location = URI.create("login?error");
        if (exception instanceof org.springframework.security.authentication.BadCredentialsException) {
            this.location = URI.create("login?error=" + INVALID_CREDENTIALS);
        } else if (exception instanceof org.springframework.security.authentication.LockedException
                && exception.getMessage().equals(EXPIRED_MESSAGE)) {
            this.location = URI.create("login?error=" + EXPIRED_PASSWORD);
        } else if (exception instanceof PendingUserException) {
            this.location = URI.create("login?error=" + PENDING_USER_ERROR);
        } else if (exception instanceof DuplicatedUsernameFoundException) {
            this.location = URI.create("login?error=" + DUPLICATE_ACCOUNT_ERROR);
        }
        return this.redirectStrategy.sendRedirect(webFilterExchange.getExchange(), this.location);
    }
}
