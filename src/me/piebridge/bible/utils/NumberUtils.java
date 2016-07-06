package me.piebridge.bible.utils;

import android.text.TextUtils;

/**
 * Created by thom on 16/7/5.
 */
public class NumberUtils {

    private NumberUtils() {

    }

    public static int parseInt(String s) {
        if (!TextUtils.isEmpty(s) && TextUtils.isDigitsOnly(s)) {
            return Integer.parseInt(s);
        } else {
            return 0;
        }
    }

}
