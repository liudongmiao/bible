/*
 * vim: set sw=4 ts=4:
 * Author: Liu DongMiao <liudongmiao@gmail.com>
 * Created  : TIMESTAMP
 * Modified : TIMESTAMP
 */

package me.piebridge.bible;

public class Log {

    private static boolean debug = false;
    private static boolean error = false;
    private static boolean warning = false;

    public static int d(String tag, String msg) {
        if (debug) {
            return android.util.Log.d(tag, msg);
        }
        return 0;
    }

    public static int e(String tag, String msg) {
        if (error) {
            return android.util.Log.e(tag, msg);
        }
        return 0;
    }

    public static int w(String tag, String msg) {
        if (warning) {
            return android.util.Log.w(tag, msg);
        }
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        if (error) {
            return android.util.Log.e(tag, msg, tr);
        }
        return 0;
    }
}
