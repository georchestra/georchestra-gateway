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
package org.georchestra.gateway.handler.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * A route predicate factory that evaluates whether an HTTP request contains a
 * specified query parameter.
 * <p>
 * This predicate allows routing based on the presence of a query parameter in
 * the request URI.
 * </p>
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * <code>
 * - id: example-route
 *   uri: http://example.com
 *   predicates:
 *    - QueryParam=token
 * </code>
 * </pre>
 * <p>
 * The above configuration will route requests to {@code http://example.com}
 * only if the query string contains the parameter {@code token}.
 * </p>
 */
public class QueryParamRoutePredicateFactory
        extends AbstractRoutePredicateFactory<QueryParamRoutePredicateFactory.Config> {

    public static final String PARAM_KEY = "param";

    /**
     * Constructs a new {@code QueryParamRoutePredicateFactory}.
     */
    public QueryParamRoutePredicateFactory() {
        super(QueryParamRoutePredicateFactory.Config.class);
    }

    /**
     * Specifies the order of the fields when using the shortcut configuration.
     * 
     * @return a list containing the expected field order
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList(PARAM_KEY);
    }

    /**
     * Applies the predicate filter to check for the presence of the configured
     * query parameter in the request.
     * 
     * @param config the predicate configuration containing the query parameter name
     * @return a {@link Predicate} that tests whether the request contains the
     *         specified query parameter
     */
    @Override
    public Predicate<ServerWebExchange> apply(QueryParamRoutePredicateFactory.Config config) {
        return new GatewayPredicate() {
            @Override
            public boolean test(ServerWebExchange exchange) {
                return exchange.getRequest().getQueryParams().containsKey(config.param);
            }

            @Override
            public String toString() {
                return "Query: param=%s".formatted(config.getParam());
            }
        };
    }

    /**
     * Configuration class for {@code QueryParamRoutePredicateFactory}.
     */
    @Validated
    public static class Config {

        @NotEmpty
        private String param;

        /**
         * Retrieves the configured query parameter name.
         * 
         * @return the name of the query parameter
         */
        public String getParam() {
            return param;
        }

        /**
         * Sets the query parameter name to check for.
         * 
         * @param param the query parameter name
         * @return this {@code Config} instance for method chaining
         */
        public QueryParamRoutePredicateFactory.Config setParam(String param) {
            this.param = param;
            return this;
        }
    }
}
