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
package org.georchestra.gateway.security;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.Valid;

import org.georchestra.gateway.security.ldap.LdapConfigBuilder;
import org.georchestra.gateway.security.ldap.LdapConfigPropertiesValidations;
import org.georchestra.gateway.security.ldap.basic.LdapServerConfig;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.Generated;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Config properties, usually loaded from georchestra datadir's
 * {@literal default.properties}.
 * <p>
 * e.g.:
 * 
 * <pre>
 *{@code 
 * ldapHost=localhost
 * ldapPort=389
 * ldapScheme=ldap
 * ldapBaseDn=dc=georchestra,dc=org
 * ldapUsersRdn=ou=users
 * ldapRolesRdn=ou=roles
 * ldapOrgsRdn=ou=orgs
 * }
 * </pre>
 */
@Data
@Generated
@Validated
@Accessors(chain = true)
@ConfigurationProperties(prefix = "georchestra.gateway.security")
public class GeorchestraGatewaySecurityConfigProperties implements Validator {

    private boolean createNonExistingUsersInLDAP = true;

    private String defaultOrganization = "";

    @Valid
    private Map<String, Server> ldap = Map.of();

    @Generated
    public static @Data class Server {

        boolean enabled;

        /**
         * Whether the LDAP authentication source shall use georchestra-specific
         * extensions. For example, when using the default OpenLDAP database with
         * additional user identity information
         */
        boolean extended;

        private String url;

        /**
         * Flag indicating the LDAP authentication end point is an Active Directory
         * service
         */
        private boolean activeDirectory;

        /**
         * Base DN of the LDAP directory Base Distinguished Name of the LDAP directory.
         * Also named root or suffix, see
         * http://www.zytrax.com/books/ldap/apd/index.html#base
         * <p>
         * For example, georchestra's default baseDn is dc=georchestra,dc=org
         */
        private String baseDn;

        /**
         * How to extract user information. Only searchFilter is used if activeDirectory
         * is true
         */
        private Users users;

        /**
         * How to extract role information, un-used for Active Directory
         */
        private Roles roles;

        /**
         * How to extract Organization information, only used for OpenLDAP if extended =
         * true
         */
        private Organizations orgs;

        /**
         * The user distinguished name (principal) to use for getting authenticated
         * contexts (optional).
         */
        private String adminDn;

        /**
         * The password (credentials) to use for getting authenticated contexts
         * (optional).
         */
        private String adminPassword;
    }

    @Generated
    public static @Data @Accessors(chain = true) class Users {

        /**
         * Users RDN Relative distinguished name of the "users" LDAP organization unit.
         * E.g. if the complete name (or DN) is ou=users,dc=georchestra,dc=org, the RDN
         * is ou=users.
         */
        private String rdn;

        /**
         * Users search filter, e.g. (uid={0}) for OpenLDAP, and
         * (&(objectClass=user)(userPrincipalName={0})) for ActiveDirectory
         */
        private String searchFilter;

        /**
         * Specifies the attributes that will be returned as part of the search.
         * <p>
         * null indicates that all attributes will be returned. An empty array indicates
         * no attributes are returned.
         */
        private @Setter String[] returningAttributes;
    }

    @Generated
    public static @Data @Accessors(chain = true) class Roles {
        /**
         * Roles RDN Relative distinguished name of the "roles" LDAP organization unit.
         * E.g. if the complete name (or DN) is ou=roles,dc=georchestra,dc=org, the RDN
         * is ou=roles.
         */
        private String rdn;

        /**
         * Roles search filter. e.g. (member={0})
         */
        private String searchFilter;
    }

    @Generated
    public static @Data @Accessors(chain = true) class Organizations {

        /**
         * Organizations search base. Default: ou=orgs
         */
        private String rdn = "ou=orgs";

        /**
         * Pending organizations search base. Default: ou=pendingorgs
         */
        private String pendingRdn = "ou=pendingorgs";
    }

    public @Override boolean supports(Class<?> clazz) {
        return GeorchestraGatewaySecurityConfigProperties.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        GeorchestraGatewaySecurityConfigProperties config = (GeorchestraGatewaySecurityConfigProperties) target;
        Map<String, Server> ldap = config.getLdap();
        if (ldap == null || ldap.isEmpty()) {
            return;
        }
        LdapConfigPropertiesValidations validations = new LdapConfigPropertiesValidations();
        ldap.forEach((name, serverConfig) -> validations.validate(name, serverConfig, errors));
    }

    public List<LdapServerConfig> simpleEnabled() {
        LdapConfigBuilder builder = new LdapConfigBuilder();
        return entries()//
                .filter(e -> e.getValue().isEnabled())//
                .filter(e -> !e.getValue().isExtended())//
                .map(e -> builder.asBasicLdapConfig(e.getKey(), e.getValue()))//
                .collect(Collectors.toList());
    }

    public List<ExtendedLdapConfig> extendedEnabled() {
        LdapConfigBuilder builder = new LdapConfigBuilder();
        return entries()//
                .filter(e -> e.getValue().isEnabled())//
                .filter(e -> !e.getValue().isActiveDirectory())//
                .filter(e -> e.getValue().isExtended())//
                .map(e -> builder.asExtendedLdapConfig(e.getKey(), e.getValue()))//
                .collect(Collectors.toList());
    }

    private Stream<Entry<String, Server>> entries() {
        return ldap == null ? Stream.empty() : ldap.entrySet().stream();
    }

}
