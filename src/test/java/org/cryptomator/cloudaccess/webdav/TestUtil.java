package org.cryptomator.cloudaccess.webdav;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;

public class TestUtil {

    public static Instant toInstant(String date) {
        try {
            return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(date).toInstant();
        } catch (ParseException e) {
            throw new IllegalStateException("Not valid date string provided", e);
        }
    }
}
