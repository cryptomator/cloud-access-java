package org.cryptomator.cloudaccess.api.exceptions;

/**
 * Indicates that an operation expected a different {@link org.cryptomator.cloudaccess.api.CloudItemType}.
 */
public class TypeMismatchException extends CloudProviderException {

    public TypeMismatchException() {
    }

    public TypeMismatchException(Throwable cause) {
        super(cause);
    }

    public TypeMismatchException(String name) {
        super(name);
    }
}
