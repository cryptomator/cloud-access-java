package org.cryptomator.cloudaccess.localfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.TypeMismatchException;

public class LocalFsCloudProvider implements CloudProvider {

	private static final Path ABS_ROOT = Path.of("/");

	private final Path root;

	public LocalFsCloudProvider(Path root) {
		this.root = root;
	}

	private Path resolve(Path cloudPath) {
		Path relPath = ABS_ROOT.relativize(cloudPath);
		return root.resolve(relPath);
	}

	private CloudItemMetadata createMetadata(Path fullPath, BasicFileAttributes attr) {
		var relPath = root.relativize(fullPath);
		var type = attr.isDirectory() ? CloudItemType.FOLDER : attr.isRegularFile() ? CloudItemType.FILE : CloudItemType.UNKNOWN;
		var modifiedDate = Optional.of(attr.lastModifiedTime().toInstant());
		var size = Optional.of(attr.size());
		return new CloudItemMetadata(relPath.getFileName().toString(), ABS_ROOT.resolve(relPath), type, modifiedDate, size);
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
		Path path = resolve(node);
		try {
			var attr = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			var metadata = createMetadata(path, attr);
			return CompletableFuture.completedFuture(metadata);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}

	@Override
	public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
		Path folderPath = resolve(folder);
		try {
			List<CloudItemMetadata> items = new ArrayList<>();
			Files.walkFileTree(folderPath, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					items.add(createMetadata(file, attrs));
					return FileVisitResult.CONTINUE;
				}
			});
			return CompletableFuture.completedFuture(new CloudItemList(items, Optional.empty()));
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (NotDirectoryException e) {
			return CompletableFuture.failedFuture(new TypeMismatchException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}

	@Override
	public CompletionStage<InputStream> read(Path file, long offset, long count, ProgressListener progressListener) {
		Path filePath = resolve(file);
		try {
			var ch = Files.newByteChannel(filePath, StandardOpenOption.READ);
			ch.position(offset);
			return CompletableFuture.completedFuture(ByteStreams.limit(Channels.newInputStream(ch), count));
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}

	@Override
	public CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener) {
		Path filePath = resolve(file);
		var options = replace
				? EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
				: EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

		try (var ch = FileChannel.open(filePath, options)) {
			var size = ch.transferFrom(Channels.newChannel(data), 0, Long.MAX_VALUE);
			var modifiedDate = Files.getLastModifiedTime(filePath).toInstant();
			var metadata = new CloudItemMetadata(file.getFileName().toString(), file, CloudItemType.FILE, Optional.of(modifiedDate), Optional.of(size));
			return CompletableFuture.completedFuture(metadata);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (FileAlreadyExistsException e) {
			return CompletableFuture.failedFuture(new AlreadyExistsException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}

	@Override
	public CompletionStage<Path> createFolder(Path folder) {
		Path folderPath = resolve(folder);
		try {
			Files.createDirectory(folderPath);
			return CompletableFuture.completedFuture(folder);
		} catch (FileAlreadyExistsException e) {
			return CompletableFuture.failedFuture(new AlreadyExistsException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}

	@Override
	public CompletionStage<Void> delete(Path node) {
		Path path = resolve(node);
		try {
			MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
			return CompletableFuture.completedFuture(null);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}

	@Override
	public CompletionStage<Path> move(Path source, Path target, boolean replace) {
		Path src = resolve(source);
		Path dst = resolve(target);
		try {
			var options = replace ? EnumSet.of(StandardCopyOption.REPLACE_EXISTING) : EnumSet.noneOf(StandardCopyOption.class);
			Files.move(src, dst, options.toArray(CopyOption[]::new));
			return CompletableFuture.completedFuture(target);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (FileAlreadyExistsException e) {
			return CompletableFuture.failedFuture(new AlreadyExistsException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		}
	}
}
