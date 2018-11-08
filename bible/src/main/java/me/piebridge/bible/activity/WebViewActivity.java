package me.piebridge.bible.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.material.appbar.AppBarLayout;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.InfoFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/11/7.
 */
public class WebViewActivity extends DrawerActivity implements AppBarLayout.OnOffsetChangedListener {

    private WebView webView;

    private Calendar calendar;

    private boolean loading;

    private String mTitle;

    private static final char[] JIANYV_COM = new char[] {
            'j', 'i', 'a', 'n', 'y', 'v', '.', 'c', 'o', 'm'
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drawer_webview);
        setupDrawer();
        AppBarLayout appBarLayout = findViewById(R.id.appbar);
        if (appBarLayout != null) {
            appBarLayout.addOnOffsetChangedListener(this);
        }

        webView = findViewById(R.id.webview);
        webView.setWebViewClient(new BibleWebViewClient(this));

        webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        File externalCacheDir = getExternalCacheDir();
        String cachePath;
        if (externalCacheDir != null) {
            cachePath = externalCacheDir.getPath();
        } else {
            cachePath = getCacheDir().getPath();
        }
        webView.getSettings().setDatabasePath(cachePath);
        webView.getSettings().setAppCachePath(cachePath);
        webView.getSettings().setAppCacheEnabled(true);

        calendar = Calendar.getInstance();
        loadData(0);
    }

    private void loadData(int delta) {
        calendar.add(Calendar.DATE, delta);
        loadData(calendar.getTime());
    }

    private void loadData(Date time) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://");
        sb.append(getString(R.string.odb_url_prefix));
        sb.append(".");
        sb.append(JIANYV_COM);
        sb.append("/");
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(time);
        sb.append(date);
        sb.append("?calendar-redirect=true&post-type=post");
        String url = sb.toString();
        LogUtils.d("load url: " + url);
        setTitle(getString(R.string.menu_odb_link) + " " + date);
        loading = true;
        invalidateOptionsMenu();
        webView.loadUrl(sb.toString());
    }

    @Override
    public void onResume() {
        super.onResume();
        setCheckedItem(R.id.menu_odb);
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBar, int offset) {
        View decorView = getWindow().getDecorView();
        // http://stackoverflow.com/questions/31872653
        if (appBar.getHeight() + offset == 0) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            decorView.setSystemUiVisibility(0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.odb, menu);
        return !loading;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_yesterday:
                loadData(-1);
                return true;
            case R.id.action_tomorrow:
                loadData(1);
                return true;
            case R.id.action_about:
                showAbout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showAbout() {
        InfoFragment infoFragment = new InfoFragment();
        infoFragment.setMessage(getString(R.string.menu_odb_link), getString(R.string.odb_about_detail));
        infoFragment.show(getSupportFragmentManager(), "fragment-webview");
    }

    public void showItems(String version, ArrayList<OsisItem> items) {
        BibleApplication application = (BibleApplication) getApplication();
        if (!TextUtils.isEmpty(version)) {
            application.setVersion(version.toLowerCase(Locale.US));
        }
        Intent intent = new Intent(this, ReadingItemsActivity.class);
        intent.putParcelableArrayListExtra(ReadingItemsActivity.ITEMS, items);
        startActivity(setFinished(intent, false));
    }

    void onPageFinished(String title) {
        LogUtils.d("onPageFinished, title: " + title + ", previous: " + title);
        if (!TextUtils.isEmpty(title) && !ObjectUtils.equals(title, mTitle) && !title.startsWith("http")) {
            mTitle = title;
            setTitle(title);
            loading = false;
            invalidateOptionsMenu();
        }
    }

    private static class BibleWebViewClient extends WebViewClient {

        private final WeakReference<WebViewActivity> mReference;

        BibleWebViewClient(WebViewActivity webViewActivity) {
            mReference = new WeakReference<>(webViewActivity);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            WebViewActivity webViewActivity = mReference.get();
            if (webViewActivity != null) {
                webViewActivity.onPageFinished(view.getTitle());
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            LogUtils.d("will override url: " + url);
            if (handleSearchBible(url)) {
                return true;
            } else {
                view.loadUrl(url);
                return false;
            }
        }

        private boolean handleSearchBible(String url) {
            Uri uri = Uri.parse(url);
            String search = uri.getQueryParameter("search");
            if (TextUtils.isEmpty(search)) {
                return false;
            }
            WebViewActivity webViewActivity = mReference.get();
            BibleApplication application = (BibleApplication) webViewActivity.getApplication();
            ArrayList<OsisItem> items = OsisItem.parseSearch(search, application);
            BibleUtils.fixItems(items);
            if (!items.isEmpty()) {
                String version = uri.getQueryParameter("version");
                webViewActivity.showItems(version, items);
                return true;
            } else {
                return false;
            }
        }
    }

}
