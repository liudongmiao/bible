package me.piebridge.bible.utils;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

/**
 * Created by thom on 16/7/16.
 */
public class DeprecationUtils {

    private DeprecationUtils() {

    }

    public static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return fromHtmlDeprecatedN(source);
        }
    }

    @SuppressWarnings("deprecation")
    private static Spanned fromHtmlDeprecatedN(String source) {
        return Html.fromHtml(source);
    }

}
