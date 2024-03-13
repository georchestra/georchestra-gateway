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

import java.util.Arrays;
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
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.gateway.security.exceptions.DuplicatedUsernameFoundException;
import org.georchestra.security.api.UsersApi;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.beans.factory.annotation.Value;
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

    private @Value("${georchestra.gateway.security.defaultOrganization:}") String defaultOrganization;
    private final @NonNull AccountDao accountDao;
    private final @NonNull RoleDao roleDao;

    private final @NonNull OrgsDao orgsDao;
    private final @NonNull UsersApi usersApi;

    public LdapAccountsManager(ApplicationEventPublisher eventPublisher, AccountDao accountDao, RoleDao roleDao,
            OrgsDao orgsDao, UsersApi usersApi) {
        super(eventPublisher);
        this.accountDao = accountDao;
        this.roleDao = roleDao;
        this.orgsDao = orgsDao;
        this.usersApi = usersApi;
    }

    @Override
    protected Optional<GeorchestraUser> findByOAuth2Uid(@NonNull String oAuth2Provider, @NonNull String oAuth2Uid) {
        return usersApi.findByOAuth2Uid(oAuth2Provider, oAuth2Uid).map(this::ensureRolesPrefixed);
    }

    @Override
    protected Optional<GeorchestraUser> findByUsername(@NonNull String username) {
        return usersApi.findByUsername(username).map(this::ensureRolesPrefixed);
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

        ensureOrgExists(newAccount);

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

        Account newAccount = AccountFactory.createBrief(username, password, firstName, lastName, email, phone, title,
                description, oAuth2Provider, oAuth2Uid);
        newAccount.setPending(false);
        if (StringUtils.isEmpty(org) && !StringUtils.isBlank(defaultOrganization)) {
            newAccount.setOrg(defaultOrganization);
        } else {
            newAccount.setOrg(org);
        }
        return newAccount;
    }

    private void ensureOrgExists(@NonNull Account newAccount) {
        String orgId = newAccount.getOrg();
        if (StringUtils.isEmpty(orgId))
            return;
        try { // account created, add org
            Org org;
            try {
                org = orgsDao.findByCommonName(orgId);
                // org already in the LDAP, add the newly
                // created account to it
                List<String> currentMembers = org.getMembers();
                currentMembers.add(newAccount.getUid());
                org.setMembers(currentMembers);
                orgsDao.update(org);
            } catch (NameNotFoundException e) {
                log.info("Org {} does not exist, trying to create it", orgId);
                // org does not exist yet, create it
                org = new Org();
                org.setId(orgId);
                org.setName(orgId);
                org.setShortName(orgId);
                org.setOrgType("Other");
                org.setMembers(Arrays.asList(newAccount.getUid()));
                orgsDao.insert(org);
            }
        } catch (Exception orgError) {
            log.error("Error when trying to create / update the organisation {}, reverting the account creation", orgId,
                    orgError);
            try {// roll-back account
                accountDao.delete(newAccount);
            } catch (NameNotFoundException | DataServiceException rollbackError) {
                log.warn("Error reverting user creation after orgsDao update failure", rollbackError);
            }
            throw new IllegalStateException(orgError);
        }
    }
}
