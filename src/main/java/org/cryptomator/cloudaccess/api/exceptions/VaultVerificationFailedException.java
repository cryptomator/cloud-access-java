package org.cryptomator.cloudaccess.api.exceptions;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;

public class VaultVerificationFailedException extends CloudProviderException {

	public VaultVerificationFailedException(SignatureVerificationException signatureVerificationException) {
		super(signatureVerificationException);
	}

	public VaultVerificationFailedException(JWTVerificationException jWTVerificationException) {
		super(jWTVerificationException);
	}
}
