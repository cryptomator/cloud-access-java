package org.cryptomator.cloudaccess.localfs;

import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.Quota;
import org.cryptomator.cloudaccess.api.exceptions.AlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.CloudProviderException;
import org.cryptomator.cloudaccess.api.exceptions.NotFoundException;
import org.cryptomator.cloudaccess.api.exceptions.TypeMismatchException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CloudProvider implementation to mirror a folder in the local filesystem.
 * <p>
 * This class is mainly for testing purposes and therefore aims for correctness, not performance.
 * All filesystem altering operations (create, delete, move and write) will be executed exclusively and blocking,
 * while all fs quering operations are performed simultanously.
 */
public class LocalFsCloudProvider implements CloudProvider {

	private static final CloudPath ABS_ROOT = CloudPath.of("/");

	private final Path root;

	/**
	 * Lock to ensure that any operation always performed on a consistent filesystem, i.e. no pending fs-altering operation exists.
	 */
	private final ReadWriteLock lock;

	public LocalFsCloudProvider(Path root) {
		this.root = root;
		this.lock = new ReentrantReadWriteLock();
	}

	private Path resolve(CloudPath cloudPath) {
		String relPath = ABS_ROOT.relativize(cloudPath).toString();
		return root.resolve(relPath);
	}

	private CloudItemMetadata createMetadata(Path fullPath, BasicFileAttributes attr) {
		var relPath = root.relativize(fullPath);
		var type = attr.isDirectory() ? CloudItemType.FOLDER : attr.isRegularFile() ? CloudItemType.FILE : CloudItemType.UNKNOWN;
		var modifiedDate = Optional.of(attr.lastModifiedTime().toInstant());
		var size = Optional.of(attr.size());
		return new CloudItemMetadata(relPath.getFileName().toString(), ABS_ROOT.resolve(relPath.toString()), type, modifiedDate, size);
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		Path path = resolve(node);
		Lock l = lock.readLock();
		l.lock();
		try {
			var attr = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			var metadata = createMetadata(path, attr);
			return CompletableFuture.completedFuture(metadata);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		var file = resolve(folder).toFile();
		var availableBytes = file.getFreeSpace();
		var totalBytes = file.getTotalSpace();
		return CompletableFuture.completedFuture(new Quota(availableBytes, Optional.of(totalBytes), Optional.empty()));
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		Path folderPath = resolve(folder);
		Lock l = lock.readLock();
		l.lock();
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
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		Path filePath = resolve(file);
		Lock l = lock.readLock();
		l.lock();
		try {
			var ch = Files.newByteChannel(filePath, StandardOpenOption.READ);
			ch.position(offset);
			return CompletableFuture.completedFuture(ByteStreams.limit(Channels.newInputStream(ch), count));
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		Path filePath = resolve(file);
		var options = replace
				? EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
				: EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

		Lock l = lock.writeLock();
		l.lock();
		try (var ch = FileChannel.open(filePath, options)) {
			var written = ch.transferFrom(Channels.newChannel(data), 0, Long.MAX_VALUE);
			assert size == written : "Written bytes should be equal to provided size";
			if (lastModified.isPresent()) {
				Files.setLastModifiedTime(filePath, FileTime.from(lastModified.get()));
			}
			return CompletableFuture.completedFuture(null);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (FileAlreadyExistsException e) {
			return CompletableFuture.failedFuture(new AlreadyExistsException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		Path folderPath = resolve(folder);
		Lock l = lock.writeLock();
		l.lock();
		try {
			Files.createDirectory(folderPath);
			return CompletableFuture.completedFuture(folder);
		} catch (FileAlreadyExistsException e) {
			return CompletableFuture.failedFuture(new AlreadyExistsException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<Void> deleteFile(CloudPath file) {
		Path path = resolve(file);
		Lock l = lock.writeLock();
		l.lock();
		try {
			Files.delete(path);
			return CompletableFuture.completedFuture(null);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<Void> deleteFolder(CloudPath folder) {
		Path path = resolve(folder);
		Lock l = lock.writeLock();
		l.lock();
		try {
			MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
			return CompletableFuture.completedFuture(null);
		} catch (NoSuchFileException e) {
			return CompletableFuture.failedFuture(new NotFoundException(e));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(new CloudProviderException(e));
		} finally {
			l.unlock();
		}
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		Path src = resolve(source);
		Path dst = resolve(target);
		Lock l = lock.writeLock();
		l.lock();
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
		} finally {
			l.unlock();
		}
	}

	@Override
	public boolean cachingCapability() {
		return true;
	}
}
