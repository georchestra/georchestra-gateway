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

import java.util.List;
import java.util.Optional;

import org.georchestra.ds.users.DuplicatedEmailException;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Aids {@link ResolveGeorchestraUserGlobalFilter} in resolving the
 * {@link GeorchestraUser} from the current request's {@link Authentication}
 * token.
 * <p>
 * Relies on the provided {@link GeorchestraUserMapperExtension}s to map an
 * {@link Authentication} to a {@link GeorchestraUsers}, and on
 * {@link GeorchestraUserCustomizerExtension} to apply additional user
 * customizations once resolved from {@link Authentication} to
 * {@link GeorchestraUser}.
 * <p>
 * {@literal GeorchestraUserMapperExtension} beans specialize in mapping auth
 * tokens for specific authentication sources (e.g. LDAP, OAuth2, OAuth2+OpenID,
 * etc).
 * <p>
 * {@literal GeorchestraUserCustomizerExtension} beans specialize in applying
 * any additional customization to the {@link GeorchestraUser} object after it
 * has been extracted from the {@link Authentication} created by the actual
 * authentication provider.
 * 
 * @see GeorchestraUserMapperExtension
 * @see GeorchestraUserCustomizerExtension
 */
@RequiredArgsConstructor
public class GeorchestraUserMapper {

    /**
     * {@link Ordered ordered} list of user mapper extensions.
     */
    private final @NonNull List<GeorchestraUserMapperExtension> resolvers;

    private final @NonNull List<GeorchestraUserCustomizerExtension> customizers;

    GeorchestraUserMapper() {
        this(List.of(), List.of());
    }

    GeorchestraUserMapper(List<GeorchestraUserMapperExtension> resolvers) {
        this(resolvers, List.of());
    }

    /**
     * @return the first non-empty user from
     *         {@link GeorchestraUserMapperExtension#resolve asking} the extension
     *         point implementations to resolve the user from the token, or
     *         {@link Optional#empty()} if no extension point implementation can
     *         handle the auth token.
     */
    public Optional<GeorchestraUser> resolve(@NonNull Authentication authToken) throws DuplicatedEmailFoundException {
        return resolvers.stream()//
                .map(resolver -> resolver.resolve(authToken))//
                .filter(Optional::isPresent)//
                .map(Optional::orElseThrow)//
                .map(mapped -> customize(authToken, mapped)).findFirst();
    }

    private GeorchestraUser customize(@NonNull Authentication authToken, GeorchestraUser mapped)
            throws DuplicatedEmailFoundException {
        GeorchestraUser customized = mapped;
        for (GeorchestraUserCustomizerExtension customizer : customizers) {
            customized = customizer.apply(authToken, customized);
        }
        return customized;
    }
}