package me.piebridge.bible.utils;

/**
 * Created by thom on 16/7/21.
 */
public class ObjectUtils {

    private ObjectUtils() {

    }

    /**
     * I'd like name it as equals, but sonar complains
     */
    public static boolean isIdentical(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

}
