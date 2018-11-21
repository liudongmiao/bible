package me.piebridge.bible.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by thom on 15/10/19.
 */
public class FileUtils {

    public static final String UTF_8 = "UTF-8";

    private static final int CACHE_SIZE = 8192;

    private FileUtils() {

    }

    public static String readAsString(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (
                BufferedInputStream bis = new BufferedInputStream(is)
        ) {
            copy(bis, baos);
        }
        return baos.toString(UTF_8);
    }

    public static boolean copy(InputStream is, OutputStream os) throws IOException {
        return copy(is, os, false);
    }

    public static boolean copy(InputStream is, OutputStream os, boolean cancellable) throws IOException {
        int length;
        byte[] bytes = new byte[CACHE_SIZE];
        while ((length = is.read(bytes)) != -1) {
            if (cancellable && Thread.currentThread().isInterrupted()) {
                LogUtils.d("cancel copy, is: " + is + ", os: " + os);
                return false;
            }
            os.write(bytes, 0, length);
        }
        return true;
    }

    public static byte[] compress(String string) {
        try {
            return compress(string.getBytes(UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static byte[] compress(byte[] uncompressed) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (
                GZIPOutputStream gos = new GZIPOutputStream(baos)
        ) {
            gos.write(uncompressed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static byte[] uncompress(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (
                GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))
        ) {
            copy(gis, baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static String uncompressAsString(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        try {
            return new String(uncompress(compressed), UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
