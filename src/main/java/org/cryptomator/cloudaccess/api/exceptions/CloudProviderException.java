package org.cryptomator.cloudaccess.api.exceptions;

public class CloudProviderException extends RuntimeException {

    public CloudProviderException() {
        super();
    }

    public CloudProviderException(Throwable e) {
        super(e);
    }

    public CloudProviderException(String message) {
        super(message);
    }

    public CloudProviderException(String message, Throwable e) {
        super(message, e);
    }

}
