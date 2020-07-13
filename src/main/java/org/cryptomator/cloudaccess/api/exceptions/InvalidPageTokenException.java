package org.cryptomator.cloudaccess.api.exceptions;

public class InvalidPageTokenException extends CloudProviderException {
    public InvalidPageTokenException(String message) {
        super(message);
    }

    public InvalidPageTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
