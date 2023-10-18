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

package org.georchestra.gateway.security.ldap.extended;

import java.util.Optional;

import org.georchestra.gateway.security.GeorchestraUserMapperExtension;
import org.georchestra.security.api.UsersApi;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * {@link GeorchestraUserMapperExtension} that maps LDAP-authenticated token to
 * {@link GeorchestraUser} by calling {@link UsersApi#findByUsername(String)},
 * with the authentication token's principal name as argument.
 * <p>
 * Resolves only {@link GeorchestraUserNamePasswordAuthenticationToken}, using
 * its {@link GeorchestraUserNamePasswordAuthenticationToken#getConfigName()
 * configName} to disambiguate amongst different configured LDAP databases.
 * 
 * @see DemultiplexingUsersApi
 */
@RequiredArgsConstructor
class GeorchestraLdapAuthenticatedUserMapper implements GeorchestraUserMapperExtension {

    private final @NonNull DemultiplexingUsersApi users;

    @Override
    public Optional<GeorchestraUser> resolve(Authentication authToken) {
        return Optional.ofNullable(authToken)//
                .filter(GeorchestraUserNamePasswordAuthenticationToken.class::isInstance)
                .map(GeorchestraUserNamePasswordAuthenticationToken.class::cast)//
                .filter(token -> token.getPrincipal() instanceof LdapUserDetails)//
                .flatMap(this::map);
    }

    Optional<GeorchestraUser> map(GeorchestraUserNamePasswordAuthenticationToken token) {
        final LdapUserDetails principal = (LdapUserDetails) token.getPrincipal();
        final String ldapConfigName = token.getConfigName();
        final String username = principal.getUsername();

        return users.findByUsername(ldapConfigName, username);
    }
}
