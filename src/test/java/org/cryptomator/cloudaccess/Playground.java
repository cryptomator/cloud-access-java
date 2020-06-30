package org.cryptomator.cloudaccess;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
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
        final var cloudItemMetadata = new CloudItemMetadata("name", Path.of("/path"), FILE, Optional.empty(), Optional.empty());

        when(cloudProvider.createFolder(any())).thenAnswer(folder -> CompletableFuture.supplyAsync(() -> folder));

        final var result = cloudProvider.createFolder(Path.of("/foo/bar"))
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
        public CompletionStage<CloudItemMetadata> itemMetadata(Path node) {
            return null;
        }

        @Override
        public CompletionStage<CloudItemList> list(Path folder, Optional<String> pageToken) {
            return null;
        }

        @Override
        public CompletionStage<OutputStream> read(Path file, ProgressListener progressListener) {
            progressListener.onProgress(12);
            progressListener.onProgress(52);
            return null;
        }

        @Override
        public CompletionStage<CloudItemMetadata> write(Path file, boolean replace, InputStream data, ProgressListener progressListener) {
            return null;
        }

        @Override
        public CompletionStage<Path> createFolder(Path folder) {
            return null;
        }

        @Override
        public CompletionStage<Void> delete(Path node) {
            return null;
        }

        @Override
        public CompletionStage<Path> move(Path source, Path target, boolean replace) {
            return null;
        }
    }
}