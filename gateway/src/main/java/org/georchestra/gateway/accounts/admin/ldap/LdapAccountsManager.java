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
package org.georchestra.gateway.accounts.admin.ldap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.DuplicatedCommonNameException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.roles.RoleFactory;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.ds.users.AccountFactory;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.georchestra.ds.users.DuplicatedUidException;
import org.georchestra.gateway.accounts.admin.AbstractAccountsManager;
import org.georchestra.gateway.accounts.admin.AccountManager;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.exceptions.DuplicatedUsernameFoundException;
import org.georchestra.gateway.security.ldap.extended.DemultiplexingUsersApi;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.NameNotFoundException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AccountManager} that manages {@link GeorchestraUser}
 * accounts through an extended LDAP service.
 * <p>
 * This class provides methods for fetching, creating, and managing user
 * accounts stored in LDAP via an {@link AccountDao} and {@link RoleDao}. If a
 * user does not exist, it ensures the necessary roles and organizational
 * memberships are created.
 * </p>
 *
 * <p>
 * Role names are automatically prefixed with {@code "ROLE_"} if missing.
 * </p>
 *
 * @see AccountManager
 * @see AbstractAccountsManager
 * @see DemultiplexingUsersApi
 * @see AccountDao
 * @see RoleDao
 * @see OrgsDao
 */
@Slf4j(topic = "org.georchestra.gateway.accounts.admin.ldap")
class LdapAccountsManager extends AbstractAccountsManager {

    /** Configuration properties for security-related settings. */
    private final @NonNull GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties;

    /** DAO for managing user accounts in LDAP. */
    private final @NonNull AccountDao accountDao;

    /** DAO for managing roles and permissions in LDAP. */
    private final @NonNull RoleDao roleDao;

    /** DAO for managing organizations in LDAP. */
    private final @NonNull OrgsDao orgsDao;

    /** API for resolving users based on OAuth2 credentials. */
    private final @NonNull DemultiplexingUsersApi demultiplexingUsersApi;

    /**
     * Constructs an instance of {@code LdapAccountsManager}.
     *
     * @param eventPublisher                             the application event
     *                                                   publisher used for
     *                                                   publishing account-related
     *                                                   events
     * @param accountDao                                 the DAO responsible for
     *                                                   managing user accounts in
     *                                                   LDAP
     * @param roleDao                                    the DAO responsible for
     *                                                   managing roles in LDAP
     * @param orgsDao                                    the DAO responsible for
     *                                                   managing organizations in
     *                                                   LDAP
     * @param demultiplexingUsersApi                     the API used for resolving
     *                                                   users by OAuth2 credentials
     * @param georchestraGatewaySecurityConfigProperties configuration properties
     *                                                   for security settings
     */
    public LdapAccountsManager(ApplicationEventPublisher eventPublisher, AccountDao accountDao, RoleDao roleDao,
            OrgsDao orgsDao, DemultiplexingUsersApi demultiplexingUsersApi,
            GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties) {
        super(eventPublisher);
        this.accountDao = accountDao;
        this.roleDao = roleDao;
        this.orgsDao = orgsDao;
        this.demultiplexingUsersApi = demultiplexingUsersApi;
        this.georchestraGatewaySecurityConfigProperties = georchestraGatewaySecurityConfigProperties;
    }

    /**
     * Retrieves a stored user based on their OAuth2 provider and unique identifier.
     * <p>
     * This method queries the {@link DemultiplexingUsersApi} for a user with the
     * given OAuth2 provider and unique identifier. If found, the user's roles are
     * normalized to ensure they are properly prefixed.
     * </p>
     *
     * @param oAuth2Provider the OAuth2 provider (e.g., Google, GitHub)
     * @param oAuth2Uid      the unique identifier assigned by the OAuth2 provider
     * @return an {@link Optional} containing the user if found, otherwise empty
     */
    @Override
    protected Optional<GeorchestraUser> findByOAuth2Uid(@NonNull String oAuth2Provider, @NonNull String oAuth2Uid) {
        return demultiplexingUsersApi.findByOAuth2Uid(oAuth2Provider, oAuth2Uid).map(this::ensureRolesPrefixed);
    }

    /**
     * Retrieves a stored user based on their username.
     * <p>
     * This method queries the {@link DemultiplexingUsersApi} for a user with the
     * given username. If found, the user's roles are normalized to ensure they are
     * properly prefixed.
     * </p>
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user if found, otherwise empty
     */
    @Override
    protected Optional<GeorchestraUser> findByUsername(@NonNull String username) {
        return demultiplexingUsersApi.findByUsername(username).map(this::ensureRolesPrefixed);
    }

    /**
     * Ensures all roles assigned to a user are prefixed with {@code "ROLE_"}.
     * <p>
     * If a role does not start with "ROLE_", this method adds the prefix. This
     * normalization ensures consistency when handling role-based access.
     * </p>
     *
     * @param user the user whose roles need to be normalized
     * @return the updated {@link GeorchestraUser} with properly formatted roles
     */
    private GeorchestraUser ensureRolesPrefixed(GeorchestraUser user) {
        List<String> roles = user.getRoles().stream().filter(Objects::nonNull)
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r).toList(); // Converts to an immutable list
        user.setRoles(new ArrayList<>(roles)); // Ensures mutability
        return user;
    }

    /**
     * Creates a new user in the LDAP repository if one does not already exist.
     * <p>
     * This method first attempts to insert the user into LDAP. If an error occurs
     * due to duplicate emails or usernames, appropriate exceptions are thrown.
     * </p>
     * <p>
     * If the user is successfully inserted, their organization is ensured to exist.
     * If an error occurs while managing the organization, the user account creation
     * is rolled back.
     * </p>
     * <p>
     * Finally, roles are assigned to the user to ensure correct access levels.
     * </p>
     *
     * @param mapped the user to create
     * @throws DuplicatedEmailFoundException    if a user with the same email
     *                                          already exists
     * @throws DuplicatedUsernameFoundException if a user with the same username
     *                                          already exists
     */
    @Override
    protected void createInternal(GeorchestraUser mapped) throws DuplicatedEmailFoundException {
        Account newAccount = mapToAccountBrief(mapped);
        try {
            accountDao.insert(newAccount);
        } catch (DataServiceException accountError) {
            throw new IllegalStateException(accountError);
        } catch (DuplicatedEmailException accountError) {
            throw new DuplicatedEmailFoundException(accountError.getMessage());
        } catch (DuplicatedUidException accountError) {
            throw new DuplicatedUsernameFoundException(accountError.getMessage());
        }

        try {
            ensureOrgExists(newAccount);
        } catch (IllegalStateException orgError) {
            log.error("Error when trying to create / update the organisation {}, reverting the account creation",
                    newAccount.getOrg(), orgError);
            rollbackAccount(newAccount);
            throw orgError;
        }

        ensureRolesExist(mapped, newAccount);
    }

    /**
     * Ensures all roles assigned to a user are prefixed with {@code "ROLE_"}.
     *
     * @param user the user whose roles need to be normalized
     * @return the updated user with properly formatted roles
     */
    private void ensureRolesExist(GeorchestraUser mapped, Account newAccount) {
        try {// account created, add roles
            if (!mapped.getRoles().contains("ROLE_USER")) {
                roleDao.addUser("USER", newAccount);
            }
            for (String role : mapped.getRoles()) {
                role = role.replaceFirst("^ROLE_", "");
                ensureRoleExists(role);
                roleDao.addUser(role, newAccount);
            }
        } catch (NameNotFoundException | DataServiceException roleError) {
            try {// roll-back account
                accountDao.delete(newAccount);
            } catch (NameNotFoundException | DataServiceException rollbackError) {
                log.warn("Error reverting user creation after roleDao update failure", rollbackError);
            }
            throw new IllegalStateException(roleError);
        }
    }

    /**
     * Ensures the organization associated with a user exists in LDAP.
     *
     * @param newAccount the account whose organization needs verification
     */
    private void ensureRoleExists(String role) throws DataServiceException {
        try {
            roleDao.findByCommonName(role);
        } catch (NameNotFoundException notFound) {
            try {
                roleDao.insert(RoleFactory.create(role, null, null));
            } catch (DuplicatedCommonNameException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Maps a {@link GeorchestraUser} to a brief {@link Account} representation for
     * LDAP storage.
     * <p>
     * This method extracts key user details such as username, email, and
     * organization, and constructs an {@link Account} object suitable for insertion
     * into LDAP. The generated account is marked as non-pending and assigned a
     * default organization if none is provided.
     * </p>
     *
     * @param preAuth the pre-authenticated {@link GeorchestraUser} containing user
     *                details
     * @return a newly created {@link Account} object with mapped attributes
     */
    private Account mapToAccountBrief(@NonNull GeorchestraUser preAuth) {
        String username = preAuth.getUsername();
        String email = preAuth.getEmail();
        String firstName = preAuth.getFirstName();
        String lastName = preAuth.getLastName();
        String org = preAuth.getOrganization();
        String password = null;
        String phone = "";
        String title = "";
        String description = "";
        final @Nullable String oAuth2Provider = preAuth.getOAuth2Provider();
        final @Nullable String oAuth2Uid = preAuth.getOAuth2Uid();

        Account newAccount = AccountFactory.createBrief(username, password, firstName, lastName, email, phone, title,
                description, oAuth2Provider, oAuth2Uid);
        newAccount.setPending(false);
        String defaultOrg = this.georchestraGatewaySecurityConfigProperties.getDefaultOrganization();
        if (StringUtils.isEmpty(org) && !StringUtils.isBlank(defaultOrg)) {
            newAccount.setOrg(defaultOrg);
        } else {
            newAccount.setOrg(org);
        }
        return newAccount;
    }

    /**
     * @throws IllegalStateException if the org can't be created/updated
     */
    private void ensureOrgExists(@NonNull Account newAccount) {
        final String orgId = newAccount.getOrg();
        if (!StringUtils.isEmpty(orgId)) {
            findOrg(orgId).ifPresentOrElse(org -> addAccountToOrg(newAccount, org),
                    () -> createOrgAndAddAccount(newAccount, orgId));
        }
    }

    /**
     * Creates an organization and assigns the user to it.
     *
     * @param newAccount the user account to add to the new organization
     * @param orgId      the identifier of the organization
     */
    private void createOrgAndAddAccount(Account newAccount, final String orgId) {
        try {
            log.info("Org {} does not exist, trying to create it", orgId);
            Org org = newOrg(orgId);
            org.getMembers().add(newAccount.getUid());
            orgsDao.insert(org);
        } catch (Exception orgError) {
            throw new IllegalStateException(orgError);
        }
    }

    private void addAccountToOrg(Account newAccount, Org org) {
        // org already in the LDAP, add the newly created account to it
        org.getMembers().add(newAccount.getUid());
        orgsDao.update(org);
    }

    /**
     * Finds an organization by its identifier.
     *
     * @param orgId the identifier of the organization
     * @return an {@link Optional} containing the organization if found, otherwise
     *         empty
     */
    private Optional<Org> findOrg(String orgId) {
        try {
            return Optional.of(orgsDao.findByCommonName(orgId));
        } catch (NameNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Rolls back user creation if an error occurs.
     *
     * @param newAccount the user account to remove from LDAP
     */
    private void rollbackAccount(Account newAccount) {
        try {// roll-back account
            accountDao.delete(newAccount);
        } catch (NameNotFoundException | DataServiceException rollbackError) {
            log.warn("Error reverting user creation after orgsDao update failure", rollbackError);
        }
    }

    /**
     * Factory method to create a new org with the given id and return it
     */
    private Org newOrg(final String orgId) {
        Org org = new Org();
        org.setId(orgId);
        org.setName(orgId);
        org.setShortName(orgId);
        org.setOrgType("Other");
        return org;
    }
}
