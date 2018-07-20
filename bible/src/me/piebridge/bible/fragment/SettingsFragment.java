package me.piebridge.bible.fragment;

import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.Preference;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 2017/6/26.
 */
public class SettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.settings, rootKey);
        findPreference("theme").setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if ("theme".equals(preference.getKey())) {
            getActivity().recreate();
        }
        return true;
    }

}
