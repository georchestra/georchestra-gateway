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

package org.georchestra.gateway.autoconfigure.actuator;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * If {@literal server.port} is different than
 * {@literal management.server.port}, routes calls to
 * {@literal /actuator/health/**} to
 * {@literal localhost:${management.server.port}/actuator/health/**}
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
@Slf4j(topic = "org.georchestra.gateway.autoconfigure.actuator")
public class HealthEndpointAutoConfiguration {

    static final String ROUTE_ID = "_management_port_proxy";

    private @Value("${management.server.port}") int managementPort;
    private @Value("${management.endpoints.web.base-path:}") String basePath;

    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder) {
        Builder routes = builder.routes();
        String base = StringUtils.hasText(basePath) ? basePath : "/actuator";
        String path = URI.create(base + "/health").normalize().toString();

        String sourcePath = path + "/**";
        String targetUri = String.format("http://localhost:%d%s", managementPort, path);
        log.info("Exposing management health endpoints at {} from {}", sourcePath, targetUri);
        routes.route(ROUTE_ID, r -> r.path(sourcePath).uri(targetUri));
        return routes.build();
    }
}
