package me.piebridge.bible.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.SelectAdapter;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ObjectUtils;

public class SelectVersionActivity extends ToolbarActivity implements SelectAdapter.OnSelectedListener {

    private String version;

    private SelectAdapter versionAdapter;
    private RecyclerView recyclerView;

    private TranslationTassk mTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_version);
        showBack(true);

        BibleApplication application = (BibleApplication) getApplication();
        version = application.getVersion();

        String font = BibleUtils.getFontPath(this);
        Typeface typeface = TextUtils.isEmpty(font) ? null : Typeface.createFromFile(font);
        versionAdapter = new SelectAdapter(this, typeface);

        recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(versionAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mTask != null) {
            mTask.cancel(false);
        }
        mTask = new TranslationTassk(this);
        mTask.execute();
    }

    @Override
    public void onStop() {
        mTask.cancel(false);
        super.onStop();
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

    void loadTranslations(List<SelectAdapter.SelectItem> items, int position) {
        versionAdapter.setData(items);
        LinearLayoutManager layoutManager = ObjectUtils.requireNonNull((LinearLayoutManager) recyclerView.getLayoutManager());
        int firstPosition = layoutManager.findFirstVisibleItemPosition();
        int lastPosition = layoutManager.findLastVisibleItemPosition();
        if (position < firstPosition || position > lastPosition) {
            recyclerView.scrollToPosition(position);
        }
    }

    private static class TranslationTassk extends AsyncTask<Void, Void, Translations> {

        private final WeakReference<SelectVersionActivity> mReference;

        public TranslationTassk(SelectVersionActivity activity) {
            mReference = new WeakReference<>(activity);
        }

        @Override
        protected Translations doInBackground(Void... voids) {
            SelectVersionActivity activity = mReference.get();
            if (activity == null || isCancelled()) {
                return null;
            }
            BibleApplication application = (BibleApplication) activity.getApplication();
            String version = application.getVersion();
            Collection<String> versions = application.getSortedVersions();
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

            return new Translations(items, position);
        }

        @Override
        protected void onPostExecute(Translations result) {
            SelectVersionActivity activity = mReference.get();
            if (activity != null && !isCancelled()) {
                activity.loadTranslations(result.items, result.position);
            }
        }

    }

    private static class Translations {
        final int position;
        final List<SelectAdapter.SelectItem> items;

        public Translations(List<SelectAdapter.SelectItem> items, int position) {
            this.items = items;
            this.position = position;
        }
    }


}
