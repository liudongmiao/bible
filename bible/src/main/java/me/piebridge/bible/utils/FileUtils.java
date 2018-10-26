package me.piebridge.bible.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by thom on 15/10/19.
 */
public class FileUtils {

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
            return bos.toString("UTF-8");
        }
    }

}
