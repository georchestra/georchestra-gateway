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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;

/**
 */
class HealthEndpointAutoConfigurationTest {

    private ReactiveWebApplicationContextRunner runner;

    private RouteLocatorBuilder mockLocatorBuilder;
    private RouteLocatorBuilder.Builder routeBuilder;

    public @BeforeEach void setup() {
        routeBuilder = mock(Builder.class);
        when(routeBuilder.build()).thenReturn(mock(RouteLocator.class));

        mockLocatorBuilder = mock(RouteLocatorBuilder.class);
        when(mockLocatorBuilder.routes()).thenReturn(routeBuilder);

        runner = new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(//
                ReactiveManagementContextAutoConfiguration.class, ManagementContextAutoConfiguration.class,
                HealthEndpointAutoConfiguration.class));
    }

    @Test
    void testDisabled() {
        runner.withBean(RouteLocatorBuilder.class, () -> mockLocatorBuilder)
                .withPropertyValues("server.port=8080", "management.server.port=8080").run(context -> {
                    assertThat(context).hasNotFailed();
                    verify(mockLocatorBuilder, times(0)).routes();
                });
    }

    @Test
    void testEnabled() {
        runner.withBean(RouteLocatorBuilder.class, () -> mockLocatorBuilder)
                .withPropertyValues("server.port=8080", "management.server.port=8081").run(context -> {
                    assertThat(context).hasNotFailed();
                    verify(mockLocatorBuilder, times(1)).routes();
                    verify(routeBuilder, times(1)).route(eq(HealthEndpointAutoConfiguration.ROUTE_ID), any());
                });
    }

}
