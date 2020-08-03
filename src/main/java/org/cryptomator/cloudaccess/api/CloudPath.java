/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cloudaccess.api;

import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * TODO: Add method to convert to a local filesystem path
 */
public class CloudPath {

	private static final String SEPARATOR = "/";
	private static final String CURRENT_DIR = ".";
	private static final String PARENT_DIR = "..";

	private final List<String> elements;
	private final boolean absolute;

	CloudPath(Path origin) {
		this.absolute = Objects.requireNonNull(origin.isAbsolute());
		this.elements = IntStream.range(0, origin.getNameCount()).mapToObj(i -> origin.getName(i).toString()).collect(Collectors.toList());
	}

	CloudPath(boolean absolute, String... elements) {
		this.absolute = absolute;
		if (elements == null || elements.length == 0 || (elements.length == 1 && elements[0].equals(SEPARATOR))) {
			this.elements = List.of();
		} else {
			this.elements = this.splitStreamAndCollect("", elements);
		}
	}

	CloudPath(boolean absolute, List<String> elements) {
		this.absolute = absolute;
		this.elements = List.copyOf(elements);
	}

	public static CloudPath of(Path origin) {
		if (origin instanceof CloudPath) {
			return (CloudPath) origin;
		} else {
			return new CloudPath(origin);
		}
	}

	public static CloudPath of(String first, String... more) {
		if (more == null || more.length == 0) {
			return new CloudPath(first.startsWith(SEPARATOR), first);
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
		return absolute ? new CloudPath(true) : null;
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
		return startsWith(new CloudPath(other.startsWith(SEPARATOR), other));
	}

	public boolean endsWith(CloudPath path) {
		if (path.elements.size() <= this.elements.size()) {
			return this.elements.subList(this.elements.size() - path.elements.size(), this.elements.size()).equals(path.elements);
		}
		return false;
	}

	public boolean endsWith(String other) {
		return endsWith(new CloudPath(other.startsWith(SEPARATOR), other));
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
		return resolve(new CloudPath(other.startsWith(SEPARATOR), other));
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
		return resolveSibling(new CloudPath(other.startsWith(SEPARATOR), other));
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

	public Path toLocalPath(FileSystem fs, String root, LinkOption... options) {
		return fs.getPath(root, this.normalize().toAbsolutePath().toString().substring(1));
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

	//TODO: still correct?
	@Override
	public int hashCode() {
		int hash = 0;
		//hash = 31 * hash + provider.hashCode();
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

	private static List<String> splitStreamAndCollect(String first, String... ss) {
		return Stream.concat(
					Arrays.stream(first.split(SEPARATOR)),
					Arrays.stream(ss).flatMap(s -> Arrays.stream(s.split(SEPARATOR)))
				)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toUnmodifiableList());
	}

}
