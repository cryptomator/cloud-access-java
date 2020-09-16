package org.cryptomator.cloudaccess.api.exceptions;

import com.auth0.jwt.exceptions.JWTVerificationException;

public class VaultKeyVerificationFailedException extends CloudProviderException {

	public VaultKeyVerificationFailedException(JWTVerificationException jWTVerificationException) {
		super(jWTVerificationException);
	}

	public VaultKeyVerificationFailedException(String name) {
		super(name);
	}
}
