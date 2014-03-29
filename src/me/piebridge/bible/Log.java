/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2013 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

public class Log {

    public static boolean on = false;
    private static boolean debug = false;
    private static boolean error = false;
    private static boolean warning = false;
    private static boolean emulator = android.os.Build.PRODUCT.contains("sdk");

    public static int d(String tag, String msg) {
        if (on || debug || emulator) {
            return android.util.Log.d(tag, msg);
        }
        return 0;
    }

    public static int e(String tag, String msg) {
        if (on || error || emulator) {
            return android.util.Log.e(tag, msg);
        }
        return 0;
    }

    public static int w(String tag, String msg) {
        if (on || warning || emulator) {
            return android.util.Log.w(tag, msg);
        }
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        if (on || error || emulator) {
            return android.util.Log.e(tag, msg, tr);
        }
        return 0;
    }

    public static void setOn(Boolean value) {
        if (value != null) {
            on = value;
        }
    }
}
