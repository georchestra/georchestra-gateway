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
package org.georchestra.gateway.security.oauth2;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.security.oauth2.client")
public class ExtendedOAuth2ClientProperties implements InitializingBean {

    private final Map<String, Provider> provider = new HashMap<>();

    public Map<String, Provider> getProvider() {
        return this.provider;
    }

    public static class Provider extends OAuth2ClientProperties.Provider {
        private String endSessionUri;
        private Boolean pivot;
        private Map<String, Object> claims;

        public String getEndSessionUri() {
            return this.endSessionUri;
        }

        public void setEndSessionUri(String endSessionUri) {
            this.endSessionUri = endSessionUri;
        }

        public Boolean getPivot() {
            return this.pivot;
        }

        public void setPivot(Boolean pivot) {
            this.pivot = pivot;
        }

        public Map<String, Object> getClaims() {
            return this.claims;
        }

        public void setClaims(Map<String, Object> claims) {
            this.claims = claims;
        }
    }

    @Override
    public void afterPropertiesSet() {
    }
}
