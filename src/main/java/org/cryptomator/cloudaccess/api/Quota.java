package org.cryptomator.cloudaccess.api;

import java.util.Optional;

public class Quota {

	private final long availableBytes;
	private final Optional<Long> totalBytes;
	private final Optional<Long> usedBytes;

	public Quota(long availableBytes, Optional<Long> totalBytes, Optional<Long> usedBytes) {
		this.availableBytes = availableBytes;
		this.totalBytes = totalBytes;
		this.usedBytes = usedBytes;
	}

	public long getAvailableBytes() {
		return availableBytes;
	}

	public Optional<Long> getTotalBytes() {
		return totalBytes;
	}

	public Optional<Long> getUsedBytes() {
		return usedBytes;
	}
}
