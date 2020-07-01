package org.cryptomator.cloudaccess.api;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Package-private utility used from default-methods in {@link CloudProvider},
 * since we can't use private static methods in interfaces in Java 8.
 */
class CloudProviderHelper {

	static CompletionStage<CloudItemList> listExhaustively(CloudProvider provider, Path folder, CloudItemList itemList) {
		return provider.list(folder, itemList.getNextPageToken()).thenCompose(nextItems -> {
			CloudItemList combined = itemList.add(nextItems.getItems(), nextItems.getNextPageToken());
			if (nextItems.getNextPageToken().isPresent()) {
				return listExhaustively(provider, folder, combined);
			} else {
				return CompletableFuture.completedFuture(combined);
			}
		});
	}

}
