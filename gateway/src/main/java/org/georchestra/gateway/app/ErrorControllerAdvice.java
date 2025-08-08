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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class ErrorControllerAdvice {

    /** Path to the geOrchestra custom stylesheet, if configured. */
    private @Value("${georchestraStylesheet:}") String georchestraStylesheet;

    /** URL of the logo displayed in the header. */
    private @Value("${logoUrl:}") String logoUrl;

    @ExceptionHandler(ErrorResponseException.class)
    public Mono<String> exception(final ErrorResponseException throwable, final Model model,
                                  ServerWebExchange exchange) {
        HttpStatusCode status = throwable != null ? throwable.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;
        String template = "error/" + (throwable != null ? status.value() : "generic");
        model.addAttribute("georchestraStylesheet", georchestraStylesheet);
        model.addAttribute("logoUrl", logoUrl);
        exchange.getResponse().setStatusCode(status);
        return Mono.just(template);
    }

}