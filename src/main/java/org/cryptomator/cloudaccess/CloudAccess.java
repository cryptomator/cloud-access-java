package org.cryptomator.cloudaccess;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.google.common.base.Preconditions;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.VaultKeyVerificationFailedException;
import org.cryptomator.cloudaccess.api.exceptions.VaultVerificationFailedException;
import org.cryptomator.cloudaccess.api.exceptions.VaultVersionVerificationFailedException;
import org.cryptomator.cloudaccess.localfs.LocalFsCloudProvider;
import org.cryptomator.cloudaccess.vaultformat8.VaultFormat8ProviderDecorator;
import org.cryptomator.cloudaccess.webdav.WebDavCloudProvider;
import org.cryptomator.cloudaccess.webdav.WebDavCredential;
import org.cryptomator.cryptolib.Cryptors;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;

public class CloudAccess {

	private CloudAccess() {
	}

	/**
	 * Decorates an existing CloudProvider by encrypting paths and file contents using Cryptomator's Vault Format 8.
	 * Uses an externally managed masterkey, i.e. it will only validate the vault version but not parse any vault config.
	 * <p>
	 * This method might return with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.VaultVersionVerificationFailedException} If the version of the vault config isn't 8</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.VaultKeyVerificationFailedException} If <code>rawKey</code> doesn't match the key used to create the vault config</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.VaultVerificationFailedException} If the <code>ciphermode</code> in the vault config doesn't match SIV_GCM</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.CloudProviderException} If the general error occurred</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param cloudProvider A CloudProvider providing access to a storage space on which to store ciphertext data
	 * @param pathToVault   Path that can be used within the given <code>cloudProvider</code> leading to the vault's root
	 * @param rawKey        512 bit key used for cryptographic operations
	 * @return A cleartext view on the given CloudProvider
	 */
	public static CloudProvider vaultFormat8GCMCloudAccess(CloudProvider cloudProvider, CloudPath pathToVault, byte[] rawKey) {
		Preconditions.checkArgument(rawKey.length == 64, "masterkey needs to be 512 bit");

		try {
			var csprng = SecureRandom.getInstanceStrong();
			var cryptor = Cryptors.version2(csprng).createFromRawKey(rawKey);

			verifyVaultFormat8GCMConfig(cloudProvider, pathToVault, rawKey);

			VaultFormat8ProviderDecorator provider = new VaultFormat8ProviderDecorator(cloudProvider, pathToVault.resolve("d"), cryptor);
			provider.initialize();
			return new MetadataCachingProviderDecorator(provider);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM doesn't supply a CSPRNG", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CloudProviderException("Vault initialization interrupted.", e);
		}
	}

	private static void verifyVaultFormat8GCMConfig(CloudProvider cloudProvider, CloudPath pathToVault, byte[] rawKey) {
		var vaultConfigPath = pathToVault.resolve("vaultconfig.jwt");
		var algorithm = Algorithm.HMAC256(rawKey);
		var verifier = JWT.require(algorithm)
				.withClaim("format", 8)
				.withClaim("cipherCombo", "SIV_GCM")
				.build();

		var read = cloudProvider.read(vaultConfigPath, ProgressListener.NO_PROGRESS_AWARE);
		try (var in = read.toCompletableFuture().join()) {
			var vaultConfigContents = in.readAllBytes();
			var token = new String(vaultConfigContents, StandardCharsets.US_ASCII);
			verifier.verify(token);
		} catch (SignatureVerificationException e) {
			throw new VaultKeyVerificationFailedException(e);
		} catch (JWTVerificationException e) {
			if (e.getMessage().equals("The Claim 'format' value doesn't match the required one.")) {
				throw new VaultVersionVerificationFailedException(e);
			} else {
				throw new VaultVerificationFailedException(e);
			}
		} catch (CloudProviderException | IOException e) {
			throw new CloudProviderException(e);
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
