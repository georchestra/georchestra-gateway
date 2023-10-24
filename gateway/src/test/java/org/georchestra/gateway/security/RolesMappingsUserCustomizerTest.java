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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.NonNull;

/**
 * Test suite for {@link RolesMappingsUserCustomizer}
 */
class RolesMappingsUserCustomizerTest {

    private GeorchestraUser user;
    private RolesMappingsUserCustomizer customizer;

    @BeforeEach
    void setUp() {
        user = new GeorchestraUser();
        Map<String, List<String>> usernameMappings = Map.of();
        Map<String, List<String>> roleMappings = Map.of();
        customizer = new RolesMappingsUserCustomizer(usernameMappings, roleMappings);
    }

    @Test
    void constructorCreatesValidPatterns() {
        Pattern pattern;

        pattern = RolesMappingsUserCustomizer.toPattern("ROLE.GDI.USER");
        assertTrue(pattern.matcher("ROLE.GDI.USER").matches());
        assertFalse(pattern.matcher("ROLE.GDI_USER").matches());

        pattern = RolesMappingsUserCustomizer.toPattern("ROLE.*.*.ADMIN");
        assertTrue(pattern.matcher("ROLE.GDI.GS.ADMIN").matches());
        assertFalse(pattern.matcher("ROLE.GDI.GS.USER").matches());

        pattern = RolesMappingsUserCustomizer.toPattern("test.user@example.com");
        assertTrue(pattern.matcher("test.user@example.com").matches());
        assertFalse(pattern.matcher("test2.user@example.com").matches());

        pattern = RolesMappingsUserCustomizer.toPattern("*.user@example.com");
        assertTrue(pattern.matcher("test.user@example.com").matches());
        assertTrue(pattern.matcher("test2.user@example.com").matches());
        assertFalse(pattern.matcher("test2.users@example.com").matches());
    }

    @Test
    void emptyConfig() {
        user.setRoles(List.of("ROLE_USER"));
        GeorchestraUser customized = customizer.apply(user);
        assertSame(user, customized);
        assertEquals(List.of("ROLE_USER"), customized.getRoles());
    }

    @Test
    void matchesLiteralMappings_based_on_roles() {
        customizer.addRoleMappings("ROLE_USER", "ROLE_EDITOR", "ROLE_USER", "ROLE_GUEST");
        customizer.addRoleMappings("ROLE_ADMIN", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR");

        GeorchestraUser customized;

        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_EDITOR", "ROLE_USER", "ROLE_GUEST"), Set.copyOf(customized.getRoles()));

        user.setRoles(List.of("ROLE_ADMIN"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"), Set.copyOf(customized.getRoles()));

        user.setRoles(List.of("ROLE_ADMIN", "ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(
                Set.of("ROLE_ADMIN", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR", "ROLE_EDITOR", "ROLE_USER", "ROLE_GUEST"),
                Set.copyOf(customized.getRoles()));
    }

    @Test
    void matchesRegexMappings_based_on_roles() {
        customizer.addRoleMappings("ROLE.*.USER", "ROLE_USER", "ROLE_GUEST");
        customizer.addRoleMappings("ROLE.*.ADMIN", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR");

        GeorchestraUser customized;

        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER"), Set.copyOf(customized.getRoles()));

        user.setRoles(List.of("ROLE.GDI.USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE.GDI.USER", "ROLE_USER", "ROLE_GUEST"), Set.copyOf(customized.getRoles()));

        user.setRoles(List.of("ROLE.GDI.ADMIN"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE.GDI.ADMIN", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"),
                Set.copyOf(customized.getRoles()));

        user.setRoles(List.of("ROLE.TEST.ADMIN", "ROLE.GDI.USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE.TEST.ADMIN", "ROLE.GDI.USER", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR", "ROLE_USER",
                "ROLE_GUEST"), Set.copyOf(customized.getRoles()));
    }

    @Test
    void matchesLiteralMappings_based_on_username() {
        customizer.addUsernameMappings("test1@user.com", "ROLE_EDITOR", "ROLE_GUEST");
        customizer.addUsernameMappings("test2@user.com", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR");

        GeorchestraUser customized;

        user.setUsername("test1@user.com");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_EDITOR", "ROLE_USER", "ROLE_GUEST"), Set.copyOf(customized.getRoles()));

        user.setUsername("test2@user.com");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"), Set.copyOf(customized.getRoles()));
    }

    @Test
    void matchesRegexMappings_based_on_username() {
        customizer.addUsernameMappings("*@user.com", "ROLE_USER", "ROLE_GUEST");
        customizer.addUsernameMappings("test2*", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR");

        GeorchestraUser customized;

        user.setUsername("test1@user.com");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER", "ROLE_GUEST"), Set.copyOf(customized.getRoles()));

        user.setUsername("another.test.user@user.com");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER", "ROLE_GUEST"), Set.copyOf(customized.getRoles()));

        user.setUsername("test2");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"), Set.copyOf(customized.getRoles()));

        user.setUsername("test2@example.com");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"), Set.copyOf(customized.getRoles()));

        user.setUsername("test2@user.com");
        user.setRoles(List.of("ROLE_USER"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_USER", "ROLE_GUEST", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"),
                Set.copyOf(customized.getRoles()));
    }

    @Test
    void matches_both_user_and_roles_mappings() {
        customizer.addUsernameMappings("*@user.com", "ROLE_USER", "ROLE_GUEST");
        customizer.addRoleMappings("ROLE_MAP_ME", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR");

        GeorchestraUser customized;

        user.setUsername("some.test.name@user.com");
        user.setRoles(List.of("ROLE_MAP_ME"));
        customized = customizer.apply(user);
        assertEquals(Set.of("ROLE_MAP_ME", "ROLE_USER", "ROLE_GUEST", "ROLE_GN_ADMIN", "ROLE_ADMINISTRATOR"),
                Set.copyOf(customized.getRoles()));
    }
}
