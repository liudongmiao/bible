package me.piebridge.bible.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by thom on 15/10/19.
 */
public class FileUtils {

    private FileUtils() {

    }

    public static String readAsString(InputStream is) throws IOException {
        int length;
        byte[] bytes = new byte[0x2000];
        BufferedInputStream bis = new BufferedInputStream(is);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((length = bis.read(bytes)) != -1) {
            bos.write(bytes, 0, length);
        }
        bis.close();
        return bos.toString("UTF-8");
    }

}
