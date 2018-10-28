package me.piebridge.bible.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        try (
                BufferedInputStream bis = new BufferedInputStream(is);
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ) {
            int length;
            byte[] bytes = new byte[CACHE_SIZE];
            while ((length = bis.read(bytes)) != -1) {
                bos.write(bytes, 0, length);
            }
            return bos.toString(UTF_8);
        }
    }

    public static byte[] compress(String string) {
        try {
            return compress(string.getBytes(UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static byte[] compress(byte[] uncompressed) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gos = new GZIPOutputStream(baos);
            gos.write(uncompressed);
            gos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] uncompress(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            GZIPInputStream gis = new GZIPInputStream(bais);
            byte[] buffer = new byte[CACHE_SIZE];
            int length;
            while ((length = gis.read(buffer)) != -1) {
                if (length > 0) {
                    baos.write(buffer, 0, length);
                }
            }
            gis.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
