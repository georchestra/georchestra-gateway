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
package org.georchestra.gateway.filter.global;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.HeaderMappings;
import org.georchestra.gateway.model.RoleBasedAccessRule;
import org.georchestra.gateway.model.Service;
import org.georchestra.gateway.security.ResolveGeorchestraUserGlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * A {@link GlobalFilter} that resolves the {@link GeorchestraTargetConfig
 * configuration} for the request's matched {@link Route} and
 * {@link GeorchestraTargetConfig#setTarget stores} it to be
 * {@link GeorchestraTargetConfig#getTarget acquired} by non-global filters as
 * needed.
 */
@RequiredArgsConstructor
@Slf4j
public class ResolveTargetGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = ResolveGeorchestraUserGlobalFilter.ORDER + 1;

    private final @NonNull GatewayConfigProperties config;

    /**
     * @return a lower precedence than {@link RouteToRequestUrlFilter}'s, in order
     *         to make sure the matched {@link Route} has been set as a
     *         {@link ServerWebExchange#getAttributes attribute} when
     *         {@link #filter} is called.
     */
    public @Override int getOrder() {
        return ResolveTargetGlobalFilter.ORDER;
    }

    /**
     * Resolves the matched {@link Route} and its corresponding
     * {@link GeorchestraTargetConfig}, if possible, and proceeds with the filter
     * chain.
     */
    public @Override Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = (Route) exchange.getAttributes().get(GATEWAY_ROUTE_ATTR);
        Objects.requireNonNull(route, "no route matched, filter shouldn't be hit");

        GeorchestraTargetConfig targetConfig = resolveTarget(route);
        log.debug("Storing geOrchestra target config for Route {} request context", route.getId());
        GeorchestraTargetConfig.setTarget(exchange, targetConfig);
        return chain.filter(exchange);
    }

    @VisibleForTesting
    @NonNull
    GeorchestraTargetConfig resolveTarget(@NonNull Route route) {

        GeorchestraTargetConfig target = new GeorchestraTargetConfig();

        Optional<Service> service = findService(route);

        setAccessRules(target, service);
        setHeaderMappings(target, service);

        return target;
    }

    private void setAccessRules(GeorchestraTargetConfig target, Optional<Service> service) {
        List<RoleBasedAccessRule> globalAccessRules = config.getGlobalAccessRules();
        var targetAccessRules = service.map(Service::getAccessRules).filter(Objects::nonNull).filter(l -> !l.isEmpty())
                .orElse(globalAccessRules);

        target.accessRules(targetAccessRules);
    }

    private void setHeaderMappings(GeorchestraTargetConfig target, Optional<Service> service) {
        HeaderMappings defaultHeaders = config.getDefaultHeaders();
        HeaderMappings mergedHeaders = service.flatMap(Service::headers)
                .map(serviceHeaders -> merge(defaultHeaders, serviceHeaders)).orElse(defaultHeaders);

        target.headers(mergedHeaders);
    }

    private HeaderMappings merge(HeaderMappings defaults, HeaderMappings service) {
        return defaults.copy().merge(service);
    }

    private Optional<Service> findService(@NonNull Route route) {
        final URI routeURI = route.getUri();

        for (Service service : config.getServices().values()) {
            var serviceURI = service.getTarget();
            if (Objects.equals(routeURI, serviceURI)) {
                return Optional.of(service);
            }
        }

        return Optional.empty();
    }

}