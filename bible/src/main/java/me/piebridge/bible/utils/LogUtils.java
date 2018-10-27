package me.piebridge.bible.utils;

import android.util.Log;

/**
 * Created by thom on 15/10/19.
 */
public class LogUtils {

    private static final String TAG = "Bible";

    private LogUtils() {

    }

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void d(String msg, Throwable tr) {
        Log.d(TAG, msg, tr);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void w(String msg, Throwable tr) {
        Log.w(TAG, msg, tr);
    }

}
