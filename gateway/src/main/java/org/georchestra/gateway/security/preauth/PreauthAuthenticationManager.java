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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.preauth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.georchestra.commons.security.SecurityHeaders;
import org.georchestra.security.model.GeorchestraUser;
import org.ldaptive.io.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public class PreauthAuthenticationManager implements ReactiveAuthenticationManager, ServerAuthenticationConverter {

    public static final String PREAUTH_HEADER_NAME = "sec-georchestra-preauthenticated";

    public static final String PREAUTH_USERNAME = "preauth-username";
    public static final String PREAUTH_EMAIL = "preauth-email";
    public static final String PREAUTH_FIRSTNAME = "preauth-firstname";
    public static final String PREAUTH_LASTNAME = "preauth-lastname";
    public static final String PREAUTH_ORG = "preauth-org";
    public static final String PREAUTH_ROLES = "preauth-roles";
    public static final String PREAUTH_PROVIDER = "preauth-provider";
    public static final String PREAUTH_PROVIDER_ID = "preauth-provider-id";

    /**
     * @return {@code Mono.empty()} if the pre-auth request headers are not
     *         provided,
     */
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        if (isPreAuthenticated(exchange)) {
            HttpHeaders headers = exchange.getRequest().getHeaders();
            String username = headers.getFirst(PREAUTH_USERNAME);
            if (!StringUtils.hasText(username)) {
                throw new IllegalStateException("Pre-authenticated user headers not provided");
            }
            PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(username,
                    extract(headers), List.of());
            return Mono.just(authentication);
        }
        return Mono.empty();
    }

    private Map<String, String> extract(HttpHeaders headers) {
        return headers.toSingleValueMap().entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().startsWith("preauth-"))
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue));
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.just(authentication);
    }

    public static boolean isPreAuthenticated(ServerWebExchange exchange) {
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
        final String preAuthHeader = requestHeaders.getFirst(PREAUTH_HEADER_NAME);
        final boolean preAuthenticated = "true".equalsIgnoreCase(preAuthHeader);
        return preAuthenticated;
    }

    public static GeorchestraUser map(Map<String, String> requestHeaders) {
        String username = SecurityHeaders.decode(requestHeaders.get(PREAUTH_USERNAME));
        String email = SecurityHeaders.decode(requestHeaders.get(PREAUTH_EMAIL));
        String firstName = SecurityHeaders.decode(requestHeaders.get(PREAUTH_FIRSTNAME));
        String lastName = SecurityHeaders.decode(requestHeaders.get(PREAUTH_LASTNAME));
        String org = SecurityHeaders.decode(requestHeaders.get(PREAUTH_ORG));
        String rolesValue = SecurityHeaders.decode(requestHeaders.get(PREAUTH_ROLES));
        String provider = SecurityHeaders.decode(requestHeaders.get(PREAUTH_PROVIDER));
        String providerId = SecurityHeaders.decode(requestHeaders.get(PREAUTH_PROVIDER_ID));

        List<String> roleNames = Optional.ofNullable(rolesValue)
                .map(roles -> Stream
                        .concat(Stream.of("ROLE_USER"), Stream.of(roles.split(";")).filter(StringUtils::hasText))
                        .distinct())
                .orElse(Stream.of("ROLE_USER")).collect(Collectors.toList());

        GeorchestraUser user = new GeorchestraUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOrganization(org);
        user.setRoles(roleNames);
        user.setOAuth2Provider(provider);
        user.setOAuth2Uid(providerId);
        // TODO rename oauth2 fields to a more generic name : externalProvider ?
        return user;
    }

    public void removePreauthHeaders(HttpHeaders mutableHeaders) {
        mutableHeaders.remove(PREAUTH_HEADER_NAME);
        mutableHeaders.remove(PREAUTH_USERNAME);
        mutableHeaders.remove(PREAUTH_EMAIL);
        mutableHeaders.remove(PREAUTH_FIRSTNAME);
        mutableHeaders.remove(PREAUTH_LASTNAME);
        mutableHeaders.remove(PREAUTH_ORG);
        mutableHeaders.remove(PREAUTH_ROLES);
        mutableHeaders.remove(PREAUTH_PROVIDER);
        mutableHeaders.remove(PREAUTH_PROVIDER_ID);
    }
}
