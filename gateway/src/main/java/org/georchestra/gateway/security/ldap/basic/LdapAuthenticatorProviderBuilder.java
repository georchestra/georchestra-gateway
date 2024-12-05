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
package org.georchestra.gateway.security.ldap.basic;

import static java.util.Objects.requireNonNull;

import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.security.ldap.NoPasswordLdapUserDetailsMapper;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapAuthenticationProvider;
import org.georchestra.gateway.security.ldap.extended.ExtendedPasswordPolicyAwareContextSource;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;

import lombok.Setter;
import lombok.experimental.Accessors;

/**
 */
@Accessors(chain = true, fluent = true)
public class LdapAuthenticatorProviderBuilder {

    private @Setter String url;
    private @Setter String baseDn;

    private @Setter String userSearchBase;
    private @Setter String userSearchFilter;

    private @Setter String rolesSearchBase;
    private @Setter String rolesSearchFilter;

    private @Setter String adminDn;
    private @Setter String adminPassword;

    private @Setter AccountDao accountDao;

    // null = all atts, empty == none
    private @Setter String[] returningAttributes = null;

    public ExtendedLdapAuthenticationProvider build() {
        requireNonNull(url, "url is not set");
        requireNonNull(baseDn, "baseDn is not set");
        requireNonNull(userSearchBase, "userSearchBase is not set");
        requireNonNull(userSearchFilter, "userSearchFilter is not set");
        requireNonNull(rolesSearchBase, "rolesSearchBase is not set");
        requireNonNull(rolesSearchFilter, "rolesSearchFilter is not set");

        final ExtendedPasswordPolicyAwareContextSource source = contextSource();
        final BindAuthenticator authenticator = ldapAuthenticator(source);
        final DefaultLdapAuthoritiesPopulator rolesPopulator = ldapAuthoritiesPopulator(source);
        ExtendedLdapAuthenticationProvider provider = new ExtendedLdapAuthenticationProvider(authenticator,
                rolesPopulator);

        final GrantedAuthoritiesMapper rolesMapper = ldapAuthoritiesMapper();
        provider.setAuthoritiesMapper(rolesMapper);
        provider.setUserDetailsContextMapper(new NoPasswordLdapUserDetailsMapper());
        provider.setAccountDao(accountDao);
        return provider;
    }

    private BindAuthenticator ldapAuthenticator(BaseLdapPathContextSource contextSource) {
        FilterBasedLdapUserSearch search = new FilterBasedLdapUserSearch(userSearchBase, userSearchFilter,
                contextSource);

        search.setReturningAttributes(returningAttributes);

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(search);
        authenticator.afterPropertiesSet();
        return authenticator;
    }

    private ExtendedPasswordPolicyAwareContextSource contextSource() {
        ExtendedPasswordPolicyAwareContextSource context = new ExtendedPasswordPolicyAwareContextSource(url);
        context.setBase(baseDn);
        if (adminDn != null) {
            context.setUserDn(adminDn);
            context.setPassword(adminPassword);
        }
        context.afterPropertiesSet();
        return context;
    }

    private GrantedAuthoritiesMapper ldapAuthoritiesMapper() {
        return new SimpleAuthorityMapper();
    }

    private DefaultLdapAuthoritiesPopulator ldapAuthoritiesPopulator(BaseLdapPathContextSource contextSource) {
        DefaultLdapAuthoritiesPopulator authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource,
                rolesSearchBase);
        authoritiesPopulator.setGroupSearchFilter(rolesSearchFilter);
        return authoritiesPopulator;
    }
}
