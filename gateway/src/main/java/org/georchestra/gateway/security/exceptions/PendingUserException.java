package org.georchestra.gateway.security.exceptions;

import org.springframework.security.core.AuthenticationException;

@SuppressWarnings("serial")
public class PendingUserException extends AuthenticationException {
    /**
     * Constructs a new {@code PendingUserException} with the specified detail
     * message.
     *
     * @param message the detail message
     */
    public PendingUserException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code PendingUserException} without a detail message.
     */
    public PendingUserException() {
        this("User is pending validation");
    }
}
