package org.cryptomator.CloudAccess.Core;

public interface ProgressListener {

	ProgressListener NO_PROGRESS_AWARE = value -> {};

	void onProgress(int value);

}
