/*
 * Copyright (C) 2023 by the geOrchestra PSC
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
package org.georchestra.gateway.accounts.admin;

import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

import org.georchestra.gateway.security.GeorchestraUserCustomizerExtension;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * {@link GeorchestraUserCustomizerExtension} that
 * {@link AccountManager#getOrCreate creates an account} when authenticated
 * through request headers (trusted proxy feature) or through OAuth2.
 */
@RequiredArgsConstructor
public class CreateAccountUserCustomizer implements GeorchestraUserCustomizerExtension, Ordered {

    // private @Value("${georchestra.gateway.security.providerWithEmailAsUnique}")
    // List<String> providerWithEmailAsUnique;

    private final @NonNull AccountManager accounts;

    private final WeakHashMap<Authentication, GeorchestraUser> loggedInUsers = new WeakHashMap<>();

    /**
     * @return {@link Ordered#LOWEST_PRECEDENCE} so it runs after all other
     *         authentication customizations have been performed, such as setting
     *         additional roles from externalized configuration, etc.
     */
    public @Override int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * @return the stored version (either existing or created as result of calling
     *         this method) of the user account, if the {@code Authentication}
     *         object is either an {@link OAuth2AuthenticationToken} or
     *         {@link PreAuthenticatedAuthenticationToken}; {@code mappedUser}
     *         otherwise.
     */
    @Override
    public @NonNull GeorchestraUser apply(@NonNull Authentication auth, @NonNull GeorchestraUser mappedUser)
            throws DuplicatedEmailFoundException {
        final boolean isOauth2 = auth instanceof OAuth2AuthenticationToken;
        final boolean isPreAuth = auth instanceof PreAuthenticatedAuthenticationToken;
        if (isOauth2) {
            Objects.requireNonNull(mappedUser.getOAuth2Provider(), "GeorchestraUser.oAuth2Provider is null");
            Objects.requireNonNull(mappedUser.getOAuth2Uid(), "GeorchestraUser.oAuth2Uid is null");
        }
        if (isPreAuth) {
            Objects.requireNonNull(mappedUser.getUsername(), "GeorchestraUser.username is null");
        }
        if (isOauth2 || isPreAuth) {
            GeorchestraUser user = loggedInUsers.get(auth);
            if (user != null) {
                Optional<GeorchestraUser> ldapUser = accounts.find(mappedUser);
                if (ldapUser.isPresent()) {
                    user = ldapUser.get();
                }
            } else {
                user = accounts.getOrCreate(mappedUser);
                accounts.createUserOrgUniqueIdIfMissing(mappedUser);
            }
            user.setIsExternalAuth(true);
            loggedInUsers.put(auth, user);
            return user;
        }
        return mappedUser;
    }
}
