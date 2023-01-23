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

import java.util.function.Function;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.core.Ordered;

/**
 * Extension point to customize the state of a {@link GeorchestraUser} once it
 * was obtained from an authentication provider by means of a
 * {@link GeorchestraUserMapperExtension}.
 * 
 * @see GeorchestraUserMapper
 */
public interface GeorchestraUserCustomizerExtension extends Ordered, Function<GeorchestraUser, GeorchestraUser> {

    default int getOrder() {
        return 0;
    }
}
