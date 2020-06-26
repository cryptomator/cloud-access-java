package org.cryptomator.CloudAccess.Core;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CloudProvider {

	CompletionStage<CloudItemMetadata> itemMetadata(Path node);

	CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken);

	CompletionStage<OutputStream> read(Path file, ProgressListener progressListener);

	CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener);

	CompletionStage<Path> createFolder(Path folder);

	CompletionStage<Void> delete(Path node);

	CompletionStage<Path> move(Path source, Path target, boolean replace);

}

