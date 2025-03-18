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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.autoconfigure.app;

import org.georchestra.gateway.handler.predicate.QueryParamRoutePredicateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for custom route predicate factories in geOrchestra
 * Gateway.
 * <p>
 * This configuration registers custom predicate factories that extend the
 * routing capabilities of Spring Cloud Gateway.
 * </p>
 */
@AutoConfiguration
public class RoutePredicateFactoriesAutoConfiguration {

    /**
     * Registers a route predicate factory that allows matching requests based on
     * query parameters.
     * <p>
     * This predicate enables routing decisions based on the presence and values of
     * query parameters in incoming requests.
     * </p>
     *
     * @return an instance of {@link QueryParamRoutePredicateFactory}
     */
    @Bean
    QueryParamRoutePredicateFactory queryParamRoutePredicateFactory() {
        return new QueryParamRoutePredicateFactory();
    }
}
