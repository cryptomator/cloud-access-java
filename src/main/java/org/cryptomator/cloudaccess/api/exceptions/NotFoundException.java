package org.cryptomator.cloudaccess.api.exceptions;

public class NotFoundException extends BackendException {

    public NotFoundException() {
    }

    public NotFoundException(String name) {
        super(name);
    }
}
