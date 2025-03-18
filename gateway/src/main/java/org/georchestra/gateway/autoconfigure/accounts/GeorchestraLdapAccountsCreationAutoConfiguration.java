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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.autoconfigure.accounts;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.georchestra.gateway.accounts.admin.ldap.GeorchestraLdapAccountManagementConfiguration;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapAuthenticationConfiguration;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Auto-configuration for LDAP account creation in geOrchestra.
 * <p>
 * This configuration enables automatic LDAP account creation when the required
 * conditions are met:
 * <ul>
 * <li>{@code georchestra.gateway.security.create-non-existing-users-in-l-d-a-p}
 * is set to {@code true}.</li>
 * <li>An extended LDAP configuration is present.</li>
 * </ul>
 * </p>
 *
 * <p>
 * If no extended LDAP configuration is found, the application will fail to
 * start.
 * </p>
 *
 * <p>
 * This class imports additional configurations:
 * <ul>
 * <li>{@link GeorchestraLdapAccountManagementConfiguration} - Manages
 * LDAP-based user accounts.</li>
 * <li>{@link ExtendedLdapAuthenticationConfiguration} - Provides extended LDAP
 * authentication support.</li>
 * </ul>
 * </p>
 *
 * @see ConditionalOnCreateLdapAccounts
 * @see GeorchestraLdapAccountManagementConfiguration
 * @see ExtendedLdapAuthenticationConfiguration
 */
@AutoConfiguration
@ConditionalOnCreateLdapAccounts
@Import({ GeorchestraLdapAccountManagementConfiguration.class, ExtendedLdapAuthenticationConfiguration.class })
@RequiredArgsConstructor
public class GeorchestraLdapAccountsCreationAutoConfiguration {

    /** The list of extended LDAP configurations required for account creation. */
    @NonNull
    private final List<ExtendedLdapConfig> configs;

    /**
     * Ensures that an extended LDAP configuration is available.
     * <p>
     * If no extended LDAP configurations are present, this method throws an
     * {@link IllegalStateException} to prevent startup with invalid settings.
     * </p>
     */
    @PostConstruct
    void failIfNoExtendedLdapConfigs() {
        if (configs.isEmpty()) {
            throw new IllegalStateException("LDAP account creation requires an extended LDAP configuration");
        }
    }
}
