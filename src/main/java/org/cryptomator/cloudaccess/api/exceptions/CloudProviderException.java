package org.cryptomator.cloudaccess.api.exceptions;

import java.util.concurrent.CompletionException;

public class CloudProviderException extends CompletionException {

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
