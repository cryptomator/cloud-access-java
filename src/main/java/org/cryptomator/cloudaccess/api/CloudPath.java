/*******************************************************************************
 * Copyright (c) 2020 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - orignal API and implementation
 *     Armin Schrenk - Refactoring and adjusting to this project
 *******************************************************************************/
package org.cryptomator.cloudaccess.api;

import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * An objects that may be used to locate resource in a remote file system independet from the actual filesystem/ cloud provider.
 * <p>
 * This class mimicks the behaviour of the Path interface from the JDK, but does not implement it and has a reduced set of methods. For example, a CloudPath is not bound to a certain cloud provider or filesystem and the same path can be used for different providers.
 * <p>
 * A CloudPath is created via the static factory method {@link CloudPath#of(String, String...)}, and can afterwards used in the most cases like a path object. For the documentation of the single instance methods, please consider the Path interface of the JDK for the time being.
 */
public class CloudPath implements Comparable<CloudPath>, Iterable<CloudPath> {

	private static final String SEPARATOR = "/";
	private static final String CURRENT_DIR = ".";
	private static final String PARENT_DIR = "..";

	private final List<String> elements;
	private final boolean absolute;

	private CloudPath(boolean absolute, List<String> elements) {
		this.absolute = absolute;
		this.elements = List.copyOf(elements);
	}

	public static CloudPath of(String first, String... more) {
		if (more == null) {
			return new CloudPath(first.startsWith(SEPARATOR), splitStreamAndCollect(first, new String[] {}));
		} else {
			return new CloudPath(first.startsWith(SEPARATOR), splitStreamAndCollect(first, more));
		}
	}

	// visible for testing
	List<String> getElements() {
		return elements;
	}

	public boolean isAbsolute() {
		return absolute;
	}

	public CloudPath getRoot() {
		return absolute ? CloudPath.of(SEPARATOR) : null;
	}

	public CloudPath getFileName() {
		int elementCount = getNameCount();
		if (elementCount == 0) {
			return null;
		} else {
			return getName(elementCount - 1);
		}
	}

	public CloudPath getParent() {
		int elementCount = getNameCount();
		if (elementCount > 1) {
			List<String> elems = elements.subList(0, elementCount - 1);
			return copyWithElements(elems);
		} else if (elementCount == 1) {
			return getRoot();
		} else {
			return null; // only root and the "empty" path don't have a parent
		}
	}

	public int getNameCount() {
		return elements.size();
	}

	public CloudPath getName(int index) {
		return subpath(index, index + 1);
	}

	public CloudPath subpath(int beginIndex, int endIndex) {
		return new CloudPath(false, elements.subList(beginIndex, endIndex));
	}

	public boolean startsWith(CloudPath path) {
		boolean matchesAbsolute = this.isAbsolute() == path.isAbsolute();
		if (matchesAbsolute && path.elements.size() <= this.elements.size()) {
			return this.elements.subList(0, path.elements.size()).equals(path.elements);
		} else {
			return false;
		}
	}

	public boolean startsWith(String other) {
		return startsWith(CloudPath.of(other));
	}

	public boolean endsWith(CloudPath path) {
		if (path.elements.size() <= this.elements.size()) {
			return this.elements.subList(this.elements.size() - path.elements.size(), this.elements.size()).equals(path.elements);
		}
		return false;
	}

	public boolean endsWith(String other) {
		return endsWith(CloudPath.of(other));
	}

	public CloudPath normalize() {
		LinkedList<String> normalized = new LinkedList<>();
		for (String elem : elements) {
			String lastElem = normalized.peekLast();
			if (elem.isEmpty() || CURRENT_DIR.equals(elem)) {
				continue;
			} else if (PARENT_DIR.equals(elem) && lastElem != null && !PARENT_DIR.equals(lastElem)) {
				normalized.removeLast();
			} else {
				normalized.add(elem);
			}
		}
		return copyWithElements(normalized);
	}

	public CloudPath resolve(CloudPath path) {
		if (path.isAbsolute()) {
			return path;
		} else {
			List<String> joined = new ArrayList<>();
			joined.addAll(this.elements);
			joined.addAll(path.elements);
			return copyWithElements(joined);
		}
	}

	public CloudPath resolve(String other) {
		return resolve(CloudPath.of(other));
	}

	public CloudPath resolveSibling(CloudPath path) {
		CloudPath parent = getParent();
		if (parent == null || path.isAbsolute()) {
			return path;
		} else {
			return parent.resolve(path);
		}
	}

	public CloudPath resolveSibling(String other) {
		return resolveSibling(CloudPath.of(other));
	}

	public CloudPath relativize(CloudPath path) {
		CloudPath normalized = this.normalize();
		CloudPath other = path.normalize();
		if (normalized.isAbsolute() == other.isAbsolute()) {
			int commonPrefix = countCommonPrefixElements(normalized, other);
			int stepsUp = this.getNameCount() - commonPrefix;
			List<String> elems = new ArrayList<>();
			elems.addAll(Collections.nCopies(stepsUp, PARENT_DIR));
			elems.addAll(other.elements.subList(commonPrefix, other.getNameCount()));
			return copyWithElementsAndAbsolute(elems, false);
		} else {
			throw new IllegalArgumentException("Can't relativize an absolute path relative to a relative path.");
		}
	}

	private int countCommonPrefixElements(CloudPath p1, CloudPath p2) {
		int n = Math.min(p1.getNameCount(), p2.getNameCount());
		for (int i = 0; i < n; i++) {
			if (!p1.elements.get(i).equals(p2.elements.get(i))) {
				return i;
			}
		}
		return n;
	}

	public CloudPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		} else {
			return copyWithAbsolute(true);
		}
	}

	public Iterator<CloudPath> iterator() {
		return new Iterator<CloudPath>() {

			private int idx = 0;

			@Override
			public boolean hasNext() {
				return idx < getNameCount();
			}

			@Override
			public CloudPath next() {
				return getName(idx++);
			}
		};
	}

	public int compareTo(CloudPath path) {
		if (this.isAbsolute() != path.isAbsolute()) {
			return this.isAbsolute() ? -1 : 1;
		}
		for (int i = 0; i < Math.min(this.getNameCount(), path.getNameCount()); i++) {
			int result = this.elements.get(i).compareTo(path.elements.get(i));
			if (result != 0) {
				return result;
			}
		}
		return this.getNameCount() - path.getNameCount();
	}

	@Override
	public int hashCode() {
		int hash = 0;
		hash = 31 * hash + elements.hashCode();
		hash = 31 * hash + (absolute ? 1 : 0);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CloudPath) {
			CloudPath other = (CloudPath) obj;
			return this.compareTo(other) == 0;
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		String prefix = absolute ? SEPARATOR : "";
		return prefix + String.join(SEPARATOR, elements);
	}

	private CloudPath copyWithElements(List<String> elements) {
		return new CloudPath(absolute, elements);
	}

	private CloudPath copyWithAbsolute(boolean absolute) {
		return new CloudPath(absolute, elements);
	}

	private CloudPath copyWithElementsAndAbsolute(List<String> elements, boolean absolute) {
		return new CloudPath(absolute, elements);
	}

	private static List<String> splitStreamAndCollect(String first, String... more) {
		return Stream.concat(
				Splitter.on(SEPARATOR).splitToStream(first),
				Arrays.stream(more).flatMap(Splitter.on(SEPARATOR)::splitToStream))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toUnmodifiableList());
	}

}
