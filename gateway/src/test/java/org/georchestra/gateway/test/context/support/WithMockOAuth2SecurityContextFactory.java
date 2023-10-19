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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.util.Assert;

/**
 * {@link WithSecurityContextFactory} for {@link WithMockOAuth2User}
 */
class WithMockOAuth2SecurityContextFactory implements WithSecurityContextFactory<WithMockOAuth2User> {

    @Override
    public SecurityContext createSecurityContext(WithMockOAuth2User withUser) {
        Collection<? extends GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(withUser.authorities());
        Map<String, Object> attributes = toAttributes(withUser);
        String nameAttributeKey = withUser.nameAttributeKey();

        OAuth2User principal = new DefaultOAuth2User(authorities, attributes, nameAttributeKey);
        String clientRegistrationId = withUser.clientRegistrationId();
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(principal, authorities, clientRegistrationId);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        return context;
    }

    private Map<String, Object> toAttributes(WithMockOAuth2User withUser) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", withUser.login());
        attributes.put("email", withUser.email());
        if (null != withUser.attributes()) {
            Stream.of(withUser.attributes()).peek(kvp -> {
                if (!kvp.matches("(.*)=(.*)")) {
                    throw new IllegalArgumentException("attributes have to be provided as key=value pairs, got " + kvp);
                }
            }).map(kvp -> kvp.split("=")).peek(
                    arr -> Assert.isTrue(arr.length == 2, "attributes have to be provided as key=value pairs, got "
                            + Stream.of(arr).collect(Collectors.joining("="))))
                    .forEach(arr -> {
                        String attribute = arr[0].trim();
                        String value = arr[1].trim();
                        attributes.put(attribute, value);
                    });
        }
        return attributes;
    }

}