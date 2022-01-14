package org.cryptomator.cloudaccess.vaultformat8;

public class VaultFormat8ProviderConfig {

	private static final int DEFAULT_FILEHEADER_TIMEOUT = 5000;

	private final int fileHeaderCacheTimeoutMillis;

	private VaultFormat8ProviderConfig(){
		this.fileHeaderCacheTimeoutMillis = Integer.getInteger("org.cryptomator.cloudaccess.vaultformat8.fileheadertimeoutMillis", DEFAULT_FILEHEADER_TIMEOUT);
	}

	public static VaultFormat8ProviderConfig createFromSystemProperties(){
		return new VaultFormat8ProviderConfig();
	}

	int getFileHeaderCacheTimeoutMillis() {
		return fileHeaderCacheTimeoutMillis;
	}
}
