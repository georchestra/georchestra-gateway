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
package org.georchestra.gateway.security.exceptions;

import org.springframework.security.core.AuthenticationException;

/**
 * Exception thrown when multiple user accounts are found with the same
 * username.
 * <p>
 * This exception is used to indicate a conflict in user identity resolution,
 * typically occurring during authentication or user synchronization processes.
 * </p>
 */
@SuppressWarnings("serial")
public class DuplicatedUsernameFoundException extends AuthenticationException {

    /**
     * Constructs a new {@code DuplicatedUsernameFoundException} with the specified
     * detail message.
     *
     * @param message the detail message
     */
    public DuplicatedUsernameFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DuplicatedUsernameFoundException} without a detail
     * message.
     */
    public DuplicatedUsernameFoundException() {
        super("Duplicate username");
    }
}
