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
package org.georchestra.gateway.security;

import java.util.Optional;
import java.util.stream.Collectors;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

/**
 * Catch and convert {@link Authentication}s created by {@literal @WithMockUser}
 */
public @Configuration class MockUserGeorchestraUserMapperConfiguration {

    @Bean
    MockUserGeorchestraUserMapperConfiguration.TestUserMapper testUserMapper() {
        return new TestUserMapper();
    }

    static class TestUserMapper implements GeorchestraUserMapperExtension {
        public @Override Optional<GeorchestraUser> resolve(Authentication authToken) {
            return Optional.ofNullable(authToken)//
                    .filter(UsernamePasswordAuthenticationToken.class::isInstance)
                    .map(UsernamePasswordAuthenticationToken.class::cast)//
                    .filter(token -> !(token.getPrincipal() instanceof LdapUserDetails))//
                    .filter(token -> token.getPrincipal() instanceof org.springframework.security.core.userdetails.User)
                    .map(t -> {
                        GeorchestraUser user = new GeorchestraUser();
                        user.setUsername(t.getName());
                        user.setRoles(t.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList()));
                        return user;
                    });
        }

    }
}