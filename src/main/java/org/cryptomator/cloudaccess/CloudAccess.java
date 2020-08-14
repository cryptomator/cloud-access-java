package org.cryptomator.cloudaccess;

import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.localfs.LocalFsCloudProvider;
import org.cryptomator.cloudaccess.vaultformat8.VaultFormat8ProviderDecorator;
import org.cryptomator.cloudaccess.webdav.WebDavCloudProvider;
import org.cryptomator.cloudaccess.webdav.WebDavCredential;
import org.cryptomator.cryptolib.Cryptors;

import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CloudAccess {

	private CloudAccess() {
	}

	/**
	 * Decorates an existing CloudProvider by encrypting paths and file contents using Cryptomator's Vault Format 8.
	 * Uses an externally managed masterkey, i.e. it will only validate the vault version but not parse any vault config.
	 *
	 * @param cloudProvider A CloudProvider providing access to a storage space on which to store ciphertext data
	 * @param pathToVault	Path that can be used within the given <code>cloudProvider</code> leading to the vault's root
	 * @param rawKey        512 bit key used for cryptographic operations
	 * @return A cleartext view on the given CloudProvider
	 */
	public static CloudProvider vaultFormat8GCMCloudAccess(CloudProvider cloudProvider, CloudPath pathToVault, byte[] rawKey) {
		try {
			var csprng = SecureRandom.getInstanceStrong();
			var cryptor = Cryptors.version2(csprng).createFromRawKey(rawKey);
			// TODO validate vaultFormat.jwt before creating decorator
			return new VaultFormat8ProviderDecorator(cloudProvider, pathToVault.resolve("d"), cryptor);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM doesn't supply a CSPRNG", e);
		}
	}

	/**
	 * Creates a new CloudProvider which provides access to the given URL via WebDAV.
	 *
	 * @param url      Base URL leading to the root resource
	 * @param username Username used during basic or digest auth challenges
	 * @param password Password used during basic or digest auth challenges
	 * @return A cloud access provider that provides access to the given WebDAV URL
	 */
	public static CloudProvider toWebDAV(URL url, String username, CharSequence password) {
		// TODO can we pass though CharSequence to the auth mechanism?
		return WebDavCloudProvider.from(WebDavCredential.from(url, username, password.toString()));
	}

	/**
	 * Creates a new CloudProvider which provides access to the given <code>folder</code>. Mainly for test purposes.
	 *
	 * @param folder An existing folder on the (local) default file system
	 * @return A cloud access provider that provides access to the given local directory
	 */
	public static CloudProvider toLocalFileSystem(Path folder) {
		return new LocalFsCloudProvider(folder);
	}

}
