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

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.georchestra.ds.orgs.Org;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.oauth2.OpenIdConnectCustomConfig;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.ApplicationEventPublisher;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Abstract implementation of {@link AccountManager} providing common account
 * management logic.
 * <p>
 * This class ensures thread-safe user retrieval and creation by using a
 * {@link ReadWriteLock}. Implementations must define specific storage
 * operations for finding and creating users.
 * </p>
 *
 * <p>
 * When a new user is created, an {@link AccountCreated} event is published
 * using the {@link ApplicationEventPublisher} to notify the system of the new
 * account.
 * </p>
 *
 * @see AccountManager
 * @see org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException
 * @see org.georchestra.security.model.GeorchestraUser
 */
@RequiredArgsConstructor
public abstract class AbstractAccountsManager implements AccountManager {

    private final @NonNull ApplicationEventPublisher eventPublisher;

    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final OpenIdConnectCustomConfig providersConfig;

    /**
     * Retrieves an existing stored user corresponding to {@code mappedUser} or
     * creates a new one if not found.
     * <p>
     * This method ensures thread safety by acquiring a read lock when searching for
     * the user and a write lock when creating a new user.
     * </p>
     * <p>
     * If a new user is created, an {@link AccountCreated} event is published.
     * </p>
     *
     * @param mappedUser the user to find or create
     * @return the existing or newly created {@link GeorchestraUser}
     * @throws DuplicatedEmailFoundException if a user with the same email already
     *                                       exists
     */
    @Override
    public GeorchestraUser getOrCreate(@NonNull GeorchestraUser mappedUser) throws DuplicatedEmailFoundException {
        return find(mappedUser).orElseGet(() -> createIfMissing(mappedUser));
    }

    /**
     * Retrieves the stored user corresponding to {@code mappedUser}, if it exists.
     * <p>
     * This method is thread-safe and acquires a read lock to ensure consistent
     * reads.
     * </p>
     *
     * @param mappedUser the user to search for
     * @return an {@link Optional} containing the found user, or an empty
     *         {@link Optional} if not found
     */
    public Optional<GeorchestraUser> find(GeorchestraUser mappedUser) {
        lock.readLock().lock();
        try {
            return findInternal(mappedUser);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Internal method to search for a user based on OAuth2 credentials or username.
     * <p>
     * This method is called within {@link #find(GeorchestraUser)} and does not
     * apply any locking.
     * </p>
     *
     * @param mappedUser the user to search for
     * @return an {@link Optional} containing the found user, or an empty
     *         {@link Optional} if not found
     */
    protected Optional<GeorchestraUser> findInternal(GeorchestraUser mappedUser) {
        String oAuth2Provider = mappedUser.getOAuth2Provider();
        String oAuth2UId = mappedUser.getOAuth2Uid();
        if (oAuth2Provider != null && oAuth2UId != null) {
            // search user by email or by OAuth2Uid
            Boolean useEmail = providersConfig.useEmail(oAuth2Provider);
            return useEmail ? findByEmail(mappedUser.getEmail()) : findByOAuth2Uid(oAuth2Provider, oAuth2UId);
        }
        return findByUsername(mappedUser.getUsername());
    }

    public Org findOrgByUser(GeorchestraUser existingUser) {
        String existUserOrgCN = existingUser.getOrganization();
        return findOrg(existUserOrgCN).orElse(null);
    }

    /**
     * Control that orgUniqueId from provider match with georchestra orgUniqueId
     * 
     * @param mapped
     * @param existingUser
     * @return false if provider user's orgUniqueId is not same as LDAP user's
     *         orgUniqueId
     */
    public Boolean isSameOrgUniqueId(GeorchestraUser mapped, GeorchestraUser existingUser) {
        if (null == existingUser.getOrganization()) {
            return false;
        }

        // Compare mapped orgUniqueId with existing user's org uniqueOrgId
        Org existUserOrg = findOrgByUser(existingUser);

        // Optional.ofNullable to consider that Null and empty are the same
        String existOrgUniqueId = Optional.ofNullable(existUserOrg.getOrgUniqueId()).orElse("");
        String mappedOrgUniqueId = Optional.ofNullable(mapped.getOAuth2OrgId()).orElse("");
        // return false if provider user's orgUniqueId is not
        // same as LDAP user's orgUniqueId
        return mappedOrgUniqueId.equals(existOrgUniqueId);
    }

    @Override
    public void createUserOrgUniqueIdIfMissing(@NonNull GeorchestraUser mapped) throws DuplicatedEmailFoundException {
        lock.writeLock().lock();
        try {
            // verify if user exist
            GeorchestraUser existing = findInternal(mapped).orElse(null);
            // verify if user org match between ldap and OAuth2 info
            if (!isSameOrgUniqueId(mapped, existing)) {
                // force username from ldap instead unknown external username
                // See issue #231 : https://github.com/georchestra/georchestra-gateway/issues/231
                if (existing != null) {
                    mapped.setUsername(existing.getUsername());
                }
                // we find or create org from this orgUniqueId and add user to this org
                // unlink
                unlinkUserOrg(existing);
                // create org if necessary and add user to org
                ensureOrgExists(mapped);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Creates a user if it does not already exist in the repository.
     * <p>
     * This method acquires a write lock to ensure only one thread creates a user at
     * a time. If a user is created, an {@link AccountCreated} event is published.
     * </p>
     *
     * @param mapped the user to create if missing
     * @return the existing or newly created {@link GeorchestraUser}
     * @throws DuplicatedEmailFoundException if a user with the same email already
     *                                       exists
     */
    protected GeorchestraUser createIfMissing(GeorchestraUser mapped) throws DuplicatedEmailFoundException {
        lock.writeLock().lock();
        try {
            // verify if user exist
            GeorchestraUser existing = findInternal(mapped).orElse(null);
            if (existing == null) {
                createInternal(mapped);
                existing = findInternal(mapped).orElseThrow(() -> new IllegalStateException(
                        "User " + mapped.getUsername() + " not found immediately after creation"));
                eventPublisher.publishEvent(new AccountCreated(existing));
            }

            createUserOrgUniqueIdIfMissing(mapped);

            return existing;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds a user by their OAuth2 provider and unique identifier.
     * <p>
     * Implementations must provide a concrete method for retrieving users from
     * storage.
     * </p>
     *
     * @param oauth2Provider the OAuth2 provider (e.g., Google, GitHub)
     * @param oauth2Uid      the unique identifier assigned by the OAuth2 provider
     * @return an {@link Optional} containing the found user, or an empty
     *         {@link Optional} if not found
     */
    protected abstract Optional<GeorchestraUser> findByOAuth2Uid(String oauth2Provider, String oauth2Uid);

    /**
     * Finds a user by their username.
     * <p>
     * Implementations must provide a concrete method for retrieving users from
     * storage.
     * </p>
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the found user, or an empty
     *         {@link Optional} if not found
     */
    protected abstract Optional<GeorchestraUser> findByUsername(String username);

    /**
     * Finds a user by their email.
     * <p>
     * Implementations must provide a concrete method for retrieving users from
     * storage.
     * </p>
     *
     * @param email the email to search for
     * @return an {@link Optional} containing the found user, or an empty
     *         {@link Optional} if not found
     */
    protected abstract Optional<GeorchestraUser> findByEmail(String email);

    /**
     * Affect a user to an organization according to user's credentials.
     * <p>
     * Will create organization if not exists. Will switch (and create if not
     * exists) orgnization if OAuth2 crendentials contain an other org. Consider
     * that Provider is a source of truth.
     * 
     * Implementations must define how users are linked to an organization and the
     * behavior to manage organization in the storage.
     * </p>
     *
     * @param mapped the user's OAuth2 credentials to search for
     * @return an {@link Optional} containing the found user, or an empty
     *         {@link Optional} if not found
     */
    protected abstract void ensureOrgExists(GeorchestraUser mapped);

    /**
     * Finds a user by their organization id.
     * <p>
     * Implementations must provide a concrete method for retrieving organization
     * from storage.
     * </p>
     *
     * @param orgId the organization common name (CN) to search for
     * @return an {@link Optional} containing the found organization, or an empty
     *         {@link Optional} if not found
     */
    protected abstract Optional<Org> findOrg(String orgId);

    /**
     * Unlink user from linked organization.
     * <p>
     * Implementations must define how users and organizations are unlink in the
     * storage system.
     * </p>
     *
     * @param existingUser the user to unlink
     */
    protected abstract void unlinkUserOrg(GeorchestraUser existingUser);

    /**
     * Creates a new user in the repository.
     * <p>
     * Implementations must define how users are persisted in the storage system.
     * </p>
     *
     * @param mapped the user to create
     */
    protected abstract void createInternal(GeorchestraUser mapped);
}
