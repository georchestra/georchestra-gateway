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

import static org.assertj.core.api.Assertions.assertThat;

import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

public class GeorchestraLdapAccessConfigurationTest {

    @EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
    static @Configuration class EnableConfigProps {
    }

    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(GeorchestraLdapAccountManagementConfiguration.class));

    public @Test void accountsAndRolesDaoRelatedBeansAreAvailable() {
        runner.withPropertyValues(""//
        // Georchestra extended LDAP default config
                , "georchestra.gateway.security.ldap.default.enabled: true" //
                , "georchestra.gateway.security.ldap.default.extended: true" //
                , "georchestra.gateway.security.ldap.default.adminDn: cn=admin,dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.default.adminPassword: secret" //
                , "georchestra.gateway.security.ldap.default.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.default.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.default.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.default.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.default.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.default.roles.searchFilter: (member={0})"//
                , "georchestra.gateway.security.ldap.default.orgs.rdn: ou=orgs,dc=tes,dc=com"//
                , "georchestra.gateway.security.createNonExistingUsersInLDAP: true" //

        ).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OrgsDao.class).hasSingleBean(AccountDao.class);
        });
    }
}
