package org.cryptomator.cloudaccess.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface CloudProvider {

	/**
	 * Fetches the metadata for a file or folder.
	 *
	 * @param node The remote path of the file or folder, whose metadata to fetch.
	 * @return CompletionStage with the metadata for a file or folder. If the fetch fails, it completes exceptionally.
	 */
	CompletionStage<CloudItemMetadata> itemMetadata(Path node);

	/**
	 * Starts fetching the contents of a folder.
	 * If the result's <code>CloudItemList</code> has a <code>nextPageToken</code>, calling this method again with the provided token will continue listing.
	 * If on the other hand the end of the list is reached, <code>nextPageToken</code> will be absent.
	 *
	 * @param folder The remote path of the folder to list.
	 * @param pageToken An optional {@link CloudItemList#getNextPageToken() nextPageToken} to continue a previous listing
	 * @return CompletionStage with a potentially incomplete list of items.
	 */
	CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken);

	/**
	 * Convenience wrapper for {@link #list(Path, Optional)} that fetches all items.
	 * @param folder The remote path of the folder to list.
	 * @return CompletionStage with a complete list of items.
	 */
	default CompletionStage<CloudItemList> listExhaustively(Path folder) {
		return CloudProviderHelper.listExhaustively(this, folder, CloudItemList.empty());
	}

	/**
	 * Reads from the given file.
	 * @param file A remote path referencing a file 
	 * @param progressListener TODO Future use
	 * @return CompletionStage with an InputStream to read from. If accessing the file fails, it'll complete exceptionally.
	 */
	default CompletionStage<InputStream> read(Path file, ProgressListener progressListener) {
		return read(file, 0, Long.MAX_VALUE, progressListener);
	}

	/**
	 * Reads part of a given file.
	 * @param file A remote path referencing a file 
	 * @param offset The first byte (inclusive) to read.
	 * @param count The number of bytes requested. Can exceed the actual file length. Set to {@link Long#MAX_VALUE} to read till EOF.
	 * @param progressListener TODO Future use
	 * @return CompletionStage with an InputStream to read from. If accessing the file fails, it'll complete exceptionally.
	 */
	CompletionStage<InputStream> read(Path file, long offset, long count, ProgressListener progressListener);

	/**
	 * Writes to a given file.
	 * @param file A remote path referencing a file 
	 * @param replace Flag indicating whether to overwrite the file if it already exists.
	 * @param data A data source from which to copy contents to the remote file
	 * @param progressListener TODO Future use
	 * @return CompletionStage that will be completed after writing all <code>data</code> and holds the new metadata of the item referenced by <code>file</code>.
	 */
	CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener);

	/**
	 * Create a folder.
	 * 
	 * @param folder The remote path of the folder to create.
	 * @return CompletionStage with the same path as <code>folder</code> if created successfully.
	 */
	CompletionStage<Path> createFolder(Path folder);

	/**
	 * Recursively delete a file or folder.
	 * 
	 * @param node The remote path of the file or folder to delete.
	 * @return CompletionStage completing successfully if node was deleted.
	 */
	CompletionStage<Void> delete(Path node);

	/**
	 * Move a file or folder to a different location.
	 * 
	 * @param source The remote path of the file or folder to be moved.
	 * @param target The remote path of the desired destination.
	 * @param replace Flag indicating whether to overwrite <code>target</code> if it already exists.
	 * @return CompletionStage completing successfully if node was moved.
	 */
	CompletionStage<Path> move(Path source, Path target, boolean replace);

}

