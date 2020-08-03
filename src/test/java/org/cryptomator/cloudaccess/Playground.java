package org.cryptomator.cloudaccess;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.cryptomator.cloudaccess.api.CloudItemType.FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class Playground {

	private final CloudProvider cloudProvider = Mockito.mock(CloudProvider.class);

	@Test
	public void test() {
		final var cloudItemMetadata = new CloudItemMetadata("name", CloudPath.of("/path"), FILE, Optional.empty(), Optional.empty());

		when(cloudProvider.createFolder(any())).thenAnswer(folder -> CompletableFuture.supplyAsync(() -> folder));

		final var result = cloudProvider.createFolder(CloudPath.of("/foo/bar"))
				.thenCompose(cloudProvider::delete)
				.thenCompose((node) -> {
					throw new RuntimeException("e");
				})
				.handle((s, t) -> "Foo")
				.toCompletableFuture()
				.join();

		assertEquals(result, "Foo");

		final var demoCloudProvider = new DemoCloudProvider();
		demoCloudProvider.read(cloudItemMetadata.getPath(), System.out::println);
	}


	private static class DemoCloudProvider implements CloudProvider {

		@Override
		public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
			return null;
		}

		@Override
		public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
			return null;
		}

		@Override
		public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
			progressListener.onProgress(12);
			progressListener.onProgress(52);
			return null;
		}

		@Override
		public CompletionStage<CloudItemMetadata> write(CloudPath file, boolean replace, InputStream data, ProgressListener progressListener) {
			return null;
		}

		@Override
		public CompletionStage<CloudPath> createFolder(CloudPath folder) {
			return null;
		}

		@Override
		public CompletionStage<Void> delete(CloudPath node) {
			return null;
		}

		@Override
		public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
			return null;
		}
	}
}