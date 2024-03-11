package org.georchestra.gateway.security.ldap.extended;

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

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.orgs.OrgsDaoImpl;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.roles.RoleDaoImpl;
import org.georchestra.ds.roles.RoleProtected;
import org.georchestra.ds.security.*;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.ds.users.AccountDaoImpl;
import org.georchestra.ds.users.UserRule;
import org.georchestra.gateway.security.GeorchestraUserMapperExtension;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties.Server;
import org.georchestra.gateway.security.ldap.basic.LdapAuthenticatorProviderBuilder;
import org.georchestra.security.api.OrganizationsApi;
import org.georchestra.security.api.UsersApi;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import lombok.extern.slf4j.Slf4j;

/**
 * Sets up a {@link GeorchestraUserMapperExtension} that knows how to map an
 * authentication credentials given by a
 * {@link GeorchestraUserNamePasswordAuthenticationToken} with an
 * {@link LdapUserDetails} (i.e., if the user authenticated with LDAP), to a
 * {@link GeorchestraUser}, making use of geOrchestra's
 * {@literal georchestra-ldap-account-management} module's {@link UsersApi}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
@Slf4j(topic = "org.georchestra.gateway.security.ldap.extended")
public class ExtendedLdapAuthenticationConfiguration {

    @Bean
    GeorchestraLdapAuthenticatedUserMapper georchestraLdapAuthenticatedUserMapper(DemultiplexingUsersApi users) {
        return users.getTargetNames().isEmpty() ? null : new GeorchestraLdapAuthenticatedUserMapper(users);
    }

    @Bean
    List<ExtendedLdapConfig> enabledExtendedLdapConfigs(GeorchestraGatewaySecurityConfigProperties config) {
        return config.extendedEnabled();
    }

    @Bean
    List<GeorchestraLdapAuthenticationProvider> extendedLdapAuthenticationProviders(List<ExtendedLdapConfig> configs) {
        return configs.stream().map(this::createLdapProvider).collect(Collectors.toList());
    }

    private GeorchestraLdapAuthenticationProvider createLdapProvider(ExtendedLdapConfig config) {
        log.info("Creating extended LDAP AuthenticationProvider {} at {}", config.getName(), config.getUrl());

        final LdapTemplate ldapTemplate;
        try {
            ldapTemplate = ldapTemplate(config);
            final AccountDao accountsDao = accountsDao(ldapTemplate, config);
            ExtendedLdapAuthenticationProvider delegate = new LdapAuthenticatorProviderBuilder()//
                    .url(config.getUrl())//
                    .baseDn(config.getBaseDn())//
                    .userSearchBase(config.getUsersRdn())//
                    .userSearchFilter(config.getUsersSearchFilter())//
                    .rolesSearchBase(config.getRolesRdn())//
                    .rolesSearchFilter(config.getRolesSearchFilter())//
                    .adminDn(config.getAdminDn().orElse(null))//
                    .adminPassword(config.getAdminPassword().orElse(null))//
                    .returningAttributes(config.getReturningAttributes()).accountDao(accountsDao).build();
            return new GeorchestraLdapAuthenticationProvider(config.getName(), delegate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    DemultiplexingUsersApi demultiplexingUsersApi(List<ExtendedLdapConfig> configs) {
        Map<String, UsersApi> usersByConfigName = new HashMap<>();
        Map<String, OrganizationsApi> orgsByConfigName = new HashMap<>();
        for (ExtendedLdapConfig config : configs) {
            try {
                LdapTemplate ldapTemplate = ldapTemplate(config);
                AccountDao accountsDao = accountsDao(ldapTemplate, config);
                UsersApi usersApi = createUsersApi(config, ldapTemplate, accountsDao);
                OrganizationsApi orgsApi = createOrgsApi(config, ldapTemplate, accountsDao);
                usersByConfigName.put(config.getName(), usersApi);
                orgsByConfigName.put(config.getName(), orgsApi);
            } catch (Exception ex) {
                throw new BeanInitializationException(
                        "Error creating georchestra users api for ldap config " + config.getName(), ex);
            }
        }
        return new DemultiplexingUsersApi(usersByConfigName, orgsByConfigName);
    }

    //////////////////////////////////////////////
    /// Low level LDAP account management beans
    //////////////////////////////////////////////

    private OrganizationsApi createOrgsApi(ExtendedLdapConfig ldapConfig, LdapTemplate ldapTemplate,
            AccountDao accountsDao) throws Exception {
        OrganizationsApiImpl impl = new OrganizationsApiImpl();
        OrgsDaoImpl orgsDao = new OrgsDaoImpl();
        orgsDao.setLdapTemplate(ldapTemplate);
        orgsDao.setAccountDao(accountsDao);
        orgsDao.setBasePath(ldapConfig.getBaseDn());
        orgsDao.setOrgSearchBaseDN(ldapConfig.getOrgsRdn());
        orgsDao.setPendingOrgSearchBaseDN(ldapConfig.getPendingOrgsRdn());
        impl.setOrgsDao(orgsDao);
        impl.setOrgMapper(new OrganizationMapperImpl());
        return impl;
    }

    private UsersApi createUsersApi(ExtendedLdapConfig ldapConfig, LdapTemplate ldapTemplate, AccountDao accountsDao)
            throws Exception {
        final RoleDao roleDao = roleDao(ldapTemplate, ldapConfig, accountsDao);

        final UserMapper ldapUserMapper = createUserMapper(roleDao);
        UserRule userRule = ldapUserRule(ldapConfig);

        UsersApiImpl impl = new UsersApiImpl();
        impl.setAccountsDao(accountsDao);
        impl.setMapper(ldapUserMapper);
        impl.setUserRule(userRule);
        return impl;
    }

    private UserMapper createUserMapper(RoleDao roleDao) {
        UserMapperImpl impl = new UserMapperImpl();
        impl.setRoleDao(roleDao);
        return impl;
    }

    private LdapTemplate ldapTemplate(ExtendedLdapConfig server) throws Exception {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(server.getUrl());
        contextSource.setBase(server.getBaseDn());
        contextSource.afterPropertiesSet();

        LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
        ldapTemplate.afterPropertiesSet();
        return ldapTemplate;
    }

    private AccountDao accountsDao(LdapTemplate ldapTemplate, ExtendedLdapConfig ldapConfig) {
        String baseDn = ldapConfig.getBaseDn();
        String userSearchBaseDN = ldapConfig.getUsersRdn();
        String roleSearchBaseDN = ldapConfig.getRolesRdn();

        // we don't need a configuration property for this,
        // we don't allow pending users to log in. The LdapAuthenticationProvider won't
        // even look them up.
        final String pendingUsersSearchBaseDN = "ou=pendingusers";

        AccountDaoImpl impl = new AccountDaoImpl(ldapTemplate);
        impl.setBasePath(baseDn);
        impl.setUserSearchBaseDN(userSearchBaseDN);
        impl.setRoleSearchBaseDN(roleSearchBaseDN);
        if (pendingUsersSearchBaseDN != null) {
            impl.setPendingUserSearchBaseDN(pendingUsersSearchBaseDN);
        }

        String orgSearchBaseDN = ldapConfig.getOrgsRdn();
        requireNonNull(orgSearchBaseDN);
        impl.setOrgSearchBaseDN(orgSearchBaseDN);

        // not needed here, only console cares, we shouldn't allow to authenticate
        // pending users, should we?
        final String pendingOrgSearchBaseDN = "ou=pendingorgs";
        impl.setPendingOrgSearchBaseDN(pendingOrgSearchBaseDN);

        impl.init();
        return impl;
    }

    private RoleDao roleDao(LdapTemplate ldapTemplate, ExtendedLdapConfig ldapConfig, AccountDao accountDao) {
        final String rolesRdn = ldapConfig.getRolesRdn();
        RoleDaoImpl impl = new RoleDaoImpl();
        impl.setLdapTemplate(ldapTemplate);
        impl.setRoleSearchBaseDN(rolesRdn);
        impl.setAccountDao(accountDao);
        impl.setRoles(ldapProtectedRoles(ldapConfig));
        return impl;
    }

    @SuppressWarnings("unused")
    private OrgsDao orgsDao(LdapTemplate ldapTemplate, Server ldapConfig) {
        OrgsDaoImpl impl = new OrgsDaoImpl();
        impl.setLdapTemplate(ldapTemplate);
        impl.setBasePath(ldapConfig.getBaseDn());
        impl.setOrgSearchBaseDN(ldapConfig.getOrgs().getRdn());

        final String pendingOrgSearchBaseDN = "ou=pendingorgs";

        // not needed here, only console cares, we shouldn't allow to authenticate
        // pending users, should we?
        impl.setPendingOrgSearchBaseDN(pendingOrgSearchBaseDN);
        // not needed here, only console's OrgsController cares about this, right?
        // final String orgTypes = "Association,Company,NGO,Individual,Other";
        // impl.setOrgTypeValues(orgTypes);
        return impl;
    }

    private UserRule ldapUserRule(ExtendedLdapConfig ldapConfig) {
        // we can't possibly try to delete a protected user, so no need to configure
        // them
        List<String> protectedUsers = Collections.emptyList();
        UserRule rule = new UserRule();
        rule.setListOfprotectedUsers(protectedUsers.toArray(String[]::new));
        return rule;
    }

    private RoleProtected ldapProtectedRoles(ExtendedLdapConfig ldapConfig) {
        // protected roles are used by the console service to avoid deleting them. This
        // application will never try to do so, so we don't care about configuring them
        List<String> protectedRoles = List.of();
        RoleProtected bean = new RoleProtected();
        bean.setListOfprotectedRoles(protectedRoles.toArray(String[]::new));
        return bean;
    }

}
