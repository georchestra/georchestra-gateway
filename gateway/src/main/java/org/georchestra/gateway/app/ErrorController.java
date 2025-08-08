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
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class ErrorController {

    /** Path to the geOrchestra custom stylesheet, if configured. */
    private @Value("${georchestraStylesheet:}") String georchestraStylesheet;

    /** URL of the logo displayed in the header. */
    private @Value("${logoUrl:}") String logoUrl;

    @ExceptionHandler(ErrorResponseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String exception(final ErrorResponseException throwable, final Model model) {
        String template = "error/" + (throwable != null ? throwable.getStatusCode().value() : "generic");
        model.addAttribute("georchestraStylesheet", georchestraStylesheet);
        model.addAttribute("logoUrl", logoUrl);
        return template;
    }

}