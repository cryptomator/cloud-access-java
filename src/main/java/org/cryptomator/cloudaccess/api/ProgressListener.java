package org.cryptomator.cloudaccess.api;

public interface ProgressListener {

	ProgressListener NO_PROGRESS_AWARE = value -> {};

	void onProgress(long value);

}
