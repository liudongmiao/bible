package me.piebridge.bible.preference;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.activity.SearchActivity;
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

        List<String> versions = new ArrayList<>(application.getSortedVersions());
        setEntryValues(versions.toArray(new String[0]));
        int length = versions.size();
        String[] humanVersions = new String[versions.size()];
        for (int i = 0; i < length; i++) {
            humanVersions[i] = application.getFullname(versions.get(i));
        }
        setEntries(humanVersions);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        BibleApplication application = (BibleApplication) getContext().getApplicationContext();
        if (!ObjectUtils.equals(application.getVersion(), value)) {
            application.setVersion(value);
            updateVersion();
        }
        super.setSummary(application.getFullname(value));
    }

    private void updateVersion() {
        Context context = getContext();
        while (!(context instanceof Activity)) {
            context = ((ContextThemeWrapper) context).getBaseContext();
        }
        if (context instanceof SearchActivity) {
            ((SearchActivity) context).updateVersion();
        }
    }

}
