package me.piebridge.bible.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Created by thom on 16/7/15.
 */
public class HttpUtils {

    public static final String ETAG = "ETag";

    private static final int STATUS_301 = HttpURLConnection.HTTP_MOVED_PERM;
    private static final int STATUS_302 = HttpURLConnection.HTTP_MOVED_TEMP;
    private static final int STATUS_303 = HttpURLConnection.HTTP_SEE_OTHER;
    private static final int STATUS_304 = HttpURLConnection.HTTP_NOT_MODIFIED;
    private static final int STATUS_307 = 307;

    private static final int MAX_REDIRECT = 5;

    private HttpUtils() {

    }

    private static boolean isRedirected(int code) {
        return code == STATUS_301 || code == STATUS_302 || code == STATUS_303 || code == STATUS_307;
    }

    public static String retrieveContent(String url, Map<String, String> headers) throws IOException {
        return retrieveContent(url, headers, 1);
    }

    private static String retrieveContent(String url, Map<String, String> headers, int count) throws IOException {
        if (count > MAX_REDIRECT) {
            return null;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        try {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            connection.addRequestProperty("Accept-Encoding", "gzip");
            int code = connection.getResponseCode();
            LogUtils.d("url: " + url + ", code: " + code);
            if (code == STATUS_304) {
                return null;
            } else if (isRedirected(code)) {
                String location = connection.getHeaderField("Location");
                LogUtils.d("redirect to " + location);
                return retrieveContent(location, headers, count + 1);
            }
            String etag = connection.getHeaderField(ETAG);
            String encoding = connection.getContentEncoding();
            LogUtils.d("content-encoding: " + encoding + ", etag: " + etag);
            if (headers != null) {
                headers.clear();
                if (etag != null) {
                    headers.put(ETAG, etag);
                }
            }
            if ("gzip".equals(encoding)) {
                return FileUtils.readAsString(new GZIPInputStream(connection.getInputStream()));
            } else {
                return FileUtils.readAsString(connection.getInputStream());
            }
        } finally {
            connection.disconnect();
        }
    }

}
