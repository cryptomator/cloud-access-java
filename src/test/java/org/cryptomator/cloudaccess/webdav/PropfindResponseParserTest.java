package org.cryptomator.cloudaccess.webdav;

import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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

	private static final CloudItemMetadata testFolder = new CloudItemMetadata("Gelöschte Dateien", Path.of("/Gelöschte Dateien"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
	private static final CloudItemMetadata testFile = new CloudItemMetadata(
			"0.txt",
			Path.of("/0.txt"),
			CloudItemType.FILE,
			Optional.of(TestUtil.toInstant("Thu, 18 May 2017 9:49:41 GMT")),
			Optional.of(54175L));

	private PropfindResponseParser propfindResponseParser;

	@BeforeEach
	public void setup() {
		propfindResponseParser = new PropfindResponseParser();
	}

	@Test
	public void testEmptyResponseLeadsToEmptyCloudNodeList() throws XmlPullParserException, IOException {
		final var propfindEntryList = propfindResponseParser.parse(load(RESPONSE_EMPTY_DIRECTORY));
		final var cloudNodeItemList = processDirList(propfindEntryList);

		Assert.assertEquals(Collections.EMPTY_LIST, cloudNodeItemList.getItems());
		Assert.assertEquals(Optional.empty(), cloudNodeItemList.getNextPageToken());
	}

	@Test
	public void testFolderWithoutServerPartInHrefResponseLeadsToFolderInCloudNodeListWithCompleteUrl() throws XmlPullParserException, IOException {
		final var propfindEntryList = propfindResponseParser.parse(load(RESPONSE_ONE_FILE_NO_SERVER));
		final var cloudNodeItemList = processDirList(propfindEntryList);

		final var resultFolder = new CloudItemMetadata(
				"DYNTZMMHWLW25RZHWYEDHLFWIUZZG2",
				Path.of("/User7de989b/asdasdasd/d/OC/DYNTZMMHWLW25RZHWYEDHLFWIUZZG2"),
				CloudItemType.FOLDER,
				Optional.empty(),
				Optional.empty());

		Assert.assertEquals(1, cloudNodeItemList.getItems().size());
		Assert.assertEquals(List.of(resultFolder), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileResponseLeadsToFileAndFoldersInCloudNodeList() throws XmlPullParserException, IOException {
		final var propfindEntryList = propfindResponseParser.parse(load(RESPONSE_ONE_FILE_AND_FOLDERS));
		final var cloudNodeItemList = processDirList(propfindEntryList);

		Assert.assertEquals(2, cloudNodeItemList.getItems().size());
		Assert.assertEquals(List.of(testFile, testFolder), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileWithMalFormattedDateResponseLeadsToFileAndFoldersInCloudNodeListWithoutDate() throws XmlPullParserException, IOException {
		final var propfindEntryList = propfindResponseParser.parse(load(RESPONSE_MAL_FORMATTED_DATE));
		final var cloudNodeItemList = processDirList(propfindEntryList);

		Assert.assertEquals(2, cloudNodeItemList.getItems().size());
		Assert.assertEquals(List.of(new CloudItemMetadata("0.txt", Path.of("/0.txt"), CloudItemType.FILE, Optional.empty(), Optional.of(54175L)),
						new CloudItemMetadata("Gelöschte Dateien", Path.of("/Gelöschte Dateien"), CloudItemType.FOLDER, Optional.empty(), Optional.empty())),
				cloudNodeItemList.getItems());
	}

	@Test
	public void testFileMultiStatusLeadsToFolderInCloudNodeList() throws XmlPullParserException, IOException {
		final var propfindEntryList = propfindResponseParser.parse(load(RESPONSE_ONE_FILE_MULTI_STATUS));
		final var cloudNodeItemList = processDirList(propfindEntryList);

		Assert.assertEquals(1, cloudNodeItemList.getItems().size());
		Assert.assertEquals(List.of(testFolder), cloudNodeItemList.getItems());
	}

	@Test
	public void testFileNoPathResponseLeadsToFileAndFoldersInCloudNodeListWithoutDate() throws XmlPullParserException, IOException {
		final var propfindEntryList = propfindResponseParser.parse(load(RESPONSE_MAL_FORMATTED_NO_PATH));
		final var cloudNodeItemList = processDirList(propfindEntryList);

		Assert.assertEquals(0, cloudNodeItemList.getItems().size());
		Assert.assertEquals(Collections.EMPTY_LIST, cloudNodeItemList.getItems());
	}

	private CloudItemList processDirList(final List<PropfindEntryData> entryData) {
		var result = new CloudItemList(new ArrayList<>());

		if(entryData.isEmpty()) {
			return result;
		}

		entryData.sort(ASCENDING_BY_DEPTH);
		// after sorting the first entry is the parent
		// because it's depth is 1 smaller than the depth
		// ot the other entries, thus we skip the first entry
		for (PropfindEntryData childEntry : entryData.subList(1, entryData.size())) {
			result = result.add(List.of(childEntry.toCloudItem()));
		}
		return result;
	}

	@Test
	public void testMallFormattedResponseLeadsToXmlPullParserException() {
		Assertions.assertThrows(XmlPullParserException.class, () -> propfindResponseParser.parse(load(RESPONSE_MAL_FORMATTED_XMLPULLPARSER_EXCEPTION)));
	}

	private InputStream load(String resourceName) {
		return getClass().getResourceAsStream("/propfind-test-requests/" + resourceName + ".xml");
	}

	private final Comparator<PropfindEntryData> ASCENDING_BY_DEPTH = Comparator.comparingInt(PropfindEntryData::getDepth);
}
