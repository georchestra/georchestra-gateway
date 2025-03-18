/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.gateway.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Gateway predicate factory that allows matching by HTTP request query string
 * parameters using {@link Pattern Java regular expressions} for both parameter
 * name and value.
 *
 * <p>
 * This predicate factory is similar to the {@link QueryRoutePredicateFactory}
 * but besides allowing regular expressions to match a parameter value, also
 * allows to match the parameter name through a regex.
 *
 * <p>
 * Just like with {@link QueryRoutePredicateFactory}, the "value" regular
 * expression is optional. If not given, the test will be performed only against
 * the query parameter names. If a value regular expression is present, though,
 * the evaluation will be performed against the values of the first parameter
 * that matches the name regex.
 *
 * <p>
 * Sample usage: the following route configuration example uses a
 * {@code RegExpQuery} predicate to match the {@code service} query parameter
 * name and {@code wfs} value in a case insensitive fashion.
 *
 * <pre>
 * <code>
 * spring:
 *  cloud:
 *   gateway:
 *    routes:
 *     - id: wfs_ows
 *       uri: lb://wfs-service
 *       predicates:
 *       - RegExpQuery=(?i:service),(?i:wfs)
 * </code>
 * </pre>
 */
public class RegExpQueryRoutePredicateFactory
        extends AbstractRoutePredicateFactory<RegExpQueryRoutePredicateFactory.Config> {

    /** HTTP request query parameter regexp key. */
    public static final String PARAM_KEY = "paramRegexp";

    /** HTTP request query parameter value regexp key. */
    public static final String VALUE_KEY = "valueRegexp";

    /**
     * Constructs a new instance of {@link RegExpQueryRoutePredicateFactory}.
     */
    public RegExpQueryRoutePredicateFactory() {
        super(Config.class);
    }

    /**
     * Returns the order of the shortcut fields.
     *
     * @return the field order list.
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList(PARAM_KEY, VALUE_KEY);
    }

    /**
     * Applies the given configuration to create a {@link GatewayPredicate}.
     *
     * @param config the configuration to apply.
     * @return a {@link GatewayPredicate} based on the provided config.
     */
    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return new RegExpQueryRoutePredicate(config);
    }

    /**
     * A {@link GatewayPredicate} implementation for matching query parameters based
     * on regular expressions.
     */
    @RequiredArgsConstructor
    private static class RegExpQueryRoutePredicate implements GatewayPredicate {
        private final @NonNull Config config;

        /**
         * Tests if the given exchange matches the predicate based on the configured
         * regular expressions.
         *
         * @param exchange the exchange to test.
         * @return true if the predicate matches the exchange, false otherwise.
         */
        @Override
        public boolean test(ServerWebExchange exchange) {
            final String paramRegexp = config.getParamRegexp();
            final String valueRegexp = config.getValueRegexp();

            boolean matchNameOnly = !StringUtils.hasText(config.getValueRegexp());
            Optional<String> paramName = findParameterName(paramRegexp, exchange);
            boolean paramNameMatches = paramName.isPresent();
            if (matchNameOnly) {
                return paramNameMatches;
            }
            return paramNameMatches && paramValueMatches(paramName.get(), valueRegexp, exchange);
        }

        /**
         * Provides a string representation of this predicate.
         *
         * @return a string describing this predicate.
         */
        @Override
        public String toString() {
            return "Query: param regexp='%s' value regexp='%s'".formatted(config.getParamRegexp(),
                    config.getValueRegexp());
        }
    }

    /**
     * Finds the first query parameter that matches the provided regular expression.
     *
     * @param regex    the regular expression to match the parameter name.
     * @param exchange the exchange containing the request.
     * @return an optional containing the parameter name if a match is found, or
     *         empty otherwise.
     */
    static Optional<String> findParameterName(@NonNull String regex, ServerWebExchange exchange) {
        Set<String> parameterNames = exchange.getRequest().getQueryParams().keySet();
        return parameterNames.stream().filter(name -> name.matches(regex)).findFirst();
    }

    /**
     * Checks if the value of the query parameter matches the provided regular
     * expression.
     *
     * @param paramName  the name of the parameter.
     * @param valueRegEx the regular expression to match the parameter value.
     * @param exchange   the exchange containing the request.
     * @return true if a matching value is found, false otherwise.
     */
    static boolean paramValueMatches(@NonNull String paramName, @NonNull String valueRegEx,
            ServerWebExchange exchange) {
        List<String> values = exchange.getRequest().getQueryParams().get(paramName);
        return values != null && values.stream().anyMatch(v -> v != null && v.matches(valueRegEx));
    }

    /**
     * Configuration class for the {@link RegExpQueryRoutePredicateFactory}.
     */
    @Data
    @Accessors(chain = true)
    @Validated
    public static class Config {

        /** The regular expression for the query parameter name. */
        @NotEmpty
        private String paramRegexp;

        /** The regular expression for the query parameter value (optional). */
        private String valueRegexp;
    }
}
