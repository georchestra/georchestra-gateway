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
package org.georchestra.gateway.filter.headers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.georchestra.gateway.filter.headers.RemoveHeadersGatewayFilterFactory.RegExConfig;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link GatewayFilterFactory} to remove incoming HTTP request headers whose
 * names match a Java regular expression.
 * <p>
 * Use a {@code RemoveHeaders=<regular expression>} filter in a
 * {@code spring.cloud.gateway.routes.filters} route config to remove all
 * incoming request headers matching the regex.
 * <p>
 * Sample usage:
 * 
 * <pre>
 * <code>
 * spring:
 *   cloud:
 *    gateway:
 *      routes:
 *      - id: root
 *        uri: http://backend-service/context
 *        filters:
 *        - RemoveHeaders=(?i)(sec-.*|Authorization) 
 * </code>
 * </pre>
 * 
 */
@Slf4j(topic = "org.georchestra.gateway.filter.headers")
public class RemoveHeadersGatewayFilterFactory extends AbstractGatewayFilterFactory<RegExConfig> {

    public RemoveHeadersGatewayFilterFactory() {
        super(RegExConfig.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("regEx");
    }

    @Override
    public GatewayFilter apply(RegExConfig regexConfig) {
        return (exchange, chain) -> {
            final RegExConfig config = regexConfig;// == null ? DEFAULT_SECURITY_HEADERS_CONFIG : regexConfig;

            ServerHttpRequest request = exchange.getRequest().mutate().headers(config::removeMatching).build();
            exchange = exchange.mutate().request(request).build();

            return chain.filter(exchange);
        };
    }

    @NoArgsConstructor
    public static class RegExConfig {

        private @Getter String regEx;

        private Pattern compiled;

        public RegExConfig(String regEx) {
            setRegEx(regEx);
        }

        public void setRegEx(String regEx) {
            Objects.requireNonNull(regEx, "regular expression can't be null");
            this.regEx = regEx;
            this.compiled = Pattern.compile(regEx);
        }

        private Pattern pattern() {
            Objects.requireNonNull(compiled, "regular expression can't be null");
            return compiled;
        }

        boolean anyMatches(@NonNull HttpHeaders httpHeaders) {
            return httpHeaders.keySet().stream().anyMatch(h -> this.matches(h, httpHeaders.get(h)));
        }

        boolean matches(@NonNull String headerNameOrTuple) {
            return pattern().matcher(headerNameOrTuple).matches();
        }

        boolean matches(@NonNull String headerName, List<String> values) {
            return values.stream().map(value -> "%s: %s".formatted(headerName, value)).anyMatch(this::matches);
        }

        void removeMatching(@NonNull HttpHeaders headers) {
            List.copyOf(headers.entrySet()).stream().filter(e -> matches(e.getKey()))//
                    .filter(e -> matches(e.getKey(), e.getValue())).map(Map.Entry::getKey)//
                    .peek(name -> log.trace("Removing header {}", name))//
                    .forEach(headers::remove);
        }
    }

}
