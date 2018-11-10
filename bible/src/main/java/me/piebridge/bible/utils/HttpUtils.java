package me.piebridge.bible.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    private static final String HTTPS = String.valueOf(new char[] {
            'h', 't', 't', 'p', 's'
    });

    private static final String HOST = String.valueOf(new char[] {
            'j', 'i', 'a', 'n', 'y', 'v', '.', 'c', 'o', 'm'
    });

    private HttpUtils() {

    }

    private static boolean isRedirected(int code) {
        return code == STATUS_301 || code == STATUS_302 || code == STATUS_303 || code == STATUS_307;
    }

    private static boolean isLetsEncrypt(URL url) {
        return HTTPS.equals(url.getProtocol()) && url.getHost().endsWith(HOST);
    }

    public static String retrieveContent(String spec, Map<String, String> headers) throws IOException {
        return retrieveContent(spec, headers, false);
    }

    public static String retrieveContent(String spec, Map<String, String> headers, boolean head) throws IOException {
        SSLSocketFactory factory = null;
        URL url = new URL(spec);
        if (isLetsEncrypt(url)) {
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, Sha1TrustManager.LETS_ENCRYPT, null);
                factory = context.getSocketFactory();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Can't getSocketFactory", e);
            }
        }
        return retrieveContent(url, factory, headers, head, 1);
    }

    static String retrieveContent(URL url, SSLSocketFactory factory, Map<String, String> headers, boolean head, int count)
            throws IOException {
        if (count > MAX_REDIRECT) {
            return null;
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (isLetsEncrypt(url)) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
        }
        connection.setInstanceFollowRedirects(false);
        try {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (head) {
                connection.setRequestMethod("HEAD");
            }
            connection.addRequestProperty("Accept-Encoding", "gzip");
            int code = connection.getResponseCode();
            LogUtils.d("url: " + url + ", code: " + code);
            if (code == STATUS_304) {
                return null;
            } else if (isRedirected(code) && !head) {
                String location = connection.getHeaderField("Location");
                LogUtils.d("redirect to " + location);
                return retrieveContent(new URL(location), factory, headers, false, count + 1);
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

    private static class Sha1TrustManager implements X509TrustManager {

        private static final byte[] FINGERPRINT_LETS_ENCRYPT = new byte[] {
                -26, -93, -76, 91, 6, 45, 80, -101, 51, -126,
                40, 45, 25, 110, -2, -105, -43, -107, 108, -53
        };

        static final TrustManager[] LETS_ENCRYPT = new TrustManager[] {
                new Sha1TrustManager(FINGERPRINT_LETS_ENCRYPT)
        };

        private static final int LENGTH = 20;

        private static final char[] HEX = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };

        private final byte[] fingerprint;

        private Sha1TrustManager(byte[] fingerprint) {
            this.fingerprint = fingerprint;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw new CertificateException("Client certificates not supported");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            outer:
            for (X509Certificate certificate : chain) {
                byte[] bytes = sha1(certificate.getEncoded());
                if (bytes != null && bytes.length == LENGTH) {
                    for (int i = 0; i < LENGTH; ++i) {
                        if (bytes[i] != fingerprint[i]) {
                            continue outer;
                        }
                    }
                    return;
                }
            }
            throw new CertificateException("Server certificates not trusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private static byte[] sha1(byte[] bytes) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(bytes);
                return md.digest();
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        }

        private static String hex(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(HEX[(b >> 4) & 0xf]);
                sb.append(HEX[b & 0xf]);
            }
            return sb.toString();
        }

    }

}
