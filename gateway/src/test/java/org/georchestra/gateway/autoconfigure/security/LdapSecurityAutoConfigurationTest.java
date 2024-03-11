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

package org.georchestra.gateway.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.georchestra.gateway.security.ldap.LdapAuthenticationConfiguration;
import org.georchestra.gateway.security.ldap.LdapAuthenticationConfiguration.LDAPAuthenticationCustomizer;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerAdapter;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;

/**
 * Assert context contributions of {@link LdapSecurityAutoConfiguration} /
 * {@link LdapAuthenticationConfiguration}
 *
 */
class LdapSecurityAutoConfigurationTest {
    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LdapSecurityAutoConfiguration.class));

    @Test
    void testConditionalOnLdapEnabled_No_LdapConfigs() {
        testDisabled(runner);
    }

    @Test
    void testConditionalOnLdapEnabled_No_LdapConfigs_enabled() {
        runner = runner.withPropertyValues(""//
        // one disabled basic ldap config
                , "georchestra.gateway.security.ldap.default.enabled: false" //
                // one disabled extended ldap config
                , "georchestra.gateway.security.ldap.extended1.enabled: false" //
                , "georchestra.gateway.security.ldap.extended1.extended: true" //
                // one disabled active directory config
                , "georchestra.gateway.security.ldap.ad1.enabled: false" //
                , "georchestra.gateway.security.ldap.ad1.activeDirectory: true" //
        );

        testDisabled(runner);
    }

    private void testDisabled(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(context).doesNotHaveBean(LDAPAuthenticationCustomizer.class);
            assertThat(context).doesNotHaveBean(AuthenticationWebFilter.class);
            assertThat(context).doesNotHaveBean(ReactiveAuthenticationManager.class);
        });
    }

    @Test
    void testConditionalOnLdapEnabled_triggers_with_basic_ldap_config() {
        runner = runner.withPropertyValues(""//
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ldap1.roles.searchFilter: (member={0})" //
        );

        testEnabled(runner);
    }

    @Test
    void testConditionalOnLdapEnabled_triggers_with_extended_ldap_config() {
        runner = runner.withPropertyValues(""//
                , "georchestra.gateway.security.ldap.ldap1.enabled: true" //
                , "georchestra.gateway.security.ldap.ldap1.extended: true" //
                , "georchestra.gateway.security.ldap.ldap1.url: ldap://ldap1.test.com:839" //
                , "georchestra.gateway.security.ldap.ldap1.baseDn: dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.rdn: ou=users,dc=georchestra,dc=org" //
                , "georchestra.gateway.security.ldap.ldap1.users.searchFilter: (uid={0})" //
                , "georchestra.gateway.security.ldap.ldap1.roles.rdn: ou=roles" //
                , "georchestra.gateway.security.ldap.ldap1.roles.searchFilter: (member={0})" //
                , "georchestra.gateway.security.ldap.ldap1.orgs.rdn: ou=orgs" //
        );

        testEnabled(runner);
    }

    private void testEnabled(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GeorchestraGatewaySecurityConfigProperties.class);
            assertThat(context).hasSingleBean(LDAPAuthenticationCustomizer.class);
            assertThat(context).hasSingleBean(AuthenticationWebFilter.class);

            assertThat(context).hasBean("ldapAuthenticationManager");
            assertThat(context.getBean("ldapAuthenticationManager"))
                    .isInstanceOf(ReactiveAuthenticationManagerAdapter.class);
        });
    }
}
