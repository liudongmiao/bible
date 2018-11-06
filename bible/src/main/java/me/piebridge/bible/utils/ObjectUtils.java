package me.piebridge.bible.utils;

import android.os.Build;

import java.util.Objects;

/**
 * Created by thom on 16/7/21.
 */
public class ObjectUtils {

    private ObjectUtils() {

    }

    /**
     * @param a an object
     * @param b an object to be compared with {@code a} for equality
     * @return {@code true} if the arguments are equal to each other
     * and {@code false} otherwise
     * @see java.util.Objects#equals(Object, Object)
     */
    public static boolean equals(Object a, Object b) { // NOSONAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Objects.equals(a, b);
        } else {
            return (a == b) || (a != null && a.equals(b));
        }
    }

    /**
     * @param object the object reference to check for nullity
     * @param <T>    the type of the reference
     * @return {@code obj} if not {@code null}
     * @throws NullPointerException if {@code obj} is {@code null}
     * @see java.util.Objects#requireNonNull(Object)
     */
    public static <T> T requireNonNull(T object) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Objects.requireNonNull(object);
        } else if (object != null) {
            return object;
        } else {
            throw new NullPointerException();
        }
    }

}
