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
package org.georchestra.gateway.security.ldap.extended;

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
 * A service responsible for selecting the appropriate {@link UsersApi} based on
 * the authentication's originating LDAP configuration, ensuring user lookups
 * occur in the correct LDAP database.
 * <p>
 * This class provides methods to:
 * <ul>
 * <li>Retrieve user details based on username, ensuring queries are made to the
 * LDAP service where authentication originated.</li>
 * <li>Resolve the user's organization information using the corresponding
 * {@link OrganizationsApi}.</li>
 * <li>Handle OAuth2-based user identification.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The mapping between LDAP configuration names and their corresponding APIs is
 * established through configuration properties following the pattern:
 * {@code georchestra.gateway.security.<serviceName>.*}.
 * </p>
 *
 * Example usage:
 * 
 * <pre>
 * {
 *     &#64;code
 *     Optional<ExtendedGeorchestraUser> user = demultiplexer.findByUsername("ldap-service-1", "jdoe");
 * }
 * </pre>
 *
 * @see GeorchestraUser
 * @see ExtendedGeorchestraUser
 * @see UsersApi
 * @see OrganizationsApi
 */
@RequiredArgsConstructor
public class DemultiplexingUsersApi {

    /**
     * Mapping between service names and their corresponding {@link UsersApi}
     * instances.
     */
    private final @NonNull Map<String, UsersApi> usersByConfigName;

    /**
     * Mapping between service names and their corresponding
     * {@link OrganizationsApi} instances.
     */
    private final @NonNull Map<String, OrganizationsApi> orgsByConfigName;

    /**
     * Retrieves the set of configured service names.
     *
     * @return a set containing all registered LDAP service names.
     */
    public @VisibleForTesting Set<String> getTargetNames() {
        return new HashSet<>(usersByConfigName.keySet());
    }

    /**
     * Finds a user by username within a specific LDAP service.
     *
     * @param serviceName the LDAP service configuration name.
     * @param username    the username to search for.
     * @return an {@link Optional} containing the {@link ExtendedGeorchestraUser},
     *         or empty if the user is not found.
     * @throws NullPointerException if no {@link UsersApi} is registered for the
     *                              given service.
     */
    public Optional<ExtendedGeorchestraUser> findByUsername(@NonNull String serviceName, @NonNull String username) {
        UsersApi usersApi = Objects.requireNonNull(usersByConfigName.get(serviceName),
                () -> "No UsersApi found for config named " + serviceName);

        Optional<GeorchestraUser> user = usersApi.findByUsername(username);
        return extendUserWithOrganization(serviceName, user);
    }

    /**
     * Finds a user by username in the first registered LDAP service.
     * <p>
     * This method is useful when only one LDAP service is expected, but may not be
     * reliable when multiple LDAP configurations exist.
     * </p>
     *
     * @param username the username to search for.
     * @return an {@link Optional} containing the {@link ExtendedGeorchestraUser},
     *         or empty if the user is not found.
     */
    public Optional<ExtendedGeorchestraUser> findByUsername(@NonNull String username) {
        return usersByConfigName.keySet().stream().findFirst()
                .flatMap(serviceName -> findByUsername(serviceName, username));
    }

    /**
     * Finds a user by email within a specific LDAP service.
     *
     * @param serviceName the LDAP service configuration name.
     * @param email       the email to search for.
     * @return an {@link Optional} containing the {@link ExtendedGeorchestraUser},
     *         or empty if the user is not found.
     * @throws NullPointerException if no {@link UsersApi} is registered for the
     *                              given service.
     */
    public Optional<ExtendedGeorchestraUser> findByEmail(@NonNull String serviceName, @NonNull String email) {
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Objects.requireNonNull(usersApi, () -> "No UsersApi found for config named " + serviceName);
        Optional<GeorchestraUser> user = usersApi.findByEmail(email);

        return extendUserWithOrganization(serviceName, user);
    }

    /**
     * Finds a user by email in the first registered LDAP service.
     * <p>
     * This method is useful when only one LDAP service is expected, but may not be
     * reliable when multiple LDAP configurations exist.
     * </p>
     *
     * @param email the email to search for.
     * @return an {@link Optional} containing the {@link ExtendedGeorchestraUser},
     *         or empty if the user is not found.
     */
    public Optional<ExtendedGeorchestraUser> findByEmail(@NonNull String email) {
        String serviceName = usersByConfigName.keySet().stream().findFirst().get();
        UsersApi usersApi = usersByConfigName.get(serviceName);
        Optional<GeorchestraUser> user = usersApi.findByEmail(email);

        return extendUserWithOrganization(serviceName, user);
    }

    /**
     * Finds a user by their OAuth2 provider and unique identifier.
     * <p>
     * This method attempts to match an OAuth2-authenticated user within the first
     * registered LDAP service.
     * </p>
     *
     * @param oauth2Provider the OAuth2 provider name (e.g., "google", "github").
     * @param oauth2Uid      the unique identifier for the user within the OAuth2
     *                       provider.
     * @return an {@link Optional} containing the {@link ExtendedGeorchestraUser},
     *         or empty if the user is not found.
     * @throws NullPointerException if no {@link UsersApi} is registered for the
     *                              selected service.
     */
    public Optional<ExtendedGeorchestraUser> findByOAuth2Uid(@NonNull String oauth2Provider,
            @NonNull String oauth2Uid) {
        return usersByConfigName.keySet().stream().findFirst().flatMap(serviceName -> {
            UsersApi usersApi = Objects.requireNonNull(usersByConfigName.get(serviceName),
                    () -> "No UsersApi found for config named " + serviceName);

            Optional<GeorchestraUser> user = usersApi.findByOAuth2Uid(oauth2Provider, oauth2Uid);
            return extendUserWithOrganization(serviceName, user);
        });
    }

    /**
     * Extends a {@link GeorchestraUser} by attaching its corresponding organization
     * details.
     *
     * @param serviceName the LDAP service configuration name.
     * @param user        the resolved user, if present.
     * @return an {@link Optional} containing the {@link ExtendedGeorchestraUser}
     *         with organization details.
     * @throws NullPointerException if no {@link OrganizationsApi} is registered for
     *                              the given service.
     */
    private Optional<ExtendedGeorchestraUser> extendUserWithOrganization(String serviceName,
            Optional<GeorchestraUser> user) {
        OrganizationsApi orgsApi = Objects.requireNonNull(orgsByConfigName.get(serviceName),
                () -> "No OrganizationsApi found for config named " + serviceName);

        Organization org = user.map(GeorchestraUser::getOrganization).flatMap(orgsApi::findByShortName).orElse(null);

        return user.map(ExtendedGeorchestraUser::new).map(u -> u.setOrg(org));
    }
}
