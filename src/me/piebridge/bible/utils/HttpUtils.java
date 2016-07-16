package me.piebridge.bible.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by thom on 16/7/15.
 */
public class HttpUtils {

    private static final int STATUS_301 = HttpURLConnection.HTTP_MOVED_PERM;
    private static final int STATUS_302 = HttpURLConnection.HTTP_MOVED_TEMP;
    private static final int STATUS_303 = HttpURLConnection.HTTP_SEE_OTHER;
    private static final int STATUS_304 = HttpURLConnection.HTTP_NOT_MODIFIED;
    private static final int STATUS_307 = 307;


    private HttpUtils() {

    }

    private static boolean isRedirected(int code) {
        return code == STATUS_301 || code == STATUS_302 || code == STATUS_303 || code == STATUS_307;
    }

    public static String retrieveContent(String url, StringBuilder eTag) throws IOException {
        return retrieveContent(url, eTag, 0x1);
    }

    private static String retrieveContent(String url, StringBuilder eTag, int count) throws IOException {
        if (count > 0x5) {
            return null;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            if (eTag.length() > 0) {
                connection.addRequestProperty("If-None-Match", eTag.toString());
            }
            int code = connection.getResponseCode();
            if (code == STATUS_304) {
                return null;
            } else if (isRedirected(code)) {
                String location = connection.getHeaderField("Location");
                LogUtils.d("redirect to " + location);
                return retrieveContent(location, eTag, count + 1);
            }
            eTag.setLength(0);
            eTag.append(connection.getHeaderField("ETag"));
            return FileUtils.readAsString(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

}
