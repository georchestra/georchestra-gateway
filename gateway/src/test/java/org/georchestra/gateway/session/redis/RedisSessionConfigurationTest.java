/*
 * Copyright (C) 2026 by the geOrchestra PSC
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
package org.georchestra.gateway.session.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test suite for {@link RedisSessionConfiguration}
 */
public class RedisSessionConfigurationTest {

    @Test
    void testTokenSerialization() throws Exception {
        ObjectMapper mapper = RedisSessionConfiguration.createObjectMapper();
        Authentication mockAuth = new UsernamePasswordAuthenticationToken("user", "pass",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_SUPERUSER")));
        GeorchestraUserNamePasswordAuthenticationToken token = new GeorchestraUserNamePasswordAuthenticationToken(
                "myConfig", mockAuth);

        String json = mapper.writeValueAsString(token);

        assertThat(json).contains("\"configName\":\"myConfig\"").contains(
                "\"orig\":{\"@class\":\"org.springframework.security.authentication.UsernamePasswordAuthenticationToken\"");

        GeorchestraUserNamePasswordAuthenticationToken readToken = mapper.readValue(json,
                GeorchestraUserNamePasswordAuthenticationToken.class);

        assertEquals("myConfig", readToken.getConfigName());
        assertThat(readToken.getAuthorities().size()).isEqualTo(1);
    }
}
