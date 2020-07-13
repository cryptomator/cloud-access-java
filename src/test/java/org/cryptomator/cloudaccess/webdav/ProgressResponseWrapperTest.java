package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ProgressResponseWrapperTest {

    @Test
    public void updateProgressWhenReadFromProgressResponseWrapper() {
        final var thisContent = new BufferedReader(new InputStreamReader(load(), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        final var responseBody = ResponseBody.create(thisContent, MediaType.parse("application/octet-stream; charset=utf-8"));

        final var progressListener = Mockito.mock(ProgressListener.class);
        final var spyProgressListener = Mockito.spy(progressListener);

        final var progressResponseWrapper = new ProgressResponseWrapper(responseBody, spyProgressListener);

        Assert.assertEquals(8193, progressResponseWrapper.contentLength());
        Assert.assertEquals(MediaType.parse("application/octet-stream; charset=utf-8"), progressResponseWrapper.contentType());

        final var thatContent = new BufferedReader(new InputStreamReader(progressResponseWrapper.byteStream(), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        Assert.assertEquals(thatContent, thisContent);

        Mockito.verify(spyProgressListener).onProgress(8192);
        Mockito.verify(spyProgressListener).onProgress(8193);
    }

    private InputStream load() {
        return getClass().getResourceAsStream("/progress-request-text.txt");
    }


}