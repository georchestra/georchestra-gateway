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

@ConfigurationProperties(prefix = "georchestra.gateway.security.oidc.config")
@Data
public class OpenIdConnectCustomConfig {
    private Boolean searchEmail;
    private Map<String, OpenIdConnectCustomConfig> provider = new HashMap<>();

    public Optional<OpenIdConnectCustomConfig> getProviderConfig(@NonNull String providerName) {
        return Optional.ofNullable(provider.get(providerName));
    }

    public boolean useEmail(@NonNull String providerName) {
        return getProviderConfig(providerName)
                // provider config
                .map(OpenIdConnectCustomConfig::getSearchEmail)
                // orf fallback general config
                .orElse(searchEmail != null ? searchEmail : false);
    }
}
