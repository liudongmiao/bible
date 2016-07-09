package me.piebridge.bible;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.List;

import me.piebridge.bible.fragment.GridAdapter;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ThemeUtils;

public class SelectVersionActivity extends Activity implements GridAdapter.GridChecker, AdapterView.OnItemClickListener {

    private Bible bible;
    private String version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_version);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        bible = Bible.getInstance(this);
        version = bible.getVersion();
        List<String> versions = bible.get(Bible.TYPE.VERSION);

        String font = BibleUtils.getFontPath(this);
        Typeface typeface = TextUtils.isEmpty(font) ? null : Typeface.createFromFile(font);
        GridAdapter versionAdapter = new GridAdapter(this, this, typeface);
        versionAdapter.setData(versions);

        GridView gridView = (GridView) findViewById(R.id.gridView);
        gridView.setAdapter(versionAdapter);
        gridView.setOnItemClickListener(this);
        gridView.smoothScrollToPosition(versions.indexOf(version));
    }

    @Override
    public String getGridName(String key) {
        return bible.getVersionFullname(key);
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
        intent.putExtra(BaseActivity.VERSION, newVersion);
        setResult(Activity.RESULT_OK, intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

}
