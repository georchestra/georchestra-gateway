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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.accounts.admin.ldap;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
import org.georchestra.gateway.security.oauth2.OpenIdConnectCustomConfig;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.NameNotFoundException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link AccountManager} that fetches and creates {@link GeorchestraUser}s from
 * the Georchestra extended LDAP service provided by an {@link AccountDao} and
 * {@link RoleDao}.
 */
@Slf4j(topic = "org.georchestra.gateway.accounts.admin.ldap")
class LdapAccountsManager extends AbstractAccountsManager {

    private final @NonNull GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties;
    private final @NonNull AccountDao accountDao;
    private final @NonNull RoleDao roleDao;
    private final @NonNull OrgsDao orgsDao;
    private final @NonNull DemultiplexingUsersApi demultiplexingUsersApi;
    private final @NonNull OpenIdConnectCustomConfig providersConfig;

    public LdapAccountsManager(ApplicationEventPublisher eventPublisher, AccountDao accountDao, RoleDao roleDao,
            OrgsDao orgsDao, DemultiplexingUsersApi demultiplexingUsersApi,
            GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties,
            OpenIdConnectCustomConfig providersConfig) {
        super(eventPublisher, providersConfig);
        this.accountDao = accountDao;
        this.roleDao = roleDao;
        this.orgsDao = orgsDao;
        this.demultiplexingUsersApi = demultiplexingUsersApi;
        this.georchestraGatewaySecurityConfigProperties = georchestraGatewaySecurityConfigProperties;
        this.providersConfig = providersConfig;
    }

    @Override
    protected Optional<GeorchestraUser> findByOAuth2Uid(@NonNull String oAuth2Provider, @NonNull String oAuth2Uid) {
        return demultiplexingUsersApi.findByOAuth2Uid(oAuth2Provider, oAuth2Uid).map(this::ensureRolesPrefixed);
    }

    @Override
    protected Optional<GeorchestraUser> findByUsername(@NonNull String username) {
        return demultiplexingUsersApi.findByUsername(username).map(this::ensureRolesPrefixed);
    }

    @Override
    protected Optional<GeorchestraUser> findByEmail(@NonNull String email) {
        return demultiplexingUsersApi.findByEmail(email).map(this::ensureRolesPrefixed);
    }

    private GeorchestraUser ensureRolesPrefixed(GeorchestraUser user) {
        List<String> roles = user.getRoles().stream().filter(Objects::nonNull)
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r).collect(Collectors.toList());
        user.setRoles(roles);
        return user;
    }

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
            rollbackAccount(newAccount, newAccount.getOrg());
            throw orgError;
        }

        ensureRolesExist(mapped, newAccount);
    }

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
        final @javax.annotation.Nullable String oAuth2Provider = preAuth.getOAuth2Provider();
        final @javax.annotation.Nullable String oAuth2Uid = preAuth.getOAuth2Uid();
        final @javax.annotation.Nullable String oAuth2OrgId = preAuth.getOAuth2OrgId();

        Account newAccount = AccountFactory.createBrief(username, password, firstName, lastName, email, phone, title,
                description, oAuth2Provider, oAuth2Uid);
        // if provider org id exists, we will use it as uniqueOrgId
        newAccount.setOAuth2OrgId(Optional.ofNullable(oAuth2OrgId).orElse(""));
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
    @Override
    protected void unlinkUserOrg(@NonNull GeorchestraUser user) {
        if (user.getOrganization() != null) {
            Account newAccount = mapToAccountBrief(user);
            orgsDao.unlinkUser(newAccount);
        }
    }

    /**
     * @throws IllegalStateException if the org can't be created/updated
     */
    @Override
    protected void ensureOrgExists(@NonNull GeorchestraUser mappedUser) {
        Account newAccount = mapToAccountBrief(mappedUser);
        ensureOrgExists(newAccount);
    }

    /**
     * Retrieve LDAP organization from org value
     * 
     * @throws IllegalStateException if the org can't be created/updated
     */
    private void ensureOrgExists(@NonNull Account newAccount) {
        final String orgId = newAccount.getOrg();
        String orgUniqueId = Optional.ofNullable(newAccount.getOAuth2OrgId()).orElse("");

        // search by orgUniqueId or CN
        if (!StringUtils.isEmpty(orgId)) {
            Optional<Org> existingOrg = StringUtils.isNotEmpty(orgUniqueId) ? findOrgById(orgId, orgUniqueId)
                    : findOrg(orgId);

            existingOrg.ifPresentOrElse(org -> addAccountToOrg(newAccount, org),
                    () -> createOrgAndAddAccount(newAccount, orgId, orgUniqueId));
        }
    }

    private void createOrgAndAddAccount(Account newAccount, final String orgId, final String orgUniqueId) {
        try {
            log.info("Org {} does not exist, trying to create it", orgId);
            Org org = newOrg(orgId, orgUniqueId);
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

    protected Optional<Org> findOrgById(String orgId, String orgUniqueId) {
        orgUniqueId = (orgUniqueId == null || orgUniqueId.isEmpty()) ? null : orgUniqueId;
        try {
            String cn = Optional.ofNullable(orgUniqueId)
                    .flatMap(id -> Optional.ofNullable(orgsDao.findByOrgUniqueId(id))).map(Org::getId).orElse(orgId);
            return findOrg(cn);
        } catch (NameNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<Org> findOrg(String orgId) {
        try {
            return Optional.of(orgsDao.findByCommonName(orgId));
        } catch (NameNotFoundException e) {
            return Optional.empty();
        }
    }

    private void rollbackAccount(Account newAccount, final String orgId) {
        try {// roll-back account
            accountDao.delete(newAccount);
        } catch (NameNotFoundException | DataServiceException rollbackError) {
            log.warn("Error reverting user creation after orgsDao update failure", rollbackError);
        }
    }

    private Org newOrg(final String orgId, final String orgUniqueId) {
        Org org = newOrg(orgId);
        org.setOrgUniqueId(Optional.ofNullable(orgUniqueId).orElse(""));
        return org;
    }

    private Org newOrg(final String orgId) {
        Org org = new Org();
        org.setId(orgId);
        org.setName(orgId);
        org.setShortName(orgId);
        org.setOrgType("Other");
        return org;
    }
}
