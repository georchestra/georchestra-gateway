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
package org.georchestra.gateway.app;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;

import lombok.extern.slf4j.Slf4j;

/**
 * Main application class for the geOrchestra Gateway.
 * <p>
 * This class initializes the Spring Boot application, manages application-wide
 * configuration, and sets up essential beans and event listeners.
 * </p>
 *
 * <p>
 * Most additional functionalities, such as security, routing, and external
 * integrations, are contributed via Spring Boot
 * {@link org.springframework.boot.autoconfigure.AutoConfiguration} classes.
 * These auto-configurations enable features dynamically based on the
 * application’s dependencies and configuration properties.
 * </p>
 *
 * <p>
 * The only explicitly defined controllers in this package are those required
 * for gateway-specific endpoints, such as authentication entry points or
 * request introspection. All other functionalities are provided through
 * auto-configuration.
 * </p>
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
public class GeorchestraGatewayApplication {

    /**
     * The route locator for retrieving gateway routes. Only used for reporting the
     * number of configured routes at startup
     */
    private @Autowired RouteLocator routeLocator;

    /** The basename for message resources, configurable via properties. */
    private @Value("${spring.messages.basename:}") String messagesBasename;

    /**
     * Entry point for the geOrchestra Gateway application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(GeorchestraGatewayApplication.class, args);
    }

    /**
     * Configures a {@link MessageSource} bean for loading internationalized
     * messages.
     * <p>
     * This method sets up a {@link ReloadableResourceBundleMessageSource} that
     * loads messages from {@code classpath:messages/login} and additional basenames
     * configured via {@code spring.messages.basename}.
     * </p>
     *
     * @return a configured {@link MessageSource} instance
     */
    @Bean
    MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(("classpath:messages/login," + messagesBasename).split(","));
        messageSource.setCacheSeconds(600);
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return messageSource;
    }

    /**
     * Handles the {@link ApplicationReadyEvent}, logging essential application
     * details.
     * <p>
     * This method retrieves environment properties, including the data directory,
     * instance ID, available CPU cores, and memory usage, and logs them for
     * debugging. It also counts the number of registered routes.
     * </p>
     *
     * @param event the application ready event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String datadir = env.getProperty("georchestra.datadir");
        if (datadir != null) {
            datadir = new File(datadir).getAbsolutePath();
        }
        String app = env.getProperty("spring.application.name");
        String instanceId = env.getProperty("info.instance-id");
        int cpus = Runtime.getRuntime().availableProcessors();
        String maxMem = getMaxMem();

        Long routeCount = routeLocator.getRoutes().count().block();
        log.info("{} ready. Data dir: {}. Routes: {}. Instance-id: {}, cpus: {}, max memory: {}", app, datadir,
                routeCount, instanceId, cpus, maxMem);
    }

    /**
     * Retrieves the maximum memory allocated to the JVM and formats it in MB or GB.
     *
     * @return the formatted maximum memory value
     */
    private String getMaxMem() {
        DataSize maxMemBytes = DataSize.ofBytes(Runtime.getRuntime().maxMemory());
        double value = maxMemBytes.toKilobytes() / 1024d;
        String unit = "MB";
        if (maxMemBytes.toGigabytes() > 0) {
            value = value / 1024d;
            unit = "GB";
        }
        return "%.2f %s".formatted(value, unit);
    }
}
