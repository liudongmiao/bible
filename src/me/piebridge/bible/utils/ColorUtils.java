package me.piebridge.bible.utils;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Created by thom on 16/7/4.
 */
public class ColorUtils {

    private static DecimalFormat DF = new DecimalFormat("#.##");

    private ColorUtils() {

    }

    public static String rgba(int color) {
        // 0xAARRGGBB
        int a = (color >>> 24) & 0xff;
        int r = (color >>> 16) & 0xff;
        int g = (color >>> 8) & 0xff;
        int b = color & 0xff;
        return String.format(Locale.US, "rgba(%d, %d, %d, %s)", r, g, b, DF.format(a / 255.0));
    }

    public static String blend(int source, int drop) {
        int sourceA = (source >>> 24) & 0xff;
        int sourceR = (source >>> 16) & 0xff;
        int sourceG = (source >>> 8) & 0xff;
        int sourceB = source & 0xff;

        int dropA = (drop >>> 24) & 0xff;
        int dropR = (drop >>> 16) & 0xff;
        int dropG = (drop >>> 8) & 0xff;
        int dropB = drop & 0xff;

        // blend: WebKit/Source/platform/graphics/Color.cpp
        int d = 255 * (sourceA + dropA) - sourceA * dropA;
        int targetA = d / 255;
        int targetR = (sourceR * sourceA * (255 - dropA) + 255 * dropA * dropR) / d;
        int targetG = (sourceG * sourceA * (255 - dropA) + 255 * dropA * dropG) / d;
        int targetB = (sourceB * sourceA * (255 - dropA) + 255 * dropA * dropB) / d;

        return String.format(Locale.US, "rgba(%d, %d, %d, %s)", targetR, targetG, targetB,
                DF.format(targetA / 255.0));
    }

    public static int replaceAlpha(int source, int alpha) {
        return (source & 0xffffff) | (alpha & 0xff000000);
    }

}
