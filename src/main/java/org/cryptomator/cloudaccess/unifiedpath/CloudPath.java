/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cloudaccess.unifiedpath;

import org.cryptomator.cloudaccess.api.CloudProvider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * TODO: maybe not "implement" the path interface, so that developers don't get any funny ideas like using this class as a return value...
 */
public class CloudPath implements Path {

	private static final String SEPARATOR = "/";
	private static final String CURRENT_DIR = ".";
	private static final String PARENT_DIR = "..";

	private final CloudProvider provider;
	private final List<String> elements;
	private final boolean absolute;

	CloudPath(CloudProvider provider, Path origin) {
		this.provider = provider;
		this.absolute = Objects.requireNonNull(origin.isAbsolute());
		this.elements = IntStream.range(0, origin.getNameCount()).mapToObj(i -> origin.getName(i).toString()).collect(Collectors.toList());
	}

	//TODO: what if elements has only one element, but it contains the whole path with seperators? ->
	CloudPath(CloudProvider provider, boolean absolute, String... elements) {
		this.provider = provider;
		this.absolute = absolute;
		this.elements = List.of(elements);
	}

	CloudPath(CloudProvider provider, boolean absolute, List<String> elements) {
		this.provider = provider;
		this.absolute = absolute;
		this.elements = List.copyOf(elements);
	}

	/**
	 * TODO: idea was to add the cloud provider as a parameter. But then it gets tricky if the path is already a CLoudPath!
	 * Maybe discard provider if it is a cloud path?
	 *
	 * @param provider Cloudprovider to which these paths belong.
	 * @param origin
	 * @return
	 */
	public static CloudPath of(CloudProvider provider, Path origin) {
		if (origin instanceof CloudPath) {
			var cloudP = (CloudPath) origin;
			if (cloudP.getProvider().equals(provider)) {
				return (CloudPath) origin;
			} else {
				return new CloudPath(provider, origin);
			}
		} else {
			return new CloudPath(provider, origin);
		}
	}

	private CloudProvider getProvider() {
		return provider;
	}

	static CloudPath cast(Path path) {
		if (path instanceof CloudPath) {
			return (CloudPath) path;
		} else {
			throw new ProviderMismatchException("Used a path from different provider: " + path);
		}
	}

	// visible for testing
	List<String> getElements() {
		return elements;
	}

	@Override
	public FileSystem getFileSystem() {
		return null;
	}

	@Override
	public boolean isAbsolute() {
		return absolute;
	}

	@Override
	public CloudPath getRoot() {
		//delegation to provider would be cool
		return absolute ? new CloudPath(provider, true, SEPARATOR) : null;
	}

	@Override
	public CloudPath getFileName() {
		int elementCount = getNameCount();
		if (elementCount == 0) {
			return null;
		} else {
			return getName(elementCount - 1);
		}
	}

	@Override
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

	@Override
	public int getNameCount() {
		return elements.size();
	}

	@Override
	public CloudPath getName(int index) {
		return subpath(index, index + 1);
	}

	@Override
	public CloudPath subpath(int beginIndex, int endIndex) {
		return new CloudPath(provider, false, elements.subList(beginIndex, endIndex));
	}

	@Override
	public boolean startsWith(Path path) {
		if (path instanceof CloudPath) {
			var other = (CloudPath) path;
			if (this.provider.equals(other.provider)) {
				boolean matchesAbsolute = this.isAbsolute() == other.isAbsolute();
				if (matchesAbsolute && other.elements.size() <= this.elements.size()) {
					return this.elements.subList(0, other.elements.size()).equals(other.elements);
				}
			}
		}
		return false;
	}

	@Override
	public boolean startsWith(String other) {
		return startsWith(new CloudPath(provider, other.startsWith(SEPARATOR), other));
	}

	@Override
	public boolean endsWith(Path path) {
		if (path instanceof CloudPath) {
			CloudPath other = (CloudPath) path;
			if (other.elements.size() <= this.elements.size()) {
				return this.elements.subList(this.elements.size() - other.elements.size(), this.elements.size()).equals(other.elements);
			}
		}
		return false;
	}

	@Override
	public boolean endsWith(String other) {
		return endsWith(new CloudPath(provider, other.startsWith(SEPARATOR), other));
	}

	@Override
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

	@Override
	public CloudPath resolve(Path path) {
		CloudPath other = cast(path); //TODO
		if (other.isAbsolute()) {
			return other;
		} else {
			List<String> joined = new ArrayList<>();
			joined.addAll(this.elements);
			joined.addAll(other.elements);
			return copyWithElements(joined);
		}
	}

	@Override
	public CloudPath resolve(String other) {
		return resolve(new CloudPath(provider, other.startsWith(SEPARATOR), other));
	}

	@Override
	public CloudPath resolveSibling(Path path) {
		CloudPath parent = getParent();
		CloudPath other = cast(path);
		if (parent == null || other.isAbsolute()) {
			return other;
		} else {
			return parent.resolve(other);
		}
	}

	@Override
	public CloudPath resolveSibling(String other) {
		return resolveSibling(new CloudPath(provider, other.startsWith(SEPARATOR), other));
	}

	@Override
	public CloudPath relativize(Path path) {
		CloudPath normalized = this.normalize();
		CloudPath other = cast(path).normalize();
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

	/**
	 * TODO: implement it
	 *
	 * @return
	 */
	@Override
	public URI toUri() {
		return null;
	}

	@Override
	public CloudPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		} else {
			return copyWithAbsolute(true);
		}
	}

	@Override
	public CloudPath toRealPath(LinkOption... options) throws IOException {
		return normalize().toAbsolutePath();
	}


	@Override
	public File toFile() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException("Method not implemented.");
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
		throw new UnsupportedOperationException("Method not implemented.");
	}

	@Override
	public Iterator<Path> iterator() {
		return new Iterator<Path>() {

			private int idx = 0;

			@Override
			public boolean hasNext() {
				return idx < getNameCount();
			}

			@Override
			public Path next() {
				return getName(idx++);
			}
		};
	}

	@Override
	public int compareTo(Path path) {
		CloudPath other = (CloudPath) path;
		if (this.isAbsolute() != other.isAbsolute()) {
			return this.isAbsolute() ? -1 : 1;
		}
		for (int i = 0; i < Math.min(this.getNameCount(), other.getNameCount()); i++) {
			int result = this.elements.get(i).compareTo(other.elements.get(i));
			if (result != 0) {
				return result;
			}
		}
		return this.getNameCount() - other.getNameCount();
	}

	@Override
	public int hashCode() {
		int hash = 0;
		hash = 31 * hash + provider.hashCode();
		hash = 31 * hash + elements.hashCode();
		hash = 31 * hash + (absolute ? 1 : 0);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CloudPath) {
			CloudPath other = (CloudPath) obj;
			return this.provider.equals(other.provider) //
					&& this.compareTo(other) == 0;
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
		return new CloudPath(provider, absolute, elements);
	}

	private CloudPath copyWithAbsolute(boolean absolute) {
		return new CloudPath(provider, absolute, elements);
	}

	private CloudPath copyWithElementsAndAbsolute(List<String> elements, boolean absolute) {
		return new CloudPath(provider, absolute, elements);
	}

}
