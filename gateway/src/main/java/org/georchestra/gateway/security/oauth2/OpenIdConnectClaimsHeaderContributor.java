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
package org.georchestra.gateway.security.oauth2;

import java.util.Optional;
import java.util.function.Consumer;

import org.georchestra.gateway.filter.headers.HeaderContributor;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ServerWebExchange;

import lombok.RequiredArgsConstructor;

/**
 * Contributes the following headers if the request was authenticated through
 * OpenId Connect:
 * <p>
 * <ul>
 * <li>{@literal authorizedParty}: from the
 * {@link OidcUser#getAuthorizedParty()}
 * <li>{@literal accessTokenHash}: from the
 * {@link OidcUser#getAccessTokenHash())}
 * </ul>
 * If required, more claims can be added and made configurable. There are just
 * too many options and no requirement right now.
 * 
 * @see GeorchestraUsers#resolveAuth(ServerWebExchange)
 */
@RequiredArgsConstructor
public class OpenIdConnectClaimsHeaderContributor extends HeaderContributor {

    public @Override Consumer<HttpHeaders> prepare(ServerWebExchange exchange) {

        return headers -> {
            Optional<Authentication> auth = GeorchestraUsers.resolveAuth(exchange);
            Optional<OidcUser> oidc = auth.filter(OAuth2AuthenticationToken.class::isInstance)
                    .map(OAuth2AuthenticationToken.class::cast).map(OAuth2AuthenticationToken::getPrincipal)
                    .filter(OidcUser.class::isInstance).map(OidcUser.class::cast);

            oidc.ifPresent(user -> {
                // if required, more claims can be added, and can be made configurable
                add(headers, "accessTokenHash", user.getAccessTokenHash());
                add(headers, "authorizedParty", user.getAuthorizedParty());
            });
        };
    }
}
