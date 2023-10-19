/*
 * Copyright (C) 2022 by the geOrchestra PSC
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.georchestra.gateway.security.GeorchestraUserMapperExtension;
import org.georchestra.security.model.GeorchestraUser;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps {@link OAuth2AuthenticationToken} to {@link GeorchestraUser}.
 * <p>
 * <ul>
 * <li>The {@link OAuth2User principal}'s {@literal login}
 * {@link OAuth2User#getAttributes() attribute} is used with preference to the
 * {@link OAuth2AuthenticationToken#getName() name} if provided, to set the
 * {@link GeorchestraUser#setUsername(String) username}, since the name is
 * usually an external sytem's numeric identifier that's not really appropriate
 * for a username.
 * <li>The user's {@link GeorchestraUser#setEmail(String) email} is obtained
 * from the {@literal email} {@link OAuth2User#getAttributes() attribute}, if
 * present.
 * <li>The user's {@link GeorchestraUser#setRoles(List) roles} are derived from
 * the {@link GrantedAuthority granted authorities} in the
 * {@link OAuth2User#getAuthorities()}, removing those that start with
 * {@literal ROLE_SCOPE_} or {@code SCOPE_}.
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class OAuth2UserMapper implements GeorchestraUserMapperExtension {

    protected final @NonNull OAuth2ConfigurationProperties config;

    @Override
    public Optional<GeorchestraUser> resolve(Authentication authToken) {
        return Optional.ofNullable(authToken)//
                .filter(OAuth2AuthenticationToken.class::isInstance)//
                .map(OAuth2AuthenticationToken.class::cast)//
                .filter(tokenFilter())//
                .flatMap(this::map);
    }

    protected Predicate<OAuth2AuthenticationToken> tokenFilter() {
        return token -> true;
    }

    protected Optional<GeorchestraUser> map(OAuth2AuthenticationToken token) {
        logger().debug("Mapping {} authentication token from provider {}",
                token.getPrincipal().getClass().getSimpleName(), token.getAuthorizedClientRegistrationId());

        OAuth2User oAuth2User = token.getPrincipal();
        GeorchestraUser user = new GeorchestraUser();
        final String oAuth2ProviderId = String.format("%s;%s", token.getAuthorizedClientRegistrationId(),
                token.getName());
        user.setOAuth2ProviderId(oAuth2ProviderId);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        List<String> roles = resolveRoles(oAuth2User.getAuthorities());
        String userName = token.getName();
        String login = (String) attributes.get("login");

        /*
         * plain Oauth2 authentication user names are usually a number. The 'login'
         * attribute usually carries over a more meaningful name, so use it in
         * preference of userName if provided
         */
        apply(user::setUsername, login, userName);
        apply(user::setEmail, (String) attributes.get("email"));
        user.setRoles(roles);

        return Optional.of(user);
    }

    protected List<String> resolveRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).map(config.getRoles()::applyTransforms)
                .collect(Collectors.toList());
    }

    protected void apply(Consumer<String> setter, String... candidates) {
        for (String candidateValue : candidates) {
            if (null != candidateValue) {
                setter.accept(candidateValue);
                break;
            }
        }
    }

    protected Logger logger() {
        return log;
    }
}
