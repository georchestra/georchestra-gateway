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
package org.georchestra.gateway.security.accessrules;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorityReactiveAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.util.Assert;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

/**
 * Variant of {@link AuthorityReactiveAuthorizationManager} that
 * {@link #check(Mono, Object) checks} access based on the effectively resolved
 * set of role names in a {@link GeorchestraUser}, as opposed to only on the
 * {@link Authentication#getAuthorities() Authenticateion authorities}.
 * <p>
 * This is so because the authorization provider (e.g. OAuth2/OIDC) returns an
 * {@link Authentication} object from which {@link GeorchestraUserMapper} will
 * derive additional roles to be sent to the downstream services as the
 * {@code sec-roles} header, and we need to also account for those derived role
 * names when granting access to an URI (see {@link AccessRulesCustomizer#apply}
 * and {@link AccessRulesCustomizer#hasAnyAuthority}).
 */
class GeorchestraUserRolesAuthorizationManager<T> implements ReactiveAuthorizationManager<T> {

    private final GeorchestraUserMapper userMapper;
    private final List<GrantedAuthority> authorities;
    private final Set<String> authorityFilter;
    private final AuthorityAuthorizationDecision unauthorized;

    GeorchestraUserRolesAuthorizationManager(GeorchestraUserMapper userMapper, String... authorities) {
        this.userMapper = userMapper;
        this.authorities = AuthorityUtils.createAuthorityList(authorities);
        this.authorityFilter = Set.of(authorities);
        this.unauthorized = new AuthorityAuthorizationDecision(false, this.authorities);
    }

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, T object) {
        return authentication.map(this::authorize).map(
                (granted) -> ((AuthorizationDecision) new AuthorityAuthorizationDecision(granted, this.authorities)))
                .defaultIfEmpty(unauthorized);
    }

    @VisibleForTesting
    boolean authorize(Authentication authentication) {
        if (!authentication.isAuthenticated()) {
            return false;
        }
        Optional<GeorchestraUser> user = userMapper.resolve(authentication);
        Stream<String> effectiveRoles = user.map(GeorchestraUser::getRoles).map(List::stream).orElse(Stream.empty());
        Stream<String> grandtedAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority);

        return Stream.concat(effectiveRoles, grandtedAuthorities).sorted().distinct()
                .anyMatch(authorityFilter::contains);
    }

    /**
     * Creates an instance of {@link GeorchestraUserRolesAuthorizationManager} with
     * the provided authority.
     * 
     * @param authority the authority to check for
     * @param <T>       the type of object being authorized
     * @return the new instance
     */
    public static <T> GeorchestraUserRolesAuthorizationManager<T> hasAuthority(GeorchestraUserMapper userMapper,
            String authority) {
        Assert.notNull(authority, "authority cannot be null");
        return new GeorchestraUserRolesAuthorizationManager<>(userMapper, authority);
    }

    /**
     * Creates an instance of {@link GeorchestraUserRolesAuthorizationManager} with
     * the provided authorities.
     * 
     * @param authorities the authorities to check for
     * @param <T>         the type of object being authorized
     * @return the new instance
     */
    public static <T> GeorchestraUserRolesAuthorizationManager<T> hasAnyAuthority(GeorchestraUserMapper userMapper,
            String... authorities) {
        Assert.notNull(authorities, "authorities cannot be null");
        for (String authority : authorities) {
            Assert.notNull(authority, "authority cannot be null");
        }
        return new GeorchestraUserRolesAuthorizationManager<>(userMapper, authorities);
    }

    /**
     * Creates an instance of {@link GeorchestraUserRolesAuthorizationManager} with
     * the provided authority.
     * 
     * @param role the authority to check for prefixed with "ROLE_"
     * @param <T>  the type of object being authorized
     * @return the new instance
     */
    public static <T> GeorchestraUserRolesAuthorizationManager<T> hasRole(GeorchestraUserMapper userMapper,
            String role) {
        Assert.notNull(role, "role cannot be null");
        return hasAuthority(userMapper, "ROLE_" + role);
    }

    /**
     * Creates an instance of {@link GeorchestraUserRolesAuthorizationManager} with
     * the provided authorities.
     * 
     * @param roles the authorities to check for prefixed with "ROLE_"
     * @param <T>   the type of object being authorized
     * @return the new instance
     */
    public static <T> GeorchestraUserRolesAuthorizationManager<T> hasAnyRole(GeorchestraUserMapper userMapper,
            String... roles) {
        Assert.notNull(roles, "roles cannot be null");
        for (String role : roles) {
            Assert.notNull(role, "role cannot be null");
        }
        return hasAnyAuthority(userMapper, toNamedRolesArray(roles));
    }

    private static String[] toNamedRolesArray(String... roles) {
        String[] result = new String[roles.length];
        for (int i = 0; i < roles.length; i++) {
            result[i] = "ROLE_" + roles[i];
        }
        return result;
    }

}
