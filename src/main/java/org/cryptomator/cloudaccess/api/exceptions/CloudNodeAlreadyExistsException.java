package org.cryptomator.cloudaccess.api.exceptions;

public class CloudNodeAlreadyExistsException extends BackendException {

    public CloudNodeAlreadyExistsException(String name) {
        super(name);
    }

}
