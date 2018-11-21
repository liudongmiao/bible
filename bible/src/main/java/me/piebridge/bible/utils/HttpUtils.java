package me.piebridge.bible.utils;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (retrieveContent(spec, headers, baos)) {
            return baos.toString(FileUtils.UTF_8);
        } else {
            return null;
        }
    }

    public static boolean retrieveContent(String spec, Map<String, String> headers, OutputStream os)
            throws IOException {
        return retrieveContent(new URL(spec), headers, os, 1);
    }

    static boolean retrieveContent(URL url, Map<String, String> headers, OutputStream os, int count)
            throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            LogUtils.d("cancel " + url);
            return false;
        }
        if (count > MAX_REDIRECT) {
            return false;
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (isLetsEncrypt(url)) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(TlsSocketFactory.LETS_ENCRYPT);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) { // force tls 1.2
            ((HttpsURLConnection) connection).setSSLSocketFactory(TlsSocketFactory.FORCE_TLS1_2);
        }
        connection.setInstanceFollowRedirects(false);
        try {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (os == null) {
                connection.setRequestMethod("HEAD");
            }
            connection.addRequestProperty("Accept-Encoding", "gzip");
            int code = connection.getResponseCode();
            LogUtils.d("url: " + url + ", code: " + code);
            if (code == STATUS_304) {
                return false;
            } else if (isRedirected(code) && os != null) {
                String location = connection.getHeaderField("Location");
                LogUtils.d("redirect to " + location);
                return retrieveContent(new URL(location), headers, os, count + 1);
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
            if (os == null) {
                return true;
            }
            if ("gzip".equals(encoding)) {
                return FileUtils.copy(new GZIPInputStream(connection.getInputStream()), os, true);
            } else {
                return FileUtils.copy(connection.getInputStream(), os, true);
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

    private static class TlsSocketFactory extends SSLSocketFactory {

        static final SSLSocketFactory FORCE_TLS1_2 = new TlsSocketFactory(null);

        static final SSLSocketFactory LETS_ENCRYPT = new TlsSocketFactory(Sha1TrustManager.LETS_ENCRYPT);

        private final SSLSocketFactory socketFactory;

        private TlsSocketFactory(TrustManager[] trustManagers) {
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustManagers, null);
                socketFactory = context.getSocketFactory();
            } catch (GeneralSecurityException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return socketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return socketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return wrapTls(socketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return wrapTls(socketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return wrapTls(socketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return wrapTls(socketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return wrapTls(socketFactory.createSocket(address, port, localAddress, localPort));
        }

        private Socket wrapTls(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
            }
            return socket;
        }

    }

}
