package org.cryptomator.cloudaccess.api.exceptions;

public class AlreadyExistsException extends CloudProviderException {

    public AlreadyExistsException(Throwable cause) {
        super(cause);
    }

    public AlreadyExistsException(String name) {
        super(name);
    }
}
