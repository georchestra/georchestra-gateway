package org.georchestra.gateway.security.exceptions;

public class DuplicatedUsernameFoundException extends RuntimeException {
    private String message;

    public DuplicatedUsernameFoundException(String message) {
        super(message);
        this.message = message;
    }

    public DuplicatedUsernameFoundException() {
    }
}
