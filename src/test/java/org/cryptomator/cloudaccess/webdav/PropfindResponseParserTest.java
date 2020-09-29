package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudPath;
import org.cryptomator.cloudaccess.api.exceptions.QuotaNotAvailableException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PropfindResponseParserTest {

	private static final String RESPONSE_EMPTY_DIRECTORY = "empty-directory";
	private static final String RESPONSE_ONE_FILE_NO_SERVER = "directory-one-file-no-server";
	private static final String RESPONSE_ONE_FILE_AND_FOLDERS = "directory-and-file";
	private static final String RESPONSE_MAL_FORMATTED_XMLPULLPARSER_EXCEPTION = "malformatted-response-xmlpullparser";
	private static final String RESPONSE_MAL_FORMATTED_DATE = "malformatted-date-response";
	private static final String RESPONSE_MAL_FORMATTED_NO_PATH = "directory-and-file-no-path";
	private static final String RESPONSE_ONE_FILE_MULTI_STATUS = "file-multi-status";
	private static final String RESPONSE_QUOTA = "quota";
	private static final String RESPONSE_QUOTA_NEGATIVE_AVAILABLE = "quota-negative-available";

	private static final CloudItemMetadata testFolder = new CloudItemMetadata("Gelöschte Dateien", CloudPath.of("/Gelöschte Dateien"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
	private static final CloudItemMetadata testFile = new CloudItemMetadata("0.txt", CloudPath.of("/0.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 18 May 2017 9:49:41 GMT")), Optional.of(54175L));
	private final Comparator<PropfindEntryItemData> ASCENDING_BY_DEPTH = Comparator.comparingLong(PropfindEntryItemData::getDepth);
	private PropfindResponseParser propfindResponseParser;

	@BeforeEach
	public void setup() {
		propfindResponseParser = new PropfindResponseParser();
	}

	@Test
	public void testEmptyResponseLeadsToEmptyCloudNodeList() throws SAXException, IOException {
		final var propfindEntryList = propfindResponseParser.parseItemData(load(RESPONSE_EMPTY_DIRECTORY));
		final var cloudNodeItemList = processDirList(propfindEntryList, CloudPath.of("/"));

		Assertions.assertEquals(Collections.EMPTY_LIST, cloudNodeItemList.getItems());
		Assertions.assertEquals(Optional.empty(), cloudNodeItemList.getNextPageToken());
	}

	@Test
	public void testFolderWithoutServerPartInHrefResponseLeadsToFolderInCloudNodeListWithCompleteUrl() throws SAXException, IOException {
		final var propfindEntryList = propfindResponseParser.parseItemData(load(RESPONSE_ONE_FILE_NO_SERVER));
		final var cloudNodeItemList = processDirList(propfindEntryList, CloudPath.of("/User7de989b/asdasdasd/d/OC/"));

		final var resultFolder = new CloudItemMetadata("DYNTZMMHWLW25RZHWYEDHLFWIUZZG2", CloudPath.of("/User7de989b/asdasdasd/d/OC/DYNTZMMHWLW25RZHWYEDHLFWIUZZG2"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());

		Assertions.assertEquals(1, cloudNodeItemList.getItems().size());
		Assertions.assertEquals(List.of(resultFolder), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileResponseLeadsToFileAndFoldersInCloudNodeList() throws SAXException, IOException {
		final var propfindEntryList = propfindResponseParser.parseItemData(load(RESPONSE_ONE_FILE_AND_FOLDERS));
		final var cloudNodeItemList = processDirList(propfindEntryList, CloudPath.of("/"));

		Assertions.assertEquals(2, cloudNodeItemList.getItems().size());
		Assertions.assertEquals(List.of(testFile, testFolder), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileWithMalFormattedDateResponseLeadsToFileAndFoldersInCloudNodeListWithoutDate() throws SAXException, IOException {
		final var propfindEntryList = propfindResponseParser.parseItemData(load(RESPONSE_MAL_FORMATTED_DATE));
		final var cloudNodeItemList = processDirList(propfindEntryList, CloudPath.of("/"));

		Assertions.assertEquals(2, cloudNodeItemList.getItems().size());
		Assertions.assertEquals(List.of(new CloudItemMetadata("0.txt", CloudPath.of("/0.txt"), CloudItemType.FILE, Optional.empty(), Optional.of(54175L)), new CloudItemMetadata("Gelöschte Dateien", CloudPath.of("/Gelöschte Dateien"), CloudItemType.FOLDER, Optional.empty(), Optional.empty())), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileMultiStatusLeadsToFolderInCloudNodeList() throws SAXException, IOException {
		final var propfindEntryList = propfindResponseParser.parseItemData(load(RESPONSE_ONE_FILE_MULTI_STATUS));
		final var cloudNodeItemList = processDirList(propfindEntryList, CloudPath.of("/"));

		Assertions.assertEquals(1, cloudNodeItemList.getItems().size());
		Assertions.assertEquals(List.of(testFolder), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileNoPathResponseLeadsToFileAndFoldersInCloudNodeListWithoutDate() throws SAXException, IOException {
		final var propfindEntryList = propfindResponseParser.parseItemData(load(RESPONSE_MAL_FORMATTED_NO_PATH));
		final var cloudNodeItemList = processDirList(propfindEntryList, CloudPath.of("/"));

		Assertions.assertEquals(0, cloudNodeItemList.getItems().size());
		Assertions.assertEquals(Collections.EMPTY_LIST, cloudNodeItemList.getItems());
	}

	@Test
	public void testQuota() throws SAXException, IOException {
		final var quota = propfindResponseParser.parseQuta(load(RESPONSE_QUOTA));

		Assertions.assertEquals(10699503366L, quota.getAvailableBytes());
		Assertions.assertEquals(37914874L, quota.getUsedBytes().get());
		Assertions.assertEquals(Optional.empty(), quota.getTotalBytes());
	}

	@Test
	public void testQuotaWithNegativeAvailable() {
		Assertions.assertThrows(QuotaNotAvailableException.class, () -> propfindResponseParser.parseQuta(load(RESPONSE_QUOTA_NEGATIVE_AVAILABLE)));
	}

	private CloudItemList processDirList(final List<PropfindEntryItemData> entryData, final CloudPath folder) {
		var result = new CloudItemList(new ArrayList<>());

		if (entryData.isEmpty()) {
			return result;
		}

		entryData.sort(ASCENDING_BY_DEPTH);
		// after sorting the first entry is the parent
		// because it's depth is 1 smaller than the depth
		// ot the other entries, thus we skip the first entry
		for (PropfindEntryItemData childEntry : entryData.subList(1, entryData.size())) {
			result = result.add(List.of(toCloudItem(childEntry, folder.resolve(childEntry.getName()))));
		}
		return result;
	}

	private CloudItemMetadata toCloudItem(final PropfindEntryItemData data, final CloudPath path) {
		if (data.isCollection()) {
			return new CloudItemMetadata(data.getName(), path, CloudItemType.FOLDER);
		} else {
			return new CloudItemMetadata(data.getName(), path, CloudItemType.FILE, data.getLastModified(), data.getSize());
		}
	}

	@Test
	public void testMallFormattedResponseLeadsToSAXException() {
		Assertions.assertThrows(SAXException.class, () -> propfindResponseParser.parseItemData(load(RESPONSE_MAL_FORMATTED_XMLPULLPARSER_EXCEPTION)));
	}

	private InputStream load(String resourceName) {
		return getClass().getResourceAsStream("/propfind-test-requests/" + resourceName + ".xml");
	}
}
