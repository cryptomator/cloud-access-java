package org.cryptomator.cloudaccess.vaultformat8;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.cryptomator.cloudaccess.CloudAccess;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.VaultKeyVerificationFailedException;
import org.cryptomator.cloudaccess.api.exceptions.VaultVerificationFailedException;
import org.cryptomator.cloudaccess.api.exceptions.VaultVersionVerificationFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class VaultFormat8IntegrationTest {

	private static final Duration TIMEOUT = Duration.ofMillis(100);

	private CloudProvider localProvider;

	@BeforeEach
	public void setup(@TempDir Path tmpDir) throws IOException {
		this.localProvider = CloudAccess.toLocalFileSystem(tmpDir);
	}

	@Nested
	@DisplayName("with valid /vaultconfig.jwt")
	public class WithInitializedVaultConfig {

		private CloudProvider encryptedProvider;

		@BeforeEach
		public void setup() throws IOException {
			var in = getClass().getResourceAsStream("/vaultconfig.jwt");
			localProvider.write(CloudPath.of("/vaultconfig.jwt"), false, in, in.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();
			this.encryptedProvider = CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), new byte[64]);
		}

		@Test
		@DisplayName("read and write through encryption decorator")
		public void testWriteThenReadFile() throws IOException {
			var path = CloudPath.of("/file.txt");
			var content = new byte[100_000];
			new Random(42l).nextBytes(content);

			// write 100k
			var futureMetadata = encryptedProvider.write(path, true, new ByteArrayInputStream(content), content.length, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
			Assertions.assertTimeoutPreemptively(TIMEOUT, () -> futureMetadata.toCompletableFuture().get());

			// read all bytes
			var futureInputStream1 = encryptedProvider.read(path, ProgressListener.NO_PROGRESS_AWARE);
			var inputStream1 = Assertions.assertTimeoutPreemptively(TIMEOUT, () -> futureInputStream1.toCompletableFuture().get());
			Assertions.assertArrayEquals(content, inputStream1.readAllBytes());

			// read partially
			var futureInputStream2 = encryptedProvider.read(path, 2000, 15000, ProgressListener.NO_PROGRESS_AWARE);
			var inputStream2 = Assertions.assertTimeoutPreemptively(TIMEOUT, () -> futureInputStream2.toCompletableFuture().get());
			Assertions.assertArrayEquals(Arrays.copyOfRange(content, 2000, 17000), inputStream2.readAllBytes());
		}

	}

	@Test
	@DisplayName("init with missing /vaultconfig.jwt fails")
	public void testInstantiateFormat8GCMCloudAccessWithoutVaultConfigFile() {
		Assumptions.assumeFalse(localProvider.exists(CloudPath.of("/vaultconfig.jwt")).toCompletableFuture().join());

		var exception = Assertions.assertThrows(CloudProviderException.class, () -> CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), new byte[64]));
		Assertions.assertTrue(exception.getCause() instanceof NotFoundException);
	}

	@Test
	@DisplayName("init with wrong format")
	public void testInstantiateFormat8GCMCloudAccessWithWrongVaultVersion() {
		Assumptions.assumeFalse(localProvider.exists(CloudPath.of("/vaultconfig.jwt")).toCompletableFuture().join());

		byte[] masterkey = new byte[64];
		Algorithm algorithm = Algorithm.HMAC256(masterkey);
		var token = JWT.create()
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("format", 9)
				.withClaim("cipherCombo", "SIV_GCM")
				.withClaim("shorteningThreshold", Integer.MAX_VALUE)
				.sign(algorithm);
		var in = new ByteArrayInputStream(token.getBytes(StandardCharsets.US_ASCII));
		localProvider.write(CloudPath.of("/vaultconfig.jwt"), false, in, in.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();

		Assertions.assertThrows(VaultVersionVerificationFailedException.class, () -> CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), masterkey));
	}

	@Test
	@DisplayName("init with invalid cipherCombo fails")
	public void testInstantiateFormat8GCMCloudAccessWithWrongCiphermode() {
		Assumptions.assumeFalse(localProvider.exists(CloudPath.of("/vaultconfig.jwt")).toCompletableFuture().join());

		byte[] masterkey = new byte[64];
		Algorithm algorithm = Algorithm.HMAC256(masterkey);
		var token = JWT.create()
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("format", 8)
				.withClaim("cipherCombo", "FOO")
				.withClaim("shorteningThreshold", Integer.MAX_VALUE)
				.sign(algorithm);
		var in = new ByteArrayInputStream(token.getBytes(StandardCharsets.US_ASCII));
		localProvider.write(CloudPath.of("/vaultconfig.jwt"), false, in, in.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();

		Assertions.assertThrows(VaultVerificationFailedException.class, () -> CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), masterkey));
	}

	@Test
	@DisplayName("init with wrong key")
	public void testInstantiateFormat8GCMCloudAccessWithWrongKey() {
		Assumptions.assumeFalse(localProvider.exists(CloudPath.of("/vaultconfig.jwt")).toCompletableFuture().join());

		byte[] masterkey = new byte[64];
		Arrays.fill(masterkey, (byte) 15);
		Algorithm algorithm = Algorithm.HMAC256(masterkey);
		var token = JWT.create()
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("format", 8)
				.withClaim("cipherCombo", "SIV_GCM")
				.withClaim("shorteningThreshold", Integer.MAX_VALUE)
				.sign(algorithm);
		var in = new ByteArrayInputStream(token.getBytes(StandardCharsets.US_ASCII));
		localProvider.write(CloudPath.of("/vaultconfig.jwt"), false, in, in.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();

		Assertions.assertThrows(VaultKeyVerificationFailedException.class, () -> CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), new byte[64]));
	}

	@Test
	@DisplayName("init with shorteningThreshold")
	public void testInstantiateFormat8GCMCloudAccessWithShortening() {
		Assumptions.assumeFalse(localProvider.exists(CloudPath.of("/vaultconfig.jwt")).toCompletableFuture().join());

		byte[] masterkey = new byte[64];
		Algorithm algorithm = Algorithm.HMAC256(masterkey);
		var token = JWT.create()
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("format", 8)
				.withClaim("cipherCombo", "SIV_GCM")
				.withClaim("shorteningThreshold", 42)
				.sign(algorithm);
		var in = new ByteArrayInputStream(token.getBytes(StandardCharsets.US_ASCII));
		localProvider.write(CloudPath.of("/vaultconfig.jwt"), false, in, in.available(), Optional.empty(), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();

		Assertions.assertThrows(VaultVerificationFailedException.class, () -> CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), new byte[64]));
	}

}
