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
package org.georchestra.gateway.test.context.support;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken.Builder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.util.Assert;

/**
 * {@link WithSecurityContextFactory} for {@link WithMockOidcUser}
 */
class WithMockOidcSecurityContextFactory implements WithSecurityContextFactory<WithMockOidcUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockOidcUser withUser) {
        Collection<? extends GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(withUser.authorities());

        Builder builder = OidcIdToken.withTokenValue("test-token-value");
        addStandardClaims(builder, withUser);
        addNonStandardClaims(builder, withUser);
        OidcIdToken idToken = builder.build();

        DefaultOidcUser principal = new DefaultOidcUser(authorities, idToken);
        String clientRegistrationId = withUser.clientRegistrationId();
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(principal, authorities, clientRegistrationId);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        return context;
    }

    private void addNonStandardClaims(Builder builder, WithMockOidcUser withUser) {
        String[] nonStandardClaims = withUser.nonStandardClaims();
        if (null == nonStandardClaims) {
            return;
        }
        Stream.of(nonStandardClaims).peek(kvp -> {
            if (!kvp.matches("(.*)=(.*)")) {
                throw new IllegalArgumentException(
                        "non standard claims have to be provided as key=value pairs, got " + kvp);
            }
        }).map(kvp -> kvp.split("=")).peek(
                arr -> Assert.isTrue(arr.length == 2, "non standard claims have to be provided as key=value pairs, got "
                        + Stream.of(arr).collect(Collectors.joining("="))))
                .forEach(arr -> {
                    String claim = arr[0].trim();
                    String value = arr[1].trim();
                    builder.claim(claim, value);
                });
    }

    private void addStandardClaims(Builder builder, WithMockOidcUser withUser) {
        builder.subject(withUser.sub()).claim("preferred_username", withUser.preferred_username())
                .claim("name", withUser.name()).claim("given_name", withUser.given_name())
                .claim("family_name", withUser.family_name()).claim("middle_name", withUser.middle_name())
                .claim("nickname", withUser.nickname()).claim("profile", withUser.profile())
                .claim("picture", withUser.picture()).claim("website", withUser.website())
                .claim("email", withUser.email()).claim("email_verified", withUser.email_verified())
                .claim("gender", withUser.gender()).claim("birthdate", withUser.birthdate())
                .claim("locale", withUser.locale()).claim("phone_number", withUser.phone_number())
                .claim("phone_number_verified", withUser.phone_number_verified())
//		.claim("address", withUser.address())
                .claim("updated_at", withUser.updated_at());
    }

}