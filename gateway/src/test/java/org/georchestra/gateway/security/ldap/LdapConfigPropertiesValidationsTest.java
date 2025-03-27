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

package org.georchestra.gateway.security.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.ldap.basic.LdapServerConfig;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Test cases for {@link LdapConfigPropertiesValidations}.
 * <p>
 * {@link LdapConfigPropertiesValidations} will forbid application startup if
 * {@link GeorchestraGatewaySecurityConfigProperties} is invalid.
 * <p>
 * {@link GeorchestraGatewaySecurityConfigProperties} is loaded from application
 * properties, usually from georchestra datadirectory's
 * {@literal gateway/gateway.yaml}
 */
class LdapConfigPropertiesValidationsTest {

    @EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
    static @Configuration class EnableConfigProps {

    }

    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableConfigProps.class);

    @Test
    void no_ldap_configs() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(context.getBean(GeorchestraGatewaySecurityConfigProperties.class).getLdap()).isEmpty();
        });
    }

    @Test
    void no_ldap_enabled() {
        runner.withPropertyValues(//
                "georchestra.gateway.security.ldap.ldap1.enabled: false" //
                , "georchestra.gateway.security.ldap.ldap2.enabled: false" //
        ).run(context -> {
            assertThat(context).hasSingleBean(GeorchestraGatewaySecurityConfigProperties.class);
            GeorchestraGatewaySecurityConfigProperties config = context
                    .getBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(config.getLdap()).hasSize(2);
            assertThat(config.simpleEnabled()).isEmpty();
            assertThat(config.extendedEnabled()).isEmpty();
        });
    }

    @Test
    void validates_common_url_is_mandatory_if_enabled() {
        runner.withPropertyValues(""//
                , "georchestra.gateway.security.ldap.basic1.enabled: true" //
                , "georchestra.gateway.security.ldap.basic1.url:" //
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: " //
                , "georchestra.gateway.security.ldap.ad1.enabled: true" //
                , "georchestra.gateway.security.ldap.ad1.activeDirectory: true" //
                , "georchestra.gateway.security.ldap.ad1.url:" //
                , "georchestra.gateway.security.ldap.basic2.enabled: false" //
                , "georchestra.gateway.security.ldap.basic2.url:" //
        ).run(context -> {
            assertThat(context).getFailure().hasStackTraceContaining("LDAP URL is required")
                    .hasStackTraceContaining("ldap.[basic1].url").hasStackTraceContaining("ldap.[extended1].url")
                    .hasStackTraceContaining("ldap.[ad1].url");
        });
    }

    @Test
    void validates_common_url_not_set_does_not_fail_if_not_enabled() {
        runner.withPropertyValues(""//
                , "georchestra.gateway.security.ldap.basic1.enabled: false" //
                , "georchestra.gateway.security.ldap.basic1.url:" //
        ).run(context -> {
            assertThat(context).hasNotFailed();
        });
    }

    @Test
    void validates_basic_and_extended_baseDn_is_mandatory() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: " //
                // Georchestra extended LDAP config
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: " //
        ).run(context -> {
            assertThat(context).getFailure()//
                    .hasStackTraceContaining("LDAP base DN is required")//
                    .hasStackTraceContaining("ldap.[ldap1].baseDn").hasStackTraceContaining("ldap.[extended1].baseDn");
        });
    }

    @Test
    void validates_basic_and_extended_users_rdn_is_mandatory() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: " //
                // Georchestra extended LDAP config
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.rdn: " //
        ).run(context -> {
            assertThat(context).getFailure()//
                    .hasStackTraceContaining("LDAP users RDN (Relative Distinguished Name) is required")//
                    .hasStackTraceContaining("ldap.[ldap1].users.rdn")//
                    .hasStackTraceContaining("ldap.[extended1].users.rdn");
        });
    }

    @Test
    void validates_basic_and_extended_users_searchFilter_is_mandatory() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: " //
                // Georchestra extended LDAP config
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.searchFilter: " //
        ).run(context -> {
            assertThat(context).getFailure()//
                    .hasStackTraceContaining("LDAP users search filter is required for standard LDAP configurations")//
                    .hasStackTraceContaining("ldap.[ldap1].users.searchFilter")//
                    .hasStackTraceContaining("ldap.[extended1].users.searchFilter");
        });
    }

    @Test
    void validates_basic_and_extended_roles_rdn_is_mandatory() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: " //
                // Georchestra extended LDAP config
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.extended1.roles.rdn: " //
        ).run(context -> {
            assertThat(context).getFailure()//
                    .hasStackTraceContaining("Roles Relative Distinguished Name is required")//
                    .hasStackTraceContaining("ldap.[ldap1].roles.rdn")//
                    .hasStackTraceContaining("ldap.[extended1].roles.rdn");
        });
    }

    @Test
    void validates_basic_and_extended_roles_searchFilter_is_mandatory() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ldap1.roles.searchFilter: " //
                // Georchestra extended LDAP config
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.extended1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.extended1.roles.searchFilter: "//
        ).run(context -> {
            assertThat(context).getFailure()//
                    .hasStackTraceContaining("Roles search filter is required")//
                    .hasStackTraceContaining("ldap.[ldap1].roles.searchFilter")//
                    .hasStackTraceContaining("ldap.[extended1].roles.searchFilter");
        });
    }

    @Test
    void validates_extended_orgs_rdn_is_mandatory() {
        runner.withPropertyValues(""//
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.extended1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.extended1.roles.searchFilter: " //
                , "georchestra.gateway.security.ldap.extended1.orgs.rdn: " //
        ).run(context -> {
            assertThat(context).getFailure()//
                    .hasStackTraceContaining("Organizations search base RDN is required if 'extended' is true")//
                    .hasStackTraceContaining("ldap.[extended1].orgs.rdn");
        });
    }

    @Test
    void valid_single_config_basic() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ldap1.roles.searchFilter: (member={0})" //
        ).run(context -> {
            assertThat(context).hasNotFailed();
            GeorchestraGatewaySecurityConfigProperties config = context
                    .getBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(config.simpleEnabled()).hasSize(1);
            assertThat(config.extendedEnabled()).isEmpty();

            LdapServerConfig basic = config.simpleEnabled().getFirst();
            assertThat(basic).hasFieldOrPropertyWithValue("name", "ldap1");
            assertThat(basic).hasFieldOrPropertyWithValue("enabled", true);
            assertThat(basic).hasFieldOrPropertyWithValue("url", "ldap://ldap1.test.com:839");
            assertThat(basic).hasFieldOrPropertyWithValue("baseDn", "dc=georchestra,dc=org");
            assertThat(basic).hasFieldOrPropertyWithValue("usersRdn", "ou=users,dc=georchestra,dc=org");
            assertThat(basic).hasFieldOrPropertyWithValue("usersSearchFilter", "(uid={0})");
            assertThat(basic).hasFieldOrPropertyWithValue("rolesRdn", "ou=roles");
            assertThat(basic).hasFieldOrPropertyWithValue("rolesSearchFilter", "(member={0})");
        });
    }

    @Test
    void valid_single_config_extended() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.extended: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ldap1.roles.searchFilter: (member={0})" //
                , "georchestra.gateway.security.ldap.ldap1.orgs.rdn: ou=orgs" //
        ).run(context -> {
            assertThat(context).hasNotFailed();
            GeorchestraGatewaySecurityConfigProperties config = context
                    .getBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(config.simpleEnabled()).isEmpty();
            assertThat(config.extendedEnabled()).hasSize(1);

            ExtendedLdapConfig extended = config.extendedEnabled().getFirst();
            assertThat(extended).hasFieldOrPropertyWithValue("name", "ldap1");
            assertThat(extended).hasFieldOrPropertyWithValue("enabled", true);
            assertThat(extended).hasFieldOrPropertyWithValue("url", "ldap://ldap1.test.com:839");
            assertThat(extended).hasFieldOrPropertyWithValue("baseDn", "dc=georchestra,dc=org");
            assertThat(extended).hasFieldOrPropertyWithValue("usersRdn", "ou=users,dc=georchestra,dc=org");
            assertThat(extended).hasFieldOrPropertyWithValue("usersSearchFilter", "(uid={0})");
            assertThat(extended).hasFieldOrPropertyWithValue("rolesRdn", "ou=roles");
            assertThat(extended).hasFieldOrPropertyWithValue("rolesSearchFilter", "(member={0})");
            assertThat(extended).hasFieldOrPropertyWithValue("orgsRdn", "ou=orgs");
        });
    }

    @Test
    void valid_multiple_configs() {
        runner.withPropertyValues(""//
        // Basic LDAP config
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ldap1.roles.searchFilter: (member={0})" //
                // Georchestra extended LDAP config
                , "georchestra.gateway.security.ldap.extended1.enabled: true" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                , "georchestra.gateway.security.ldap.extended1.url: ldap://ldap2.test.com:839" //
                , "georchestra.gateway.security.ldap.extended1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.extended1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.extended1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.extended1.roles.searchFilter: (member={0})" //
                , "georchestra.gateway.security.ldap.extended1.orgs.rdn: ou=orgs" //
                // minimal AD config (no users.searchFilter)
                , "georchestra.gateway.security.ldap.ad1.enabled: true" //
                , "georchestra.gateway.security.ldap.ad1.activeDirectory: true" //
                , "georchestra.gateway.security.ldap.ad1.url: ldap://ad1.test.com:839" //
                , "georchestra.gateway.security.ldap.ad1.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.ad1.users.rdn: ou=users,dc=tes,dc=com" //
                , "georchestra.gateway.security.ldap.ad1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ad1.roles.searchFilter: (member={0})" //
                // full AD config
                , "georchestra.gateway.security.ldap.ad2.enabled: true" //
                , "georchestra.gateway.security.ldap.ad2.activeDirectory: true" //
                , "georchestra.gateway.security.ldap.ad2.url: ldap://ad2.test.com:839" //
                , "georchestra.gateway.security.ldap.ad2.baseDn: dc=test,dc=com" //
                , "georchestra.gateway.security.ldap.ad2.users.rdn: ou=users,dc=tes,dc=com" //
                ,
                "georchestra.gateway.security.ldap.ad2.users.searchFilter: (&(objectClass=user)(userPrincipalName={0}))" //
                , "georchestra.gateway.security.ldap.ad2.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ad2.roles.searchFilter: (member={0})" //
        ).run(context -> {
            assertThat(context).hasNotFailed();

            GeorchestraGatewaySecurityConfigProperties config = context
                    .getBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(config.simpleEnabled()).hasSize(3);
            assertThat(config.extendedEnabled()).hasSize(1);
        });
    }
}
