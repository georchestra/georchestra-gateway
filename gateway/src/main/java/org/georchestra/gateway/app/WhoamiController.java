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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Controller that provides user authentication details.
 * <p>
 * This controller exposes an endpoint to return information about the currently
 * authenticated user, including their mapped {@link GeorchestraUser} details.
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class WhoamiController {

    /**
     * The user mapper responsible for resolving authentication details into a
     * {@link GeorchestraUser}.
     */
    private final GeorchestraUserMapper userMapper;

    /**
     * Returns details about the currently authenticated user.
     * <p>
     * This endpoint returns a JSON response containing the mapped
     * {@link GeorchestraUser} object and the raw {@link Authentication} object. If
     * the user is not authenticated, both values will be {@code null}.
     * </p>
     *
     * <p>
     * <b>Example Response:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "GeorchestraUser": {
     *     "username": "jdoe",
     *     "email": "jdoe@example.com",
     *     "roles": ["ROLE_USER"],
     *     "organization": "ExampleOrg"
     *   },
     *   "org.springframework.security.authentication.UsernamePasswordAuthenticationToken": {
     *     "principal": "jdoe",
     *     "authorities": ["ROLE_USER"]
     *   }
     * }
     * </pre>
     *
     * @param principal the currently authenticated user, if available
     * @param exchange  the current web exchange request context
     * @return a reactive {@link Mono} containing a map with user authentication
     *         details
     */
    @GetMapping(path = "/whoami", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Mono<Map<String, Object>> whoami(Authentication principal, ServerWebExchange exchange) {
        GeorchestraUser user;
        try {
            user = Optional.ofNullable(principal).flatMap(userMapper::resolve).orElse(null);
        } catch (DuplicatedEmailFoundException e) {
            user = null;
        }

        Map<String, Object> ret = new LinkedHashMap<>();
        if (user != null) {
            // notes is an internal field and should not be provided by the /whoami endpoint
            // (see #170)
            user.setNotes(null);
        }
        ret.put("GeorchestraUser", user);
        if (principal == null) {
            ret.put("Authentication", null);
        } else {
            ret.put(principal.getClass().getCanonicalName(), principal);
        }
        return Mono.just(ret);
    }
}
