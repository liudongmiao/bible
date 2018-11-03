package me.piebridge.bible.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.appcompat.app.ActionBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.GridAdapter;
import me.piebridge.bible.utils.BibleUtils;

public class SelectVersionActivity extends ToolbarActivity implements GridAdapter.GridChecker, AdapterView.OnItemClickListener {

    private String version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_version);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        BibleApplication application = (BibleApplication) getApplication();
        version = application.getVersion();
        List<String> versions = new ArrayList<>(application.getVersions());
        Collections.sort(versions, application::compareFullname);

        String font = BibleUtils.getFontPath(this);
        Typeface typeface = TextUtils.isEmpty(font) ? null : Typeface.createFromFile(font);
        GridAdapter versionAdapter = new GridAdapter(this, this, typeface);
        versionAdapter.setData(versions);

        GridView gridView = findViewById(R.id.gridView);
        gridView.setNumColumns(1);
        gridView.setAdapter(versionAdapter);
        gridView.setOnItemClickListener(this);
        gridView.setSelection(versions.indexOf(version));
    }

    @Override
    public String getGridName(String key) {
        BibleApplication application = (BibleApplication) getApplication();
        return application.getFullname(key);
    }

    @Override
    public boolean isGridEnabled(String key) {
        return true;
    }

    @Override
    public boolean isGridChecked(String key) {
        return key.equals(version);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Adapter adapter = parent.getAdapter();
        String newVersion = String.valueOf(adapter.getItem(position));
        if (!newVersion.equals(version)) {
            setResult(newVersion);
        }
        finish();
    }

    private void setResult(String newVersion) {
        Intent intent = getIntent();
        intent.putExtra(AbstractReadingActivity.VERSION, newVersion);
        setResult(Activity.RESULT_OK, intent);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(getString(R.string.reading_translation));
    }

}
