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

import static org.springframework.validation.ValidationUtils.rejectIfEmptyOrWhitespace;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties.Server;
import org.springframework.validation.Errors;

import lombok.extern.slf4j.Slf4j;

/**
 * Validator for LDAP configuration properties.
 * <p>
 * This class ensures that necessary LDAP configuration fields are correctly
 * defined based on the type of LDAP authentication used.
 * </p>
 *
 * <p>
 * Validation rules include:
 * </p>
 * <ul>
 * <li>Ensuring required properties such as URL and base DN are provided.</li>
 * <li>Applying additional validation rules for extended LDAP
 * configurations.</li>
 * <li>Ensuring Active Directory configurations do not include unnecessary
 * properties.</li>
 * </ul>
 */
@Slf4j(topic = "org.georchestra.gateway.security.ldap")
public class LdapConfigPropertiesValidations {

    /**
     * Validates an LDAP configuration.
     *
     * @param name   the LDAP configuration name
     * @param config the {@link Server} configuration containing LDAP settings
     * @param errors the {@link Errors} object for capturing validation errors
     */
    public void validate(String name, Server config, Errors errors) {
        if (!config.isEnabled()) {
            log.debug("Ignoring validation of LDAP config '{}', enabled = false", name);
            return;
        }

        // Ensure the LDAP URL is defined
        final String url = "ldap.[%s].url".formatted(name);
        rejectIfEmptyOrWhitespace(errors, url, "", "LDAP URL is required (e.g., ldap://my.ldap.com:389)");

        // Validate base LDAP configuration
        validateSimpleLdap(name, config, errors);

        // Validate geOrchestra-specific extensions if enabled
        if (config.isExtended()) {
            validateGeorchestraExtensions(name, config, errors);
        }

        // Apply specific validation rules for Active Directory configurations
        if (config.isActiveDirectory()) {
            validateActiveDirectory(name, config, errors);
        } else {
            validateUsersSearchFilterMandatory(name, errors);
        }
    }

    /**
     * Validates essential LDAP properties for a standard LDAP configuration.
     *
     * @param name   the LDAP configuration name
     * @param config the LDAP server configuration
     * @param errors the validation error object
     */
    private void validateSimpleLdap(String name, Server config, Errors errors) {
        rejectIfEmptyOrWhitespace(errors, "ldap.[%s].baseDn".formatted(name), "",
                "LDAP base DN is required (e.g., dc=georchestra,dc=org)");

        rejectIfEmptyOrWhitespace(errors, "ldap.[%s].users.rdn".formatted(name), "",
                "LDAP users RDN (Relative Distinguished Name) is required (e.g., ou=users,dc=georchestra,dc=org)");

        rejectIfEmptyOrWhitespace(errors, "ldap.[%s].roles.rdn".formatted(name), "",
                "Roles Relative Distinguished Name is required (e.g., ou=roles)");

        rejectIfEmptyOrWhitespace(errors, "ldap.[%s].roles.searchFilter".formatted(name), "",
                "Roles search filter is required (e.g., (member={0}))");
    }

    /**
     * Ensures that the user search filter is mandatory for non-Active Directory
     * configurations.
     *
     * @param name   the LDAP configuration name
     * @param errors the validation error object
     */
    private void validateUsersSearchFilterMandatory(String name, Errors errors) {
        rejectIfEmptyOrWhitespace(errors, "ldap.[%s].users.searchFilter".formatted(name), "",
                "LDAP users search filter is required for standard LDAP configurations (e.g., (uid={0})), "
                        + "but optional for Active Directory (e.g., (&(objectClass=user)(userPrincipalName={0})))");
    }

    /**
     * Validates geOrchestra-specific LDAP extensions.
     *
     * @param name   the LDAP configuration name
     * @param config the LDAP server configuration
     * @param errors the validation error object
     */
    private void validateGeorchestraExtensions(String name, Server config, Errors errors) {
        rejectIfEmptyOrWhitespace(errors, "ldap.[%s].orgs.rdn".formatted(name), "",
                "Organizations search base RDN is required if 'extended' is true (e.g., ou=orgs)");
    }

    /**
     * Ensures that Active Directory configurations do not contain unused
     * properties.
     *
     * @param name   the LDAP configuration name
     * @param config the LDAP server configuration
     * @param errors the validation error object
     */
    private void validateActiveDirectory(String name, Server config, Errors errors) {
        warnUnusedByActiveDirectory(name, "orgs", config.getOrgs());
    }

    /**
     * Logs a warning if an Active Directory configuration contains an unused
     * property.
     *
     * @param name     the LDAP configuration name
     * @param property the property name
     * @param value    the property value
     */
    private void warnUnusedByActiveDirectory(String name, String property, Object value) {
        if (value != null) {
            log.warn("Found config property 'org.georchestra.gateway.security.ldap.{}.{}', "
                    + "but it is not used by Active Directory configurations.", name, property);
        }
    }
}
