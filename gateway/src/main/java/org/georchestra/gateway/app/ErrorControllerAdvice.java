/*
 * Copyright (C) 2025 by the geOrchestra PSC
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@ControllerAdvice
public class ErrorControllerAdvice {

    /** Path to the geOrchestra custom stylesheet, if configured. */
    private @Value("${georchestraStylesheet:}") String georchestraStylesheet;

    /** URL of the logo displayed in the header. */
    private @Value("${logoUrl:}") String logoUrl;

    @Value("${spring.thymeleaf.prefix:classpath:/templates/}")
    private String thymeleafPrefix;

    private final ResourceLoader resourceLoader;

    public ErrorControllerAdvice(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @ExceptionHandler(ErrorResponseException.class)
    public Mono<String> exception(final ErrorResponseException throwable, final Model model,
                                  ServerWebExchange exchange) {
        HttpStatusCode status = throwable.getStatusCode();
        String template = "error/" + status.value();
        model.addAttribute("georchestraStylesheet", georchestraStylesheet);
        model.addAttribute("logoUrl", logoUrl);
        exchange.getResponse().setStatusCode(status);
        if (!templateExists(template)) {
            log.warn("Template '{}' not found, falling back to 'error/generic'", template);
            template = "error/generic";
        }
        return Mono.just(template);
    }

    private boolean templateExists(String templateName) {
        String resourcePath = thymeleafPrefix + templateName + ".html";
        Resource resource = resourceLoader.getResource(resourcePath);
        return resource.exists() && resource.isReadable();
    }
}