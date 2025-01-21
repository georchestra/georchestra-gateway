/*
 * Copyright (C) 2024 by the geOrchestra PSC
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
package org.georchestra.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HeaderMappingsTest {

    @Test
    void copy() {
        assertCopy(new HeaderMappings());
        assertCopy(new HeaderMappings().enableAll());
        assertCopy(new HeaderMappings().disableAll());
    }

    @Test
    void merge() {
        HeaderMappings a = new HeaderMappings();
        HeaderMappings b = new HeaderMappings().enableAll();
        HeaderMappings merged = a.merge(b);
        assertThat(merged).isSameAs(a).isEqualTo(b);

        a.userid(false);
        a.jsonUser(false);

        b.merge(a);
        assertThat(b).isEqualTo(a);

        a = new HeaderMappings();
        b = new HeaderMappings();
        a.userid(false);
        b.jsonUser(true);

        HeaderMappings expected = a.copy().jsonUser(true);
        assertThat(a.merge(b)).isEqualTo(expected);
    }

    private void assertCopy(HeaderMappings orig) {
        HeaderMappings copy = orig.copy();
        assertThat(copy).isNotSameAs(orig).isEqualTo(orig);
    }

}
