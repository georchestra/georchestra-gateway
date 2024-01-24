/*
 * Copyright (C) 2023 by the geOrchestra PSC
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
package org.georchestra.gateway.autoconfigure.app;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;

import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Maps connection exceptions to HTTP 503 status code instead of 500
 * <p>
 * In the event that a route exists and the downstream service is not available,
 * usually the Gateway returns a 503 status code as expected.
 * <p>
 * On a dynamic environment though, such as k8s and docker compose, the
 * underlying error results from a DNS lookup failure, and the default error is
 * 500 instead.
 * <p>
 * This {@link ErrorAttributes} overrides the {@link DefaultErrorAttributes}
 * configured in {@link ErrorWebFluxAutoConfiguration} and injected to the
 * {@link ErrorWebExceptionHandler}, and translates
 * {@link java.net.UnknownHostException} and {@link java.net.ConnectException}
 * to {@link HttpStatus#SERVICE_UNAVAILABLE}
 */
class CustomErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        Throwable error = super.getError(request);
        if (error instanceof UnknownHostException || error instanceof ConnectException) {
            attributes.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
            attributes.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        }
        return attributes;
    }

}