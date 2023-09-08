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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class GeorchestraUserRolesAuthorizationManagerTest {

    GeorchestraUserMapper userMapper;
    GeorchestraUser user;
    GeorchestraUserRolesAuthorizationManager<Object> authManager;

    @BeforeEach
    void setup() {
        userMapper = mock(GeorchestraUserMapper.class);
        user = new GeorchestraUser();
        when(userMapper.resolve(any())).thenReturn(Optional.of(user));

        authManager = GeorchestraUserRolesAuthorizationManager.hasAnyAuthority(userMapper, "GDI_ADMIN", "SUPERUSER",
                "ROLE_ADMIN");
    }

    private TestingAuthenticationToken authentication(String... roles) {
        return new TestingAuthenticationToken("gabe", null, roles);
    }

    @Test
    void hasAnyAuthority_notAuthenticated() {
        TestingAuthenticationToken authentication = authentication();
        authentication.setAuthenticated(false);
        assertThat(authManager.authorize(authentication)).isFalse();
    }

    @Test
    void hasAnyAuthority() {
        TestingAuthenticationToken authentication = authentication("ROLE_USER");
        user.setRoles(List.of("ROLE_USER", "GDI_ADMIN"));
        assertThat(authManager.authorize(authentication)).isTrue();

        user.setRoles(List.of("ROLE_USER", "SUPERUSER"));
        assertThat(authManager.authorize(authentication)).isTrue();

        user.setRoles(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertThat(authManager.authorize(authentication)).isTrue();

        user.setRoles(List.of("ROLE_USER"));
        assertThat(authManager.authorize(authentication)).isFalse();
    }

    @Test
    void hasAnyAuthority_joins_user_and_authentication_authorities() {
        TestingAuthenticationToken authentication = authentication("GDI_ADMIN");
        user.setRoles(List.of("ROLE_USER"));
        assertThat(authManager.authorize(authentication)).isTrue();
    }

    @Test
    void hasAnyAuthority_noResolvedUser_nor_grantedAuthorities() {
        TestingAuthenticationToken authentication = authentication();
        when(userMapper.resolve(any())).thenReturn(Optional.empty());

        assertThat(authManager.authorize(authentication)).isFalse();
    }

    @Test
    void hasAnyAuthority_noResolvedUser_resolved_grantedAuthorities() {
        TestingAuthenticationToken authentication = authentication("GDI_ADMIN");
        when(userMapper.resolve(any())).thenReturn(Optional.empty());

        assertThat(authManager.authorize(authentication)).isTrue();
    }

}
