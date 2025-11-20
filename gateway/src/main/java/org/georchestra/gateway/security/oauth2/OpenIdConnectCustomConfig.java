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
package org.georchestra.gateway.security.oauth2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.NonNull;

/**
 * This class allow to set a custom configuration for OpenID Connect providers.
 *
 * <p>
 * This configuration is not use to set claims or scopes. In fact, some
 * providers needs a specific behavior to works with georchestra. So, this
 * configuration allow to override general settings for a specific provider.
 *
 * For example, if you want to search a provider's user into georchestra's users
 * by email, you need to set the searchEmail parameter to true under :
 * georchestra.gateway.security.oidc.config.provider.[provider].searchEmail
 * 
 * Else, If moderatedSignup is enabled, any new user will be marked as pending and will wait for administrator approval before their account becomes active :
 * georchestra.gateway.security.oidc.config.provider.[provider].moderatedSignup
 * 
 * Note that moderatedSignup can be configured either in the datadir’s default.properties file or in the gateway configuration.
 * 
 * </p>
 * 
 * <p>
 * Example configuration in {@code application.yml}:
 * </p>
 * 
 * <pre>
 * <code>
 * georchestra:
 *   gateway:
 *     security:
 *       oidc:
 *         config:
 *           searchEmail: false
 *           provider:
 *              proconnect:
 *                  searchEmail: true
 *                  moderatedSignup: true
 *              google:
 *                  searchEmail: false
 *                  moderatedSignup: false
 * </code>
 * </pre>
 */
@ConfigurationProperties(prefix = "georchestra.gateway.security.oidc.config")
@Data
public class OpenIdConnectCustomConfig {

    private Boolean searchEmail;

    private Boolean moderatedSignup;

    private Map<String, OpenIdConnectCustomConfig> provider = new HashMap<>();

    /**
     * Return a sub {@OpenIdConnectCustomConfig} configuration for a given provider
     * name.
     * 
     * @param providerName The {@String} provider name
     * @return An {@OpenIdConnectCustomConfig} configuration according to current
     *         provider in use
     */
    public Optional<OpenIdConnectCustomConfig> getProviderConfig(@NonNull String providerName) {
        return Optional.ofNullable(provider.get(providerName));
    }

    /**
     * Determines if the user will be searched by email (false by default).
     * 
     * @param providerName provider id in use
     * @return {@Boolean} true if user will be search by email from storage
     */
    public boolean useEmail(@NonNull String providerName) {
        return getProviderConfig(providerName)
                // provider config
                .map(OpenIdConnectCustomConfig::getSearchEmail)
                // orf fallback general config
                .orElse(searchEmail != null ? searchEmail : false);
    }

    /**
     * Determines if a new user will be set as pending (false by default).
     * 
     * @param providerName provider id in use
     * @return {@Boolean} true if user will be set as pending on creation
     */
    public boolean moderatedSignup(@NonNull String providerName) {
        return getProviderConfig(providerName)
                // provider config
                .map(OpenIdConnectCustomConfig::getModeratedSignup)
                // orf fallback general config
                .orElse(moderatedSignup != null ? moderatedSignup : false);
    }
}
