package org.cryptomator.cloudaccess.api.exceptions;

public class NotFoundException extends CloudProviderException {

    public NotFoundException() {
    }

    public NotFoundException(Throwable cause) {
        super(cause);
    }

    public NotFoundException(String name) {
        super(name);
    }
}
