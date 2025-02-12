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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.georchestra.security.api.OrganizationsApi;
import org.georchestra.security.api.UsersApi;
import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.security.model.Organization;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Demultiplexer to call the appropriate {@link UsersApi} based on the
 * authentication's service name, as provided by
 * {@link GeorchestraUserNamePasswordAuthenticationToken#getConfigName()},
 * matching a configured LDAP database through the configuration properties
 * {@code georchestra.gateway.security.<serviceName>.*}.
 * <p>
 * Ensures {@link GeorchestraLdapAuthenticatedUserMapper} queries the same LDAP
 * database the authentication object was created from, avoiding the need to
 * disambiguate if two configured LDAP databases have accounts with the same
 * {@literal username}.
 */
@RequiredArgsConstructor
public class DemultiplexingUsersApi {

    private final @NonNull Map<String, UsersApi> usersByConfigName;
    private final @NonNull Map<String, OrganizationsApi> orgsByConfigName;

    public @VisibleForTesting Set<String> getTargetNames() {
        return new HashSet<>(usersByConfigName.keySet());
    }

    /**
     *
     * @param serviceName the configured LDAP service name, as from the
     *                    configuration properties
     *                    {@code georchestra.gateway.security.<serviceName>}
     * @param username    the user name to query the service's {@link UsersApi} with
     *
     * @return the {@link GeorchestraUser} returned by the service's
     *         {@link UsersApi}, or {@link Optional#empty() empty} if not found
     */
    public Optional<ExtendedGeorchestraUser> findByUsername(@NonNull String serviceName, @NonNull String username) {
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Objects.requireNonNull(usersApi, () -> "No UsersApi found for config named " + serviceName);
        Optional<GeorchestraUser> user = usersApi.findByUsername(username);

        return extend(serviceName, user);
    }

    public Optional<ExtendedGeorchestraUser> findByUsername(@NonNull String username) {
        // TODO: iterates over every possible geOrchestra LDAP being registered ?
        // I would expect to have generally only one geOrchestra (extended) LDAP
        // configured,
        // so the first extended LDAP should do.
        String serviceName = usersByConfigName.keySet().stream().findFirst().get();
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Optional<GeorchestraUser> user = usersApi.findByUsername(username);

        return extend(serviceName, user);
    }

    public Optional<ExtendedGeorchestraUser> findByEmail(@NonNull String serviceName, @NonNull String email) {
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Objects.requireNonNull(usersApi, () -> "No UsersApi found for config named " + serviceName);
        Optional<GeorchestraUser> user = usersApi.findByEmail(email);

        return extend(serviceName, user);
    }

    public Optional<ExtendedGeorchestraUser> findByEmail(@NonNull String email) {
        String serviceName = usersByConfigName.keySet().stream().findFirst().get();
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Optional<GeorchestraUser> user = usersApi.findByEmail(email);

        return extend(serviceName, user);
    }

    public Optional<ExtendedGeorchestraUser> findByOAuth2Uid(@NonNull String oauth2Provider,
            @NonNull String oauth2Uid) {
        String serviceName = usersByConfigName.keySet().stream().findFirst().get();
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Objects.requireNonNull(usersApi, () -> "No UsersApi found for config named " + serviceName);
        Optional<GeorchestraUser> user = usersApi.findByOAuth2Uid(oauth2Provider, oauth2Uid);

        return extend(serviceName, user);
    }

    private Optional<ExtendedGeorchestraUser> extend(String serviceName, Optional<GeorchestraUser> user) {
        OrganizationsApi orgsApi = orgsByConfigName.get(serviceName);
        Objects.requireNonNull(orgsApi, () -> "No OrganizationsApi found for config named " + serviceName);

        Organization org = user.map(GeorchestraUser::getOrganization).flatMap(orgsApi::findByShortName).orElse(null);

        return user.map(ExtendedGeorchestraUser::new).map(u -> u.setOrg(org));
    }

}
