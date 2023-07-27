/*
 * Copyright (C) 2021 by the geOrchestra PSC
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
package org.georchestra.gateway.autoconfigure.app;

import org.georchestra.gateway.filter.global.AccessLogFilter;
import org.georchestra.gateway.filter.global.AccessLogFilterConfig;
import org.georchestra.gateway.filter.global.RequestIdGlobalFilter;
import org.georchestra.gateway.filter.global.ResolveTargetGlobalFilter;
import org.georchestra.gateway.filter.headers.HeaderFiltersConfiguration;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(GatewayAutoConfiguration.class)
@Import(HeaderFiltersConfiguration.class)
@EnableConfigurationProperties({ GatewayConfigProperties.class, AccessLogFilterConfig.class })
public class FiltersAutoConfiguration {

    /**
     * {@link GlobalFilter} to {@link GeorchestraTargetConfig#setTarget save) the
     * matched Route's GeorchestraTargetConfig for each HTTP request-response
     * interaction before other filters are applied.
     */
    public @Bean ResolveTargetGlobalFilter resolveTargetWebFilter(GatewayConfigProperties config) {
        return new ResolveTargetGlobalFilter(config);
    }

    @Bean
    RequestIdGlobalFilter requestIdGlobalFilter() {
        return new RequestIdGlobalFilter();
    }

    @Bean
    AccessLogFilter accessLogGlobalFilter(AccessLogFilterConfig config) {
        return new AccessLogFilter(config);
    }
}
