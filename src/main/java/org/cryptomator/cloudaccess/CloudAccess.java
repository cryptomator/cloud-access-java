package org.cryptomator.cloudaccess;

import java.net.URL;
import java.nio.file.Path;

import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.localfs.LocalFsCloudProvider;
import org.cryptomator.cloudaccess.webdav.WebDavCloudProvider;
import org.cryptomator.cloudaccess.webdav.WebDavCredential;

public class CloudAccess {

	private CloudAccess() {
	}

	/**
	 * Creates a new CloudProvider which provides access to the given URL via WebDAV.
	 *
	 * @param url Base URL leading to the root resource
	 * @param username Username used during basic or digest auth challenges
	 * @param password Password used during basic or digest auth challenges
	 * @return A cloud access provider that provides access to the given WebDAV URL.
	 */
	public static CloudProvider toWebDAV(URL url, String username, CharSequence password) {
		// TODO adapt types
		return WebDavCloudProvider.from(WebDavCredential.from(null, username, null));
	}

	/**
	 * Creates a new CloudProvider which provides access to the given <code>folder</code>. Mainly for test purposes.
	 *
	 * @param folder An existing folder on the (local) default file system.
	 * @return A cloud access provider that provides access to the given local directory.
	 */
	public static CloudProvider toLocalFileSystem(Path folder) {
		return new LocalFsCloudProvider(folder);
	}

}
