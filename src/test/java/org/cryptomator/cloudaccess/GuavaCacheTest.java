package org.cryptomator.cloudaccess;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

@Disabled
public class GuavaCacheTest {

	/*
	 * evict /clap_backup/d/SG/DH74LJJBSFMBQCOLHGIJ65WDZQGMDJ/s3f2Tk6zhsSZO7oAzI6zIN81.c9r
	 * eviction failed for /clap_backup/d/SG/DH74LJJBSFMBQCOLHGIJ65WDZQGMDJ/s3f2Tk6zhsSZO7oAzI6zIN81.c9r
	 * itemMetadata, amout of entires in cache: 1
	 * /clap_backup/d/SG/DH74LJJBSFMBQCOLHGIJ65WDZQGMDJ/s3f2Tk6zhsSZO7oAzI6zIN81.c9r
	 */

	private final CloudPath dir1 = CloudPath.of("/clap_backup/d/KG/6TFDGKXGZEGWRZOGTDFDF4YEGAZO6Q/dir1");
	private final CloudPath dir2 = CloudPath.of("/clap_backup/d/KG/6TFDGKXGZEGWRZOGTDFDF4YEGAZO6Q/dir2");
	private final CloudPath dir3 = CloudPath.of("/clap_backup/d/KG/6TFDGKXGZEGWRZOGTDFDF4YEGAZO6Q/dir3");
	private Cache<CloudPath, CompletionStage<String>> cachedItemMetadataRequests;

	@BeforeEach
	public void setup() {
		cachedItemMetadataRequests = CacheBuilder.newBuilder().build();
	}

	@Test
	@DisplayName("foo")
	public void foo() throws ExecutionException {
		/*
		 -->
		 * put dir2
		 * get called
		 * evict dir2
		 * put dir1
		 * put dir3
		 * evict dir1
		 * evict dir3
		 * get called
		 * eviction failed dir1
		 * get called
		 * put dir2
		 * evict dir2
		 * put dir1
		 * evict dir1
		 * get called
		 * put dir3
		 * evict dir3
		 * get called
		 * put dir1
		 * evict dir1
		 * get called
		 * put dir2
		 * evict dir2
		 * eviction failed dir1
		 * eviction failed dir2
		 * put dir3
		 * get called
		 * evict dir3
		 * put dir2
		 */

		while (true) {
			System.out.println("get called");

			cachedItemMetadataRequests.get(dir1, () -> CompletableFuture.supplyAsync(() -> {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("put dir1");
				return "Foo";
			}).whenComplete((metadata, throwable) -> {
				System.out.println("evict dir1");
				cachedItemMetadataRequests.invalidate(dir1);
				if(cachedItemMetadataRequests.getIfPresent(dir1) != null) {
					System.out.println("eviction failed dir1");
				}
			}));
			cachedItemMetadataRequests.get(dir2, () -> CompletableFuture.supplyAsync(() -> {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("put dir2");
				return "Foo";
			}).whenComplete((metadata, throwable) -> {
				System.out.println("evict dir2");
				cachedItemMetadataRequests.invalidate(dir2);
				if(cachedItemMetadataRequests.getIfPresent(dir2) != null) {
					System.out.println("eviction failed dir2");
				}
			}));
			cachedItemMetadataRequests.get(dir3, () -> CompletableFuture.supplyAsync(() -> {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("put dir3");
				return "Foo";
			}).whenComplete((metadata, throwable) -> {
				System.out.println("evict dir3");
				cachedItemMetadataRequests.invalidate(dir3);
				if(cachedItemMetadataRequests.getIfPresent(dir3) != null) {
					System.out.println("eviction failed dir3");
				}
			}));

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Test
	@DisplayName("bar")
	public void bar() {
		AsyncCache<CloudPath, String> cache = Caffeine.newBuilder().buildAsync();

		while (true) {
			System.out.println("get called");

			cache.get(dir1, k -> {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("put dir1");
				return "Foo";
			}).whenComplete((metadata, throwable) -> {
				System.out.println("evict dir1");
				cache.synchronous().invalidate(dir1);
				if(cache.synchronous().getIfPresent(dir1) != null) { // without synchronous() it doesn't work here too
					System.out.println("eviction failed dir1");
				}
			});

			cache.get(dir2, k -> {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("put dir2");
				return "Foo";
			}).whenComplete((metadata, throwable) -> {
				System.out.println("evict dir2");
				cache.synchronous().invalidate(dir2);
				if(cache.synchronous().getIfPresent(dir2) != null) {
					System.out.println("eviction failed dir2");
				}
			});

			cache.get(dir3, k -> {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("put dir3");
				return "Foo";
			}).whenComplete((metadata, throwable) -> {
				System.out.println("evict dir3");
				cache.synchronous().invalidate(dir3);
				if(cache.synchronous().getIfPresent(dir3) != null) {
					System.out.println("eviction failed dir3");
				}
			});

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}