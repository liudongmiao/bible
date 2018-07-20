package me.piebridge.bible.utils;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Created by thom on 16/7/4.
 */
public class ColorUtils {

    private static DecimalFormat DF = new DecimalFormat("#.###");

    private static final int A = 24;
    private static final int R = 16;
    private static final int G = 8;
    private static final double MAX = 255.0;
    private static final double A100 = 100.0;
    private static final double A1000 = 1000.0;

    private ColorUtils() {

    }

    private static double cssAlpha(int alpha) {
        // https://drafts.csswg.org/cssom/#serializing-css-values
        double rounded = Math.round(alpha * A100 / MAX) / A100;
        if (Math.round(rounded * MAX) == alpha) {
            return rounded;
        } else {
            return Math.round(alpha * A1000 / MAX) / A1000;
        }
    }

    public static String rgba(int color) {
        // 0xAARRGGBB
        int a = (color >>> A) & 0xff;
        int r = (color >>> R) & 0xff;
        int g = (color >>> G) & 0xff;
        int b = color & 0xff;
        if (a == 0xff) {
            return String.format(Locale.US, "#%02x%02x%02x", r, g, b);
        } else {
            return String.format(Locale.US, "rgba(%d, %d, %d, %s)", r, g, b, DF.format(cssAlpha(a)));
        }
    }

    public static String blend(int source, int drop) {
        int sourceA = (source >>> A) & 0xff;
        int sourceR = (source >>> R) & 0xff;
        int sourceG = (source >>> G) & 0xff;
        int sourceB = source & 0xff;
        if (sourceA == 0) {
            sourceA = 0xff;
        }

        int dropA = (drop >>> A) & 0xff;
        int dropR = (drop >>> R) & 0xff;
        int dropG = (drop >>> G) & 0xff;
        int dropB = drop & 0xff;
        if (dropA == 0) {
            dropA = 0xff;
        }

        // blend: third_party/blink/renderer/platform/graphics/color.cc
        int d = 0xff * (sourceA + dropA) - sourceA * dropA;
        int targetA = d / 0xff;
        int targetR = (sourceR * sourceA * (0xff - dropA) + 0xff * dropA * dropR) / d;
        int targetG = (sourceG * sourceA * (0xff - dropA) + 0xff * dropA * dropG) / d;
        int targetB = (sourceB * sourceA * (0xff - dropA) + 0xff * dropA * dropB) / d;

        return String.format(Locale.US, "rgba(%d, %d, %d, %s)", targetR, targetG, targetB,
                DF.format(cssAlpha(targetA)));
    }

    public static int resolve(Context context, int resId) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(resId, tv, true);
        if (isColor(tv.type)) {
            return tv.data;
        } else {
            return ContextCompat.getColor(context, tv.resourceId);
        }
    }

    private static boolean isColor(int type) {
        return type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT;
    }

}
