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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.ldap.extended;

import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.security.model.Organization;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;

/**
 * An extended version of {@link GeorchestraUser} that includes an associated
 * {@link Organization}.
 * <p>
 * This class wraps an existing {@link GeorchestraUser} instance while adding an
 * {@link #org} property, which represents the user's resolved organization.
 * This is useful for systems where user information is stored separately from
 * organizational details.
 * </p>
 *
 * <h3>Example Usage:</h3>
 * 
 * <pre>
 * {
 *     &#64;code
 *     GeorchestraUser baseUser = new GeorchestraUser();
 *     baseUser.setUsername("jdoe");
 * 
 *     Organization org = new Organization();
 *     org.setName("GeoOrg");
 * 
 *     ExtendedGeorchestraUser extendedUser = new ExtendedGeorchestraUser(baseUser);
 *     extendedUser.setOrg(org);
 * 
 *     System.out.println(extendedUser.getUsername()); // Inherited from GeorchestraUser
 *     System.out.println(extendedUser.getOrg().getName()); // "GeoOrg"
 * }
 * </pre>
 *
 * <h3>Key Features:</h3>
 * <ul>
 * <li>Delegates all calls to the wrapped {@link GeorchestraUser} instance.</li>
 * <li>Allows seamless access to user properties while adding an
 * {@link Organization} field.</li>
 * <li>Uses {@link JsonIgnore} to prevent unnecessary serialization of sensitive
 * fields.</li>
 * <li>Overrides {@link #equals(Object)} and {@link #hashCode()} to ensure
 * consistent equality checks.</li>
 * </ul>
 */
@SuppressWarnings("serial")
@RequiredArgsConstructor
@Accessors(chain = true)
public class ExtendedGeorchestraUser extends GeorchestraUser {

    /**
     * The underlying {@link GeorchestraUser} instance, which is fully delegated.
     */
    @JsonIgnore
    private final @NonNull @Delegate GeorchestraUser user;

    /**
     * The organization associated with this user.
     */
    @JsonIgnore
    private @Getter @Setter Organization org;

    /**
     * Compares this user to another object based on the properties of the
     * underlying {@link GeorchestraUser}.
     *
     * @param o the object to compare against
     * @return {@code true} if the object is a {@link GeorchestraUser} and has the
     *         same properties, otherwise {@code false}
     */
    public @Override boolean equals(Object o) {
        if (!(o instanceof GeorchestraUser)) {
            return false;
        }
        return super.equals(o);
    }

    /**
     * Computes the hash code based on the underlying {@link GeorchestraUser}.
     *
     * @return the hash code value for this user
     */
    public @Override int hashCode() {
        return super.hashCode();
    }

    /**
     * The orgUniqueId associated with this user.
     */
    @JsonIgnore
    private @Getter @Setter String orgUniqueId;
}
