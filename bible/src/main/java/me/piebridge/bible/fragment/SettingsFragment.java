package me.piebridge.bible.fragment;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SettingsActivity;
import me.piebridge.bible.utils.NumberUtils;

/**
 * Created by thom on 2017/6/26.
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private Preference fontsizeDefaultPreference;

    private Preference fontsizeVersionPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.settings, rootKey);
        findPreference("theme").setOnPreferenceChangeListener(this);

        fontsizeDefaultPreference = findPreference("fontsizeDefault");
        fontsizeVersionPreference = findPreference("fontsizeVersion");
        SettingsActivity activity = (SettingsActivity) getActivity();
        if (TextUtils.isEmpty(activity.getWebviewData())) {
            removePreferences(fontsizeDefaultPreference, fontsizeVersionPreference);
        } else {
            fontsizeDefaultPreference.setKey(activity.getDefaultFontsizeKey());
            fontsizeVersionPreference.setKey(activity.getFontsizeKey());

            fontsizeDefaultPreference.setTitle(activity.getDefaultFontsizeTitle());
            fontsizeVersionPreference.setTitle(activity.getFontsizeTitle());

            int defaultFontsizeValue = activity.getDefaultFontsizeValue();
            int versionFontsizeValue = activity.getFontsizeValue(defaultFontsizeValue);

            fontsizeDefaultPreference.setSummary(Integer.toString(defaultFontsizeValue));
            fontsizeVersionPreference.setSummary(Integer.toString(versionFontsizeValue));
        }
    }

    private void removePreferences(Preference... preferences) {
        for (Preference preference : preferences) {
            if (preference != null) {
                preference.getParent().removePreference(preference);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if ("theme".equals(preference.getKey())) {
            getActivity().recreate();
        }
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key != null && key.startsWith("fontsize")) {
            ((SettingsActivity) getActivity()).showFontsizeDialog(key, preference.getTitle().toString(), getIntValue(preference));
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private int getIntValue(Preference preference) {
        return NumberUtils.parseInt(preference.getSummary().toString(), FontsizeFragment.FONTSIZE_DEFAULT);
    }

    public void updateFontsize() {
        SettingsActivity activity = (SettingsActivity) getActivity();
        if (activity != null) {
            int defaultFontsizeValue = activity.getDefaultFontsizeValue();
            int versionFontsizeValue = activity.getFontsizeValue(defaultFontsizeValue);
            if (fontsizeDefaultPreference != null) {
                fontsizeDefaultPreference.setSummary(Integer.toString(defaultFontsizeValue));
            }
            if (fontsizeVersionPreference != null) {
                fontsizeVersionPreference.setSummary(Integer.toString(versionFontsizeValue));
            }
        }
    }

}
