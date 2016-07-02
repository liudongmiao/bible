package me.piebridge.bible.utils;

import android.app.Activity;
import android.content.Context;
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
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        activity.setTheme(THEME_LIGHT.equals(sp.getString(THEME, THEME_LIGHT)) ? R.style.light : R.style.dark);
    }

    public static void switchTheme(Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = sp.getString(ThemeUtils.THEME, ThemeUtils.THEME_LIGHT);
        if (ThemeUtils.THEME_LIGHT.equals(theme)) {
            sp.edit().putString(ThemeUtils.THEME, ThemeUtils.THEME_DARK).apply();
        } else {
            sp.edit().putString(ThemeUtils.THEME, ThemeUtils.THEME_LIGHT).apply();
        }
    }

}
