package org.cryptomator.cloudaccess.api.exceptions;

import com.auth0.jwt.exceptions.JWTVerificationException;

public class VaultVersionVerificationFailedException extends CloudProviderException {

	public VaultVersionVerificationFailedException(JWTVerificationException jWTVerificationException) {
		super(jWTVerificationException);
	}

	public VaultVersionVerificationFailedException(String name) {
		super(name);
	}
}
