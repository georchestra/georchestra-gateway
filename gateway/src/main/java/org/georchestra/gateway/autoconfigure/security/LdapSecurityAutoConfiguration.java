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
package org.georchestra.gateway.autoconfigure.security;

import org.georchestra.gateway.security.ldap.LdapAuthenticationConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for LDAP-based authentication in geOrchestra.
 * <p>
 * This configuration enables LDAP authentication when at least one LDAP data
 * source is enabled and the required dependencies are available.
 * </p>
 *
 * <p>
 * It imports {@link LdapAuthenticationConfiguration}, which sets up the
 * necessary beans for LDAP authentication.
 * </p>
 *
 * <p>
 * Upon initialization, this configuration logs a message indicating that LDAP
 * authentication has been enabled.
 * </p>
 *
 * @see LdapAuthenticationConfiguration
 */
@AutoConfiguration
@ConditionalOnLdapEnabled
@Import(LdapAuthenticationConfiguration.class)
@Slf4j(topic = "org.georchestra.gateway.autoconfigure.security")
public class LdapSecurityAutoConfiguration {

    /**
     * Logs a message when LDAP security is enabled.
     */
    @PostConstruct
    public void log() {
        log.info("georchestra LDAP security enabled");
    }
}
