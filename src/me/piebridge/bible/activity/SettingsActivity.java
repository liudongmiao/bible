package me.piebridge.bible.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;

import me.piebridge.bible.R;
import me.piebridge.bible.fragment.SettingsFragment;

/**
 * Created by thom on 2017/6/26.
 */

public class SettingsActivity extends ToolbarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        SettingsFragment settingsFragment = new SettingsFragment();

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.content, settingsFragment)
                .commit();
    }

}
