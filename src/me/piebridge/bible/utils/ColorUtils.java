package me.piebridge.bible.utils;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Created by thom on 16/7/4.
 */
public class ColorUtils {

    private static DecimalFormat DF = new DecimalFormat("#.##");

    private static final int A = 24;
    private static final int R = 16;
    private static final int G = 8;
    private static final double MAX = 255.0;

    private ColorUtils() {

    }

    public static String rgba(int color) {
        // 0xAARRGGBB
        int a = (color >>> A) & 0xff;
        int r = (color >>> R) & 0xff;
        int g = (color >>> G) & 0xff;
        int b = color & 0xff;
        return String.format(Locale.US, "rgba(%d, %d, %d, %s)", r, g, b, DF.format(a / MAX));
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

        // blend: WebKit/Source/platform/graphics/Color.cpp
        int d = 0xff * (sourceA + dropA) - sourceA * dropA;
        int targetA = d / 0xff;
        int targetR = (sourceR * sourceA * (0xff - dropA) + 0xff * dropA * dropR) / d;
        int targetG = (sourceG * sourceA * (0xff - dropA) + 0xff * dropA * dropG) / d;
        int targetB = (sourceB * sourceA * (0xff - dropA) + 0xff * dropA * dropB) / d;

        return String.format(Locale.US, "rgba(%d, %d, %d, %s)", targetR, targetG, targetB,
                DF.format(targetA / MAX));
    }

    public static int replaceAlpha(int source, int alpha) {
        return (source & 0xffffff) | (alpha & 0xff000000);
    }

}
