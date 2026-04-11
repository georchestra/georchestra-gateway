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
package org.georchestra.gateway.autoconfigure.sirene;

import org.georchestra.gateway.orgresolvers.OrganizationNameResolver;
import org.georchestra.gateway.security.sirene.SireneApiConfigProperties;
import org.georchestra.gateway.security.sirene.SireneOrganizationNameResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for the SIRENE API organization name resolver.
 * <p>
 * This configuration is only active when
 * {@code georchestra.gateway.security.sirene.enabled=true}.
 * </p>
 * <p>
 * It registers a {@link SireneOrganizationNameResolver} bean backed by a
 * {@link RestClient} configured with the SIRENE API base URL and authentication
 * key.
 * </p>
 *
 * @see SireneApiConfigProperties
 * @see SireneOrganizationNameResolver
 */
@AutoConfiguration
@ConditionalOnProperty(name = "georchestra.gateway.security.sirene.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(SireneApiConfigProperties.class)
@Slf4j(topic = "org.georchestra.gateway.autoconfigure.sirene")
public class SireneAutoConfiguration {

    @PostConstruct
    public void log() {
        log.info("SIRENE API organization name resolver enabled");
    }

    /**
     * Creates a {@link RestClient} pre-configured for the INSEE SIRENE API.
     *
     * @param properties the SIRENE API configuration properties
     * @return a configured {@link RestClient} instance
     */
    @Bean("sireneRestClient")
    RestClient sireneRestClient(SireneApiConfigProperties properties) {
        log.info("Configuring SIRENE API RestClient with base URL: {}", properties.getBaseUrl());
        // Using JdkClientHttpRequestFactory as reactive Webclient doesn't support blocking requests
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).baseUrl(properties.getBaseUrl())
                .defaultHeader("X-INSEE-Api-Key-Integration", properties.getApiKey())
                .defaultHeader("Accept", "application/json").build();
    }

    /**
     * Creates the {@link OrganizationNameResolver} backed by the SIRENE API.
     *
     * @param restClient the RestClient configured for the SIRENE API
     * @return an {@link OrganizationNameResolver} instance
     */
    @Bean
    OrganizationNameResolver sireneOrganizationNameResolver(@Qualifier("sireneRestClient") RestClient restClient) {
        return new SireneOrganizationNameResolver(restClient);
    }
}
