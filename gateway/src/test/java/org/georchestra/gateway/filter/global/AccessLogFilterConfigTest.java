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

package org.georchestra.gateway.filter.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 */
class AccessLogFilterConfigTest {

    private AccessLogFilterConfig config;

    @BeforeEach
    void setUp() {
        config = new AccessLogFilterConfig();
    }

    @Test
    void testDisabled() {
        config.setEnabled(false);
        assertThat(config.matches(URI.create("https://my.host"))).isTrue();

        config.getExclude().add(Pattern.compile(".*"));
        assertThat(config.matches(URI.create("https://my.host"))).isTrue();
    }

    @Test
    void testIncludes() {
        config.getInclude().add(Pattern.compile(".*/ows/.*GetFeature.*"));
        config.getInclude().add(Pattern.compile(".*/ows/.*GetMap.*"));

        assertThat(config.matches(URI.create("https://my.host/ows/?request=GetFeature&typeName=test"))).isTrue();
        assertThat(config.matches(URI.create("https://my.host/ows/?request=GetMap&typeName=test"))).isTrue();
        assertThat(config.matches(URI.create("https://my.host/some/path/img1.svg"))).isFalse();
    }

    @Test
    void testExcludes() {
        config.getExclude().add(Pattern.compile(".*\\.png"));
        config.getExclude().add(Pattern.compile(".*\\.jpeg"));

        assertThat(config.matches(URI.create("https://my.host/some/path/img1.png"))).isFalse();
        assertThat(config.matches(URI.create("https://my.host/some/path/img1.svg"))).isTrue();

        assertThat(config.matches(URI.create("https://my.host/some/path/img2.jpeg"))).isFalse();
        assertThat(config.matches(URI.create("https://my.host/some/path/img2.svg"))).isTrue();
    }

    @Test
    void testIncludesAndExcludes() {
        config.getInclude().add(Pattern.compile(".*/ows/.*"));
        config.getExclude().add(Pattern.compile(".*/ows/.*GetMap.*"));

        assertThat(config.matches(URI.create("https://my.host/ows/?request=GetFeature&typeName=test"))).isTrue();
        assertThat(config.matches(URI.create("https://my.host/ows/?request=GetMap&typeName=test"))).isFalse();
    }
}
