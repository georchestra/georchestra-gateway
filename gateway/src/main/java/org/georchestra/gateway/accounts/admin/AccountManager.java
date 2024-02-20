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
package org.georchestra.gateway.accounts.admin;

import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.ResolveGeorchestraUserGlobalFilter;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * @see CreateAccountUserCustomizer
 * @see ResolveGeorchestraUserGlobalFilter
 */
public interface AccountManager {

    /**
     * Finds the stored user that belongs to the {@code mappedUser} if it exists
     *
     * @param mappedUser the user {@link ResolveGeorchestraUserGlobalFilter}
     *                   resolved by calling
     *                   {@link GeorchestraUserMapper#resolve(Authentication)}
     * @return the stored version of the user if it exists, otherwise an empty
     *         Optional
     */
    Optional<GeorchestraUser> find(GeorchestraUser mappedUser);

    /**
     * Finds the stored user that belongs to the {@code mappedUser} or creates it if
     * it doesn't exist in the users repository.
     * <p>
     * When a user is created, an {@link AccountCreated} event must be published to
     * the {@link ApplicationEventPublisher}.
     * 
     * @param mappedUser the user {@link ResolveGeorchestraUserGlobalFilter}
     *                   resolved by calling
     *                   {@link GeorchestraUserMapper#resolve(Authentication)}
     * @return the stored version of the user, whether it existed or was created as
     *         the result of calling this method.
     */
    GeorchestraUser getOrCreate(GeorchestraUser mappedUser);

}
