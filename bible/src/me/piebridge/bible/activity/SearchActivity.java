package me.piebridge.bible.activity;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.SearchView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import me.piebridge.bible.Bible;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.SearchFragment;
import me.piebridge.bible.provider.SearchProvider;
import me.piebridge.bible.utils.ChooserUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/19.
 */
public class SearchActivity extends ToolbarActivity implements SearchView.OnQueryTextListener, View.OnFocusChangeListener {

    public static final String OSIS_FROM = "osisFrom";

    public static final String OSIS_TO = "osisTo";

    public static final String CROSS = "cross";

    private boolean mHasFocus;

    private SearchView mSearchView;

    private SearchFragment mSearchFragment;

    private SearchRecentSuggestions mSuggestions;

    private Bible bible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        showBack(true);

        mSuggestions = new SearchRecentSuggestions(this,
                SearchProvider.AUTHORITY, SearchProvider.MODE);

        mSearchView = findViewById(R.id.searchView);
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(this);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
            mSearchView.setSearchableInfo(searchableInfo);
        }

        mSearchFragment = new SearchFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, mSearchFragment)
                .commit();

        bible = Bible.getInstance(getApplication());

        handleIntent(getIntent());
        updateTaskDescription(getTitle().toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.search, menu);
        return !mHasFocus;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear:
                mSuggestions.clearHistory();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (!TextUtils.isEmpty(query)) {
            doSearch(query, true, false);
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    private void doSearch(String query, boolean parse, boolean finished) {
        mSuggestions.saveRecentQuery(query, null);

        ArrayList<OsisItem> items;
        if (parse) {
            items = OsisItem.parseSearch(query, getApplication());
            fixItems(items);
            if (!items.isEmpty()) {
                LogUtils.d("items: " + items);
            }
        } else {
            items = new ArrayList<>();
        }
        if (!items.isEmpty()) {
            showItems(items, false, false);
        } else {
            Intent intent = new Intent(this, ResultsActivity.class);
            intent.setAction(Intent.ACTION_SEARCH);
            intent.putExtra(SearchManager.QUERY, query);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            intent.putExtra(OSIS_FROM, sharedPreferences.getString(SearchFragment.KEY_SEARCH_FROM, null));
            intent.putExtra(OSIS_TO, sharedPreferences.getString(SearchFragment.KEY_SEARCH_TO, null));

            LogUtils.d("intent: " + intent + ", extra: " + intent.getExtras());
            super.startActivity(setFinished(intent, finished));
        }
    }

    private void fixItems(ArrayList<OsisItem> items) {
        Iterator<OsisItem> it = items.iterator();
        while (it.hasNext()) {
            OsisItem item = it.next();
            if (TextUtils.isEmpty(item.chapter)) {
                it.remove();
            }
        }
    }

    @Override
    public void startActivity(Intent intent) {
        if ((getComponentName()).equals(intent.getComponent())) {
            String query = parseQuery(intent);
            if (!TextUtils.isEmpty(query)) {
                mSearchView.setQuery(query, true);
            }
        } else {
            super.startActivity(intent);
        }
    }

    private String parseQuery(Intent intent) {
        return intent.getStringExtra(SearchManager.QUERY);
    }

    private String lower(String version) {
        if (version == null) {
            return null;
        } else {
            return version.toLowerCase(Locale.US);
        }
    }

    private void handleIntent(Intent intent) {
        LogUtils.d("intent: " + intent + ", extra: " + intent.getExtras());
        String action = intent.getAction();
        if (action == null) {
            mSearchView.setQuery(parseQuery(intent), false);
            return;
        }
        String query = null;
        Uri data = null;
        switch (intent.getAction()) {
            case Intent.ACTION_SEND:
                query = intent.getStringExtra(Intent.EXTRA_TEXT);
                break;
            case Intent.ACTION_PROCESS_TEXT:
                query = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                break;
            case Intent.ACTION_VIEW:
                data = intent.getData();
                break;
            case Intent.ACTION_SEARCH:
                query = intent.getStringExtra(SearchManager.QUERY);
                break;
            default:
                break;
        }

        LogUtils.d("query: " + query + ", data: " + data);

        boolean cross = intent.getBooleanExtra(CROSS, false);

        if (!TextUtils.isEmpty(query) && (query.startsWith("http://") || query.startsWith("https://"))) {
            data = Uri.parse(query);
        }

        String version = null;
        if (data != null) {
            version = lower(data.getQueryParameter("version"));
            query = data.getQueryParameter("search");
            if (TextUtils.isEmpty(query)) {
                query = data.getQueryParameter("q");
            }
            if (TextUtils.isEmpty(query) && data.getPath() != null) {
                query = data.getPath().replaceAll("^/search/([^/]*).*$", "$1");
            }
        }

        if (version != null && bible.get(Bible.TYPE.VERSION).contains(version)) {
            bible.setVersion(version);
            mSearchFragment.updateVersion(version);
        }

        if (query != null) {
            ArrayList<OsisItem> items = OsisItem.parseSearch(query, getApplication());
            fixItems(items);
            if (!items.isEmpty() && !bible.checkItems(items)) {
                LogUtils.d("items: " + items);
                mSuggestions.saveRecentQuery(query, null);
                showItems(items, cross, true);
            } else if (data != null) {
                redirect(data);
            } else {
                doSearch(query, false, true);
            }
            finish();
        }
    }

    private void redirect(Uri data) {
        // FIXME: use chrome tabs
        Intent intent = new Intent(Intent.ACTION_VIEW, data);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        ChooserUtils.startActivityExcludeSelf(this, intent, null);
    }

    private void showItems(ArrayList<OsisItem> items, boolean cross, boolean finished) {
        Intent intent = new Intent(this, cross ? ReadingCrossActivity.class : ReadingItemsActivity.class);
        intent.putParcelableArrayListExtra(ReadingItemsActivity.ITEMS, items);
        intent.putExtra(Intent.EXTRA_REFERRER, getIntent().getAction());
        super.startActivity(setFinished(intent, finished));
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mSearchView) {
            mHasFocus = hasFocus;
            invalidateOptionsMenu();
        }
    }

    public void hideSoftInput() {
        mSearchView.clearFocus();
        mHasFocus = false;
    }

    @Override
    protected boolean hasFinished() {
        Intent intent = getIntent();
        if (intent.getAction() == null || intent.getBooleanExtra(CROSS, false)) {
            return false;
        } else {
            return super.hasFinished();
        }
    }

}
