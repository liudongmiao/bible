package me.piebridge.bible.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import me.piebridge.bible.Bible;
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
        Bible bible = Bible.getInstance(context.getApplicationContext());
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(getKey(), bible.getVersion()).apply();

        String[] versions = bible.get(Bible.TYPE.VERSION).toArray(new String[0]);
        setEntryValues(versions);
        String[] humanVersions = new String[versions.length];
        for (int i = 0; i < versions.length; i++) {
            humanVersions[i] = bible.getVersionFullname(versions[i]);
        }
        setEntries(humanVersions);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        Bible bible = Bible.getInstance(getContext().getApplicationContext());
        if (!ObjectUtils.equals(bible.getVersion(), value)) {
            bible.setVersion(value);
        }
        super.setSummary(bible.getVersionFullname(value));
    }

}
