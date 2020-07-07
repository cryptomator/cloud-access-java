package org.cryptomator.cloudaccess.api;

public class InvalidPageTokenException extends IllegalArgumentException {
    public InvalidPageTokenException(String message) {
        super(message);
    }

    public InvalidPageTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
