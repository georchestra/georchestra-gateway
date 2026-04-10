
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.orgresolvers;

import java.util.Optional;

/**
 * SPI interface for resolving an organization name from an identifier (e.g. a
 * SIRET number).
 * <p>
 * This is designed to be API-agnostic: the SIRENE (INSEE) implementation is the
 * first adapter, but any country or system could provide its own implementation
 * to resolve organization names from national registries.
 * </p>
 */
public interface OrganizationNameResolver {

    /**
     * Attempts to resolve the organization name from the given identifier.
     *
     * @param organizationIdentifier the identifier to look up (e.g. a SIRET number)
     * @return an {@link Optional} containing the resolved organization details, or
     *         {@link Optional#empty()} if the identifier could not be resolved
     */
    Optional<ResolvedOrganization> resolve(String organizationIdentifier);
}
