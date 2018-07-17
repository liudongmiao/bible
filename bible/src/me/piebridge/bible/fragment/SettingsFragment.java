package me.piebridge.bible.fragment;

import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;

import me.piebridge.bible.R;

/**
 * Created by thom on 2017/6/26.
 */
public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.settings, rootKey);
    }

}
