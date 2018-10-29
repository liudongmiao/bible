package me.piebridge.bible.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/10/21.
 */
public class VersionPreference extends ListPreference {

    public VersionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVersions(context);
    }

    private void initVersions(Context context) {
        BibleApplication application = (BibleApplication) getContext().getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(getKey(), application.getVersion()).apply();

        String[] versions = application.getVersions().toArray(new String[0]);
        setEntryValues(versions);
        String[] humanVersions = new String[versions.length];
        for (int i = 0; i < versions.length; i++) {
            humanVersions[i] = application.getFullname(versions[i]);
        }
        setEntries(humanVersions);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        BibleApplication application = (BibleApplication) getContext().getApplicationContext();
        if (!ObjectUtils.equals(application.getVersion(), value)) {
            application.setVersion(value);
        }
        super.setSummary(application.getFullname(value));
    }

}
