package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.FontsizeFragment;
import me.piebridge.bible.fragment.SettingsFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2017/6/26.
 */

public class SettingsActivity extends ToolbarActivity {

    private static final String FRAGMENT_FONT_SIZE = "font-size";

    private String defaultFontsizeTitle;
    private String defaultFontsizeKey;

    private String fontsizeTitle;
    private String fontsizeKey;

    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        showBack(true);

        BibleApplication application = (BibleApplication) getApplication();
        String version = application.getVersion();
        fontsizeTitle = getString(R.string.settings_fontsize, application.getName(version));
        fontsizeKey = AbstractReadingActivity.FONT_SIZE + "-" + BibleUtils.removeDemo(version);

        defaultFontsizeTitle = getString(R.string.settings_fontsize, getString(R.string.settings_fontsize_reset));
        defaultFontsizeKey = AbstractReadingActivity.FONT_SIZE + "-default";

        settingsFragment = new SettingsFragment();

        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, settingsFragment)
                .commit();
    }

    public String getWebviewData() {
        Intent intent = getIntent();
        if (intent != null) {
            return FileUtils.uncompressAsString(intent.getByteArrayExtra(ReadingActivity.WEBVIEW_DATA));
        } else {
            return null;
        }
    }

    public void showFontsizeDialog(String key, String title, int value) {
        final String tag = FRAGMENT_FONT_SIZE;
        FragmentManager manager = getSupportFragmentManager();
        FontsizeFragment fragment = (FontsizeFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new FontsizeFragment();
        fragment.setBody(getWebviewData());
        fragment.setFontsize(key, title, value);
        fragment.show(manager, tag);
        LogUtils.d("show font size, key: " + key + ", title: " + title + ", value: " + value);
    }

    public String getDefaultFontsizeTitle() {
        return defaultFontsizeTitle;
    }

    public String getDefaultFontsizeKey() {
        return defaultFontsizeKey;
    }

    public int getDefaultFontsizeValue() {
        return PreferenceManager.getDefaultSharedPreferences(getApplication())
                .getInt(getDefaultFontsizeKey(), FontsizeFragment.FONTSIZE_DEFAULT);
    }

    public String getFontsizeTitle() {
        return fontsizeTitle;
    }

    public String getFontsizeKey() {
        return fontsizeKey;
    }

    public int getFontsizeValue(int defaultFontsize) {
        return PreferenceManager.getDefaultSharedPreferences(getApplication())
                .getInt(getFontsizeKey(), defaultFontsize);
    }

    public void updateFontsize() {
        settingsFragment.updateFontsize();
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.manifest_settings));
    }

    @Override
    protected boolean hasFinished() {
        return false;
    }

}
