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
package org.georchestra.gateway.security.ldap;

import static java.lang.String.format;
import static org.springframework.validation.ValidationUtils.rejectIfEmptyOrWhitespace;

import org.georchestra.gateway.security.ldap.LdapConfigProperties.Server;
import org.georchestra.gateway.security.ldap.LdapConfigProperties.Users;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "org.georchestra.gateway.security.ldap")
class LdapConfigPropertiesValidations {

    public void validate(String name, Server config, Errors errors) {
        if (!config.isEnabled()) {
            log.debug("ignoring validation of LDAP config {}, enabled = false", name);
            return;
        }
        final String url = format("ldap.[%s].url", name);
        rejectIfEmptyOrWhitespace(errors, url, "", "LDAP url is required (e.g.: ldap://my.ldap.com:389)");

        validateIsNotExtendedAndActiveDirectory(name, config, errors);

        if (config.isActiveDirectory()) {
            validateActiveDirectory(name, config, errors);
        } else {
            validateSimpleLdap(name, config, errors);
            if (config.isExtended()) {
                validateGeorchestraExtensions(name, config, errors);
            }
        }
    }

    private void validateSimpleLdap(String name, Server config, Errors errors) {
        rejectIfEmptyOrWhitespace(errors, format("ldap.[%s].baseDn", name), "",
                "LDAP base DN is required. e.g.: dc=georchestra,dc=org");

        rejectIfEmptyOrWhitespace(errors, format("ldap.[%s].users.rdn", name), "",
                "LDAP users RDN (Relative Distinguished Name) is required. e.g.: ou=users,dc=georchestra,dc=org");

        rejectIfEmptyOrWhitespace(errors, format("ldap.[%s].users.searchFilter", name), "",
                "LDAP users searchFilter is required for regular LDAP configs. e.g.: (uid={0}), and optional for Active Directory. e.g.: (&(objectClass=user)(userPrincipalName={0}))");

        rejectIfEmptyOrWhitespace(errors, format("ldap.[%s].roles.rdn", name), "",
                "Roles Relative distinguished name is required. e.g.: ou=roles");

        rejectIfEmptyOrWhitespace(errors, format("ldap.[%s].roles.searchFilter", name), "",
                "Roles searchFilter is required. e.g.: (member={0})");
    }

    private void validateGeorchestraExtensions(String name, Server config, Errors errors) {
        rejectIfEmptyOrWhitespace(errors, format("ldap.[%s].orgs.rdn", name), "",
                "Organizations search base RDN is required if extended is true. e.g.: ou=orgs");
    }

    private void validateActiveDirectory(String name, Server config, Errors errors) {
        if (!StringUtils.hasText(config.getDomain())) {
            log.warn("ldap.{}.domain is null, it is recommended to set up a domain name (e.g. my.company.com)", name);
        }

        if (!StringUtils.hasText(config.getBaseDn())) {
            log.debug("ldap.{}.baseDn is null, will derive Active Directory rootDn from domain", name);
        }

        Users users = config.getUsers();
        String searchFilter = users == null ? null : users.getSearchFilter();
        if (!StringUtils.hasText(searchFilter)) {
            log.debug(
                    "ldap.{}.users.searchFilter is null, will use default Active Directory value: (&(objectClass=user)(userPrincipalName={0}))",
                    name);
        }
        if (users != null) {
            String rdn = users.getRdn();
            warnUnusedByActiveDirectory(name, "users.rdn", rdn);
        }
        warnUnusedByActiveDirectory(name, "roles", config.getRoles());
        warnUnusedByActiveDirectory(name, "orgs", config.getOrgs());
    }

    private void warnUnusedByActiveDirectory(String name, String property, Object value) {
        if (value != null) {
            log.warn(
                    "Found config property org.georchestra.gateway.security.ldap.{}.{} but it's not used by Active Directory",
                    name, property);
        }
    }

    private void validateIsNotExtendedAndActiveDirectory(String name, Server config, Errors errors) {
        final boolean activeDirectory = config.isActiveDirectory();
        final boolean extended = config.isExtended();
        if (activeDirectory && extended) {
            errors.rejectValue(format("ldap.[%s].extended", name), "",
                    "extended and activeDirectory are mutually exclusive");
            errors.rejectValue(format("ldap.[%s].activeDirectory", name), "",
                    "extended and activeDirectory are mutually exclusive");
        }
    }
}