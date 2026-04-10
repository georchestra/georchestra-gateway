/*
 * Copyright (C) 2024 by the geOrchestra PSC
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
package org.georchestra.gateway.security.sirene;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for the SIRENE API integration.
 *
 * <p>
 * Example configuration in {@code application.yml}:
 * </p>
 *
 * <pre>
 * {@code
 * georchestra:
 *   gateway:
 *     security:
 *       sirene:
 *         enabled: true
 *         api-key: ${SIRENE_API_KEY}
 *         base-url: https://api.insee.fr/entreprises/sirene/V3.11
 * }
 * </pre>
 */
@ConfigurationProperties(prefix = "georchestra.gateway.security.sirene")
@Data
public class SireneApiConfigProperties {

    /**
     * Whether the SIRENE API integration is enabled.
     */
    private boolean enabled = false;

    /**
     * The API key (Bearer token) for authenticating with the INSEE SIRENE API. Free
     * keys can be obtained from https://portail-api.insee.fr/.
     */
    private String apiKey;

    /**
     * The base URL for the SIRENE API.
     */
    private String baseUrl = "https://api.insee.fr/api-sirene/3.11";
}
