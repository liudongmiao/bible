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
    private static boolean emulator = android.os.Build.PRODUCT.contains("sdk") || android.os.Build.PRODUCT.contains("mb612");

    public static int d(String tag, String msg) {
        if (debug || emulator) {
            return android.util.Log.d(tag, msg);
        }
        return 0;
    }

    public static int e(String tag, String msg) {
        if (error || emulator) {
            return android.util.Log.e(tag, msg);
        }
        return 0;
    }

    public static int w(String tag, String msg) {
        if (warning || emulator) {
            return android.util.Log.w(tag, msg);
        }
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        if (error || emulator) {
            return android.util.Log.e(tag, msg, tr);
        }
        return 0;
    }
}
