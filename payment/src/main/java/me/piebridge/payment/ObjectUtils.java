package me.piebridge.payment;

import android.os.Build;

import java.util.Objects;

/**
 * Created by thom on 2018/11/8.
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

}
