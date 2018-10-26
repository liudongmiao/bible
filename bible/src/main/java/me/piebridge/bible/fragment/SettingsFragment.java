package me.piebridge.bible.fragment;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.activity.SettingsActivity;

/**
 * Created by thom on 2017/6/26.
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private Preference fontSizePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.settings, rootKey);
        findPreference("theme").setOnPreferenceChangeListener(this);

        fontSizePreference = findPreference(AbstractReadingActivity.FONT_SIZE);
        SettingsActivity activity = (SettingsActivity) getActivity();
        if (TextUtils.isEmpty(activity.getWebviewData())) {
            fontSizePreference.getParent().removePreference(fontSizePreference);
        } else {
            fontSizePreference.setKey(activity.getFontsizeKey());
            fontSizePreference.setTitle(activity.getFontsizeTitle());
            fontSizePreference.setSummary(Integer.toString(activity.getFontsizeValue()));
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
            ((SettingsActivity) getActivity()).showFontsizeDialog();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    public void updateFontsize() {
        if (fontSizePreference != null) {
            SettingsActivity activity = (SettingsActivity) getActivity();
            fontSizePreference.setSummary(Integer.toString(activity.getFontsizeValue()));
        }
    }

}
