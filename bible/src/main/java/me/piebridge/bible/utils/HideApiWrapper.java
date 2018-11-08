package me.piebridge.bible.utils;

import android.os.SystemProperties;

/**
 * Created by thom on 2018/5/11.
 */
public class HideApiWrapper {

    private HideApiWrapper() {

    }

    public static String getProperty(String key, String def) {
        try {
            return SystemProperties.get(key, def);
        } catch (LinkageError ignore) {
            return def;
        }
    }

}
