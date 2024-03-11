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
package org.georchestra.gateway.autoconfigure.accounts;

import org.georchestra.gateway.accounts.admin.ldap.GeorchestraLdapAccountManagementConfiguration;
import org.georchestra.gateway.security.ldap.extended.ExtendedLdapAuthenticationConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration @AutoConfiguration}
 * 
 * @see ConditionalOnCreateLdapAccounts
 * @see GeorchestraLdapAccountManagementConfiguration
 */
@AutoConfiguration
@ConditionalOnCreateLdapAccounts
@Import({ ExtendedLdapAuthenticationConfiguration.class, GeorchestraLdapAccountManagementConfiguration.class })
public class GeorchestraLdapAccountsCreationAutoConfiguration {
}
