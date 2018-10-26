package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import me.piebridge.bible.Bible;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.FontsizeFragment;
import me.piebridge.bible.fragment.SettingsFragment;

/**
 * Created by thom on 2017/6/26.
 */

public class SettingsActivity extends ToolbarActivity {

    private static final String FRAGMENT_FONT_SIZE = "font-size";

    private String fontsizeTitle;

    private String fontsizeKey;

    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Bible bible = Bible.getInstance(getApplication());
        fontsizeTitle = getString(R.string.fontsize, bible.getVersionName(bible.getVersion()));
        fontsizeKey = AbstractReadingActivity.FONT_SIZE + "-" + bible.getVersion();

        settingsFragment = new SettingsFragment();

        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, settingsFragment)
                .commit();
    }

    public String getWebviewData() {
        Intent intent = getIntent();
        if (intent != null) {
            return intent.getStringExtra(ReadingActivity.WEBVIEW_DATA);
        } else {
            return null;
        }
    }

    public void showFontsizeDialog() {
        final String tag = FRAGMENT_FONT_SIZE;
        FragmentManager manager = getSupportFragmentManager();
        FontsizeFragment fragment = (FontsizeFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new FontsizeFragment();
        fragment.setBody(getWebviewData());
        fragment.setFontsize(getFontsizeValue());
        fragment.show(manager, tag);
    }

    public String getFontsizeTitle() {
        return fontsizeTitle;
    }

    public String getFontsizeKey() {
        return fontsizeKey;
    }

    public int getFontsizeValue() {
        return PreferenceManager.getDefaultSharedPreferences(getApplication())
                .getInt(getFontsizeKey(), FontsizeFragment.FONTSIZE_DEFAULT);
    }

    public void updateFontsize() {
        settingsFragment.updateFontsize();
    }

}
