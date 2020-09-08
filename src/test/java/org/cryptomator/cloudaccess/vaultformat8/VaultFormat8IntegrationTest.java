package org.cryptomator.cloudaccess.vaultformat8;

import org.cryptomator.cloudaccess.CloudAccess;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public class VaultFormat8IntegrationTest {

	private static final Duration TIMEOUT = Duration.ofMillis(100);

	private CloudProvider localProvider;
	private CloudProvider encryptedProvider;

	@BeforeEach
	public void setup(@TempDir Path tmpDir) {
		this.localProvider = CloudAccess.toLocalFileSystem(tmpDir);
		this.encryptedProvider = CloudAccess.vaultFormat8GCMCloudAccess(localProvider, CloudPath.of("/"), new byte[64]);
	}

	@Test
	public void testWriteThenReadFile() throws IOException {
		var path = CloudPath.of("/file.txt");
		var content = new byte[100_000];
		new Random(42l).nextBytes(content);

		// write 100k
		var futureMetadata = encryptedProvider.write(path, true, new ByteArrayInputStream(content), content.length, Optional.empty(), ProgressListener.NO_PROGRESS_AWARE);
		var metadata = Assertions.assertTimeoutPreemptively(TIMEOUT, () -> futureMetadata.toCompletableFuture().get());
		Assertions.assertEquals(content.length, metadata.getSize().get());

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
