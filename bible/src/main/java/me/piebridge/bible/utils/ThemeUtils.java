package me.piebridge.bible.utils;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import me.piebridge.bible.R;

/**
 * Created by thom on 15/10/18.
 */
public class ThemeUtils {

    private static final String THEME = "theme";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private ThemeUtils() {

    }

    public static void setTheme(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.dark : R.style.light);
    }

    public static void setDialogTheme(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.dark_dialog : R.style.light_dialog);
    }

    public static boolean isDark(Activity activity) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        return THEME_DARK.equals(sharedPreferences.getString(THEME, THEME_LIGHT));
    }

}
