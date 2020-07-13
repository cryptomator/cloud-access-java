package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class WebDavCloudProvider implements CloudProvider {

    private final WebDavClient webDavClient;

    private WebDavCloudProvider(final WebDavCredential webDavCredential) {
        webDavClient = WebDavClient.WebDavAuthenticator.createAuthenticatedWebDavClient(webDavCredential);
    }

    public static WebDavCloudProvider from(final WebDavCredential webDavCredential) throws UnauthorizedException, ServerNotWebdavCompatibleException {
        return new WebDavCloudProvider(webDavCredential);
    }

    @Override
    public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
        return supplyAsync(() -> webDavClient.itemMetadata(node));
    }

    @Override
    public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
        return supplyAsync(() -> webDavClient.list(folder));
    }

    @Override
    public CompletionStage<CloudItemList> listExhaustively(Path folder) {
        return supplyAsync(() -> webDavClient.listExhaustively(folder));
    }

    @Override
    public CompletionStage<InputStream> read(Path file, ProgressListener progressListener) {
        return supplyAsync(() -> webDavClient.read(file, progressListener));
    }

    @Override
    public CompletionStage<InputStream> read(Path file, long offset, long count, ProgressListener progressListener) {
        return supplyAsync(() -> webDavClient.read(file, offset, count, progressListener));
    }

    @Override
    public CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener) {
        return supplyAsync(() -> webDavClient.write(file, replace, data, progressListener));
    }

    @Override
    public CompletionStage<Path> createFolder(Path folder) {
        return supplyAsync(() -> webDavClient.createFolder(folder));
    }

    @Override
    public CompletionStage<Void> delete(Path node) {
        return CompletableFuture.runAsync(() -> webDavClient.delete(node));
    }

    @Override
    public CompletionStage<Path> move(Path source, Path target, boolean replace) {
        return supplyAsync(() -> webDavClient.move(source, target, replace));
    }

}
