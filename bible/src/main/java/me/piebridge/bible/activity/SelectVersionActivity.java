package me.piebridge.bible.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.SelectAdapter;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ObjectUtils;

public class SelectVersionActivity extends ToolbarActivity implements SelectAdapter.OnSelectedListener {

    private String version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_version);
        showBack(true);

        BibleApplication application = (BibleApplication) getApplication();
        version = application.getVersion();
        List<String> versions = new ArrayList<>(application.getVersions());
        Collections.sort(versions, application::compareFullname);

        String font = BibleUtils.getFontPath(this);
        Typeface typeface = TextUtils.isEmpty(font) ? null : Typeface.createFromFile(font);
        SelectAdapter versionAdapter = new SelectAdapter(this, typeface);
        List<SelectAdapter.SelectItem> items = new ArrayList<>();
        int position = -1;
        for (String key : versions) {
            String value = application.getFullname(key);
            boolean checked = ObjectUtils.equals(key, version);
            if (checked) {
                position = items.size();
            }
            items.add(new SelectAdapter.SelectItem(key, value, checked, true));
        }
        versionAdapter.setData(items);

        RecyclerView recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(versionAdapter);
        if (position >= 0) {
            recyclerView.scrollToPosition(position);
        }
    }

    @Override
    public void onSelected(String key) {
        if (!ObjectUtils.equals(key, version)) {
            setResult(key);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.translation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_manage_translations:
                startActivity(new Intent(this, VersionsActivity.class));
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public String toString() {
        return "SelectTranslation";
    }

}
