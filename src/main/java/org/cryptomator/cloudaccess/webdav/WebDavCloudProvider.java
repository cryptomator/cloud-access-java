package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.Quota;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WebDavCloudProvider implements CloudProvider {

	private final WebDavClient webDavClient;

	private WebDavCloudProvider(final WebDavCredential webDavCredential) {
		var config = WebDavProviderConfig.createFromSystemPropertiesOrDefaults();
		webDavClient = WebDavClient.WebDavAuthenticator.createAuthenticatedWebDavClient(webDavCredential, config);
	}

	public static WebDavCloudProvider from(final WebDavCredential webDavCredential) throws UnauthorizedException, ServerNotWebdavCompatibleException {
		return new WebDavCloudProvider(webDavCredential);
	}

	@Override
	public CompletionStage<CloudItemMetadata> itemMetadata(CloudPath node) {
		return CompletableFuture.supplyAsync(() -> webDavClient.itemMetadata(node));
	}

	@Override
	public CompletionStage<Quota> quota(CloudPath folder) {
		return CompletableFuture.supplyAsync(() -> webDavClient.quota(folder));
	}

	@Override
	public CompletionStage<CloudItemList> list(CloudPath folder, Optional<String> pageToken) {
		return CompletableFuture.supplyAsync(() -> webDavClient.list(folder));
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, ProgressListener progressListener) {
		return CompletableFuture.supplyAsync(() -> webDavClient.read(file, progressListener));
	}

	@Override
	public CompletionStage<InputStream> read(CloudPath file, long offset, long count, ProgressListener progressListener) {
		return CompletableFuture.supplyAsync(() -> webDavClient.read(file, offset, count, progressListener));
	}

	@Override
	public CompletionStage<Void> write(CloudPath file, boolean replace, InputStream data, long size, Optional<Instant> lastModified, ProgressListener progressListener) {
		return CompletableFuture.runAsync(() -> webDavClient.write(file, replace, data, size, lastModified, progressListener));
	}

	@Override
	public CompletionStage<CloudPath> createFolder(CloudPath folder) {
		return CompletableFuture.supplyAsync(() -> webDavClient.createFolder(folder));
	}

	@Override
	public CompletionStage<Void> deleteFile(CloudPath file) {
		return CompletableFuture.runAsync(() -> webDavClient.delete(file));
	}

	@Override
	public CompletionStage<Void> deleteFolder(CloudPath folder) {
		return CompletableFuture.runAsync(() -> webDavClient.delete(folder));
	}

	@Override
	public CompletionStage<CloudPath> move(CloudPath source, CloudPath target, boolean replace) {
		return CompletableFuture.supplyAsync(() -> webDavClient.move(source, target, replace));
	}

}
