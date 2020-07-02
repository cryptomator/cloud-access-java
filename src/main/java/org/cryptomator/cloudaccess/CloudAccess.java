package org.cryptomator.cloudaccess;

import java.nio.file.Path;

import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.localfs.LocalFsCloudProvider;

public class CloudAccess {

	private CloudAccess() {
	}

	/**
	 * Creates a new CloudProvider which provides access to the given <code>folder</code>. Mainly for test purposes.
	 *
	 * @param folder An existing folder on the (local) default file system.
	 * @return A cloud access provider that provides access to the given local directory.
	 */
	static CloudProvider toLocalFileSystem(Path folder) {
		return new LocalFsCloudProvider(folder);
	}

}
