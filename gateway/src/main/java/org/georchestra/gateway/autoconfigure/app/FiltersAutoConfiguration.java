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
import org.geoserver.cloud.gateway.filter.RouteProfileGatewayFilterFactory;
import org.geoserver.cloud.gateway.filter.StripBasePathGatewayFilterFactory;
import org.geoserver.cloud.gateway.predicate.RegExpQueryRoutePredicateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
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

    /**
     * Custom gateway predicate factory to support matching by regular expressions
     * on both name and value of query parameters
     */
    public @Bean RegExpQueryRoutePredicateFactory regExpQueryRoutePredicateFactory() {
        return new RegExpQueryRoutePredicateFactory();
    }

    /** Allows to enable routes only if a given spring profile is enabled */
    public @Bean RouteProfileGatewayFilterFactory routeProfileGatewayFilterFactory() {
        return new RouteProfileGatewayFilterFactory();
    }

    public @Bean StripBasePathGatewayFilterFactory stripBasePathGatewayFilterFactory() {
        return new StripBasePathGatewayFilterFactory();
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
