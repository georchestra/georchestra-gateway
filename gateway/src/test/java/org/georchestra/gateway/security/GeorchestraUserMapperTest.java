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

package org.georchestra.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

/**
 * Test suite for {@link GeorchestraUserMapper}
 */
class GeorchestraUserMapperTest {

    @Test
    void testResolve_null_auth_token() {
        assertThrows(NullPointerException.class, () -> new GeorchestraUserMapper(List.of()).resolve(null));
    }

    @Test
    void testResolve_no_extensions() {
        GeorchestraUserMapper mapper = new GeorchestraUserMapper(List.of());
        Authentication auth = mock(Authentication.class);
        Optional<GeorchestraUser> resolved = mapper.resolve(auth);
        assertNotNull(resolved);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void testResolve() {
        GeorchestraUserMapperExtension ext1 = mock(GeorchestraUserMapperExtension.class);
        when(ext1.resolve(any(Authentication.class))).thenReturn(Optional.empty());

        GeorchestraUser expected = mock(GeorchestraUser.class);
        Authentication auth = mock(Authentication.class);

        GeorchestraUserMapperExtension ext2 = mock(GeorchestraUserMapperExtension.class);
        when(ext2.resolve(same(auth))).thenReturn(Optional.of(expected));

        List<GeorchestraUserMapperExtension> resolvers = List.of(ext1, ext2);
        GeorchestraUserMapper mapper = new GeorchestraUserMapper(resolvers);
        Optional<GeorchestraUser> resolved = mapper.resolve(auth);
        assertTrue(resolved.isPresent());
        assertSame(expected, resolved.get());
    }

    @Test
    void testResolveOrder() {
        Authentication auth = mock(Authentication.class);

        GeorchestraUser user1 = mock(GeorchestraUser.class);
        GeorchestraUserMapperExtension ext1 = mock(GeorchestraUserMapperExtension.class);
        when(ext1.resolve(same(auth))).thenReturn(Optional.of(user1));

        GeorchestraUser user2 = mock(GeorchestraUser.class);
        GeorchestraUserMapperExtension ext2 = mock(GeorchestraUserMapperExtension.class);
        when(ext2.resolve(same(auth))).thenReturn(Optional.of(user2));

        List<GeorchestraUserMapperExtension> resolvers = List.of(ext1, ext2);
        GeorchestraUserMapper mapper = new GeorchestraUserMapper(resolvers);
        Optional<GeorchestraUser> resolved = mapper.resolve(auth);
        assertTrue(resolved.isPresent());
        assertSame(user1, resolved.get());
    }

    @Test
    void testAppliesPosResolvingCustomizerExtensions() {
        Authentication auth = mock(Authentication.class);

        GeorchestraUser user = new GeorchestraUser();
        GeorchestraUserMapperExtension userMapper = mock(GeorchestraUserMapperExtension.class);
        when(userMapper.resolve(same(auth))).thenReturn(Optional.of(user));

        GeorchestraUserCustomizerExtension customizer1 = u -> {
            u.setUsername("customizer1");
            return u;
        };

        GeorchestraUserCustomizerExtension customizer2 = u -> {
            u.setRoles(List.of("ROLE_1", "ROLE_2"));
            return u;
        };

        List<GeorchestraUserCustomizerExtension> postResolveCustomizers = List.of(customizer1, customizer2);

        GeorchestraUserMapper mapper = new GeorchestraUserMapper(List.of(userMapper), postResolveCustomizers);
        Optional<GeorchestraUser> resolved = mapper.resolve(auth);
        assertTrue(resolved.isPresent());
        assertSame(user, resolved.get());

        assertEquals("customizer1", resolved.get().getUsername());
        assertEquals(List.of("ROLE_1", "ROLE_2"), resolved.get().getRoles());
    }
}
