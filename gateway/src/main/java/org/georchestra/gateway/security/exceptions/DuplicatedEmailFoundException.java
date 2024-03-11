package org.georchestra.gateway.security.exceptions;

public class DuplicatedEmailFoundException extends RuntimeException {
    private String message;

    public DuplicatedEmailFoundException(String message) {
        super(message);
        this.message = message;
    }

    public DuplicatedEmailFoundException() {
    }
}
