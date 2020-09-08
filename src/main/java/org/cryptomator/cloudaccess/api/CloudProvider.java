package org.cryptomator.cloudaccess.api;

import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Common interface of all providers that provide access to a certain cloud.
 * Due to the nature of remotely stored data, every operation is expected to take while to complete. Implementers of this
 * API are expected to immediately return a {@link CompletionStage} (e.g. by facilitating {@link CompletableFuture}) and
 * perform the actual cloud access async. In case of exceptions, it must be guaranteed that the <code>CompletionStage</code>
 * is still completed, e.g. using {@link CompletableFuture#completeExceptionally(Throwable)}.
 * Consumers of this API are expected to handle timeouts when handling the result of a <code>CompletionStage</code>.
 */
public interface CloudProvider {

	/**
	 * Fetches the metadata for a file or folder.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.NotFoundException} If no item exists for the given path</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param node The remote path of the file or folder, whose metadata to fetch.
	 * @return CompletionStage with the metadata for a file or folder. If the fetch fails, it completes exceptionally.
	 */
	CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node);

	/**
	 * Starts fetching the contents of a folder.
	 * If the result's <code>CloudItemList</code> has a <code>nextPageToken</code>, calling this method again with the provided token will continue listing.
	 * If on the other hand the end of the list is reached, <code>nextPageToken</code> will be absent.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.NotFoundException} If no item exists for the given path</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.TypeMismatchException} If the path doesn't represent a folder</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.InvalidPageTokenException} if <code>pageToken</code> is invalid</li>
	 * </ul>
	 *
	 * @param folder    The remote path of the folder to list.
	 * @param pageToken An optional {@link CloudItemList#getNextPageToken() nextPageToken} to continue a previous listing
	 * @return CompletionStage with a potentially incomplete list of items.
	 */
	CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken);

	/**
	 * Convenience wrapper for {@link #list(CloudPath, Optional)} that fetches all items.
	 *
	 * @param folder The remote path of the folder to list.
	 * @return CompletionStage with a complete list of items.
	 * @see #list(CloudPath, Optional)
	 */
	default CompletionStage<CloudItemList> listExhaustively(CloudPath folder) {
		return listExhaustively(folder, CloudItemList.empty());
	}

	private CompletionStage<CloudItemList> listExhaustively(CloudPath folder, CloudItemList itemList) {
		return list(folder, itemList.getNextPageToken()).thenCompose(nextItems -> {
			var combined = itemList.add(nextItems.getItems(), nextItems.getNextPageToken());
			if (nextItems.getNextPageToken().isPresent()) {
				return listExhaustively(folder, combined);
			} else {
				return CompletableFuture.completedStage(combined);
			}
		});
	}

	/**
	 * Reads from the given file.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with the same exceptions as specified in {@link #read(CloudPath, long, long, ProgressListener)}.
	 *
	 * @param file             A remote path referencing a file
	 * @param progressListener TODO Future use
	 * @return CompletionStage with an InputStream to read from. If accessing the file fails, it'll complete exceptionally.
	 * @see #read(CloudPath, long, long, ProgressListener)
	 */
	default CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return read(file, 0, Long.MAX_VALUE, progressListener);
	}

	/**
	 * Reads part of a given file.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.NotFoundException} If no item exists for the given path</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.TypeMismatchException} If the path points to a node that isn't a file</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param file             A remote path referencing a file
	 * @param offset           The first byte (inclusive) to read.
	 * @param count            The number of bytes requested. Can exceed the actual file length. Set to {@link Long#MAX_VALUE} to read till EOF.
	 * @param progressListener TODO Future use
	 * @return CompletionStage with an InputStream to read from. If accessing the file fails, it'll complete exceptionally. If the requested range cannot be fulfilled, an inputstream with 0 bytes is returned
	 */
	CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener);

	/**
	 * Writes to a given file, creating it if it doesn't exist yet. <code>lastModified</code> is applied with best-effort but without guarantee.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.NotFoundException} If the parent directory of this file doesn't exist</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.TypeMismatchException} If the path points to a node that isn't a file</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException} If a node with the given path already exists and <code>replace</code> is false</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param file             A remote path referencing a file
	 * @param replace          Flag indicating whether to overwrite the file if it already exists.
	 * @param data             A data source from which to copy contents to the remote file
	 * @param size             The size of data
	 * @param lastModified     The lastModified which should be provided to the server
	 * @param progressListener TODO Future use
	 * @return CompletionStage that will be completed after writing all <code>data</code>.
	 */
	CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener);

	/**
	 * Create a folder. Does not create any potentially missing parent directories.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException} If a node with the given path already exists</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param folder The remote path of the folder to create.
	 * @return CompletionStage with the same path as <code>folder</code> if created successfully.
	 */
	CompletionStage<CloudPath> createFolder(CloudPath folder);

	/**
	 * Convenience method, which is the same as {@link #createFolder(CloudPath)}, except that it will not fail
	 * in case of an {@link AlreadyExistsException}.
	 *
	 * @param folder The remote path of the folder to create.
	 * @return CompletionStage with the same path as <code>folder</code> if created successfully or already existing.
	 */
	default CompletionStage<CloudPath> createFolderIfNonExisting(CloudPath folder) {
		return createFolder(folder)
				.handle((createdFolder, exception) -> {
					if (exception == null) {
						assert createdFolder != null;
						return CompletableFuture.completedFuture(createdFolder);
					} else if (exception instanceof AlreadyExistsException) {
						return CompletableFuture.completedFuture(folder);
					} else {
						return CompletableFuture.<CloudPath>failedFuture(exception);
					}
				})
				.thenCompose(Function.identity());
	}

	/**
	 * Recursively delete a file or folder.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.NotFoundException} If no item exists for the given path</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param node The remote path of the file or folder to delete.
	 * @return CompletionStage completing successfully if node was deleted.
	 */
	CompletionStage<Void> delete(CloudPath node);

	/**
	 * Move a file or folder to a different location.
	 * <p>
	 * The returned CompletionStage might complete exceptionally with one of the following exceptions:
	 * <ul>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.NotFoundException} If no item exists for the given source path</li>
	 *     <li>{@link org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException} If a node with the given target path already exists and <code>replace</code> is false</li>
	 *     <li>{@link CloudProviderException} in case of generic I/O errors</li>
	 * </ul>
	 *
	 * @param source  The remote path of the file or folder to be moved.
	 * @param target  The remote path of the desired destination.
	 * @param replace Flag indicating whether to overwrite <code>target</code> if it already exists.
	 * @return CompletionStage with the same path as {@code target} if node was moved successfully.
	 */
	CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace);

}

