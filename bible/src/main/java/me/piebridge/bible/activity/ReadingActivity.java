package me.piebridge.bible.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.lang.ref.WeakReference;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.fragment.SwitchVersionConfirmFragment;
import me.piebridge.bible.provider.VersionProvider;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingActivity extends AbstractReadingActivity {

    public static final String WEBVIEW_DATA = "webview-data";

    private static final int SIZE = 1189;
    private static final String FRAGMENT_CONFIRM = "fragment-confirm";

    private TextView bookView;
    private TextView chapterView;

    private BroadcastReceiver receiver;

    private String mTitle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        receiver = new Receiver(this);
    }

    @Override
    protected void initializeHeader(View header) {
        setupDrawer();
        bookView = header.findViewById(R.id.book);
        header.findViewById(R.id.book_button).setOnClickListener(this);

        chapterView = header.findViewById(R.id.chapter);
        header.findViewById(R.id.chapter_button).setOnClickListener(this);

        initializeVersion(header);
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis) {
        String book = BibleUtils.getBook(osis);
        BibleApplication application = (BibleApplication) getApplication();
        String bookName = application.getHuman(book);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        mTitle = BibleUtils.getBookChapterVerse(bookName, chapterVerse);

        bookView.setText(bookName);
        chapterView.setText(chapterVerse);
        updateTaskDescription(mTitle);

        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.onSelected();
        }
    }

    @Override
    protected int getToolbarLayout() {
        return R.id.toolbar_reading;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.drawer_reading;
    }

    @Override
    protected int retrieveOsisCount() {
        Uri uri = VersionProvider.CONTENT_URI_CHAPTERS.buildUpon().build();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndex(BaseColumns._COUNT));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot get chapter size", e);
        }
        return SIZE;
    }

    protected String getInitialOsis() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(OSIS, null);
    }

    protected int getInitialPosition() {
        return POSITION_UNKNOWN;
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onStop() {
        saveOsis();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        setCheckedItem(R.id.menu_reading);
        String currentVersion = getCurrentVersion();
        BibleApplication application = (BibleApplication) getApplication();
        String databaseVersion = application.getVersion();
        if (currentVersion != null && !currentVersion.equals(databaseVersion)) {
            LogUtils.d("version changed from " + currentVersion + " to " + databaseVersion);
            refreshAdapter();
        }
        if (BibleUtils.isDemoVersion(databaseVersion)) {
            String version = BibleUtils.removeDemo(databaseVersion);
            int id = getResources().getIdentifier(version, "string", BuildConfig.APPLICATION_ID);
            if (id != 0) {
                String filename = getString(id);
                if (!application.isDownloading(filename)) {
                    LogUtils.d("will download " + filename + " for " + databaseVersion);
                    application.download(filename, true);
                }
            }
        }
    }

    private void saveOsis() {
        String osis = getCurrentOsis();
        if (!TextUtils.isEmpty(osis)) {
            String book = BibleUtils.getBook(osis);
            String chapter = BibleUtils.getChapter(osis);
            BibleApplication application = (BibleApplication) getApplication();
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(OSIS, osis)
                    .putString(book, chapter)
                    .putString("version", application.getVersion()).apply();
        }
    }

    @Override
    protected void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            intent.putExtra(WEBVIEW_DATA, FileUtils.compress(currentFragment.getBody()));
        }

        startActivityForResult(intent, REQUEST_CODE_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            ReadingFragment currentFragment = getCurrentFragment();
            if (currentFragment != null && isChanged(currentFragment.getArguments())) {
                LogUtils.d("settings changed, refresh");
                refresh();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String version = intent.getStringExtra(AbstractReadingActivity.VERSION);
        if (!TextUtils.isEmpty(version) && !ObjectUtils.equals(version, getCurrentVersion())) {
            BibleApplication application = (BibleApplication) getApplication();
            if (application.setVersion(version)) {
                refreshAdapter();
            }
        }
    }

    protected void onReceive(Intent intent) {
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            refreshAdapter();
        }
    }

    @Override
    protected void switchToVersion(String version) {
        BibleApplication application = (BibleApplication) getApplication();
        String currentOsis = getCurrentOsis();
        if (TextUtils.isEmpty(currentOsis) || application.hasChapter(version, currentOsis)) {
            super.switchToVersion(version);
        } else {
            showSwitchToVersion(version);
        }
    }

    private void showSwitchToVersion(String version) {
        final String tag = FRAGMENT_CONFIRM;
        FragmentManager manager = getSupportFragmentManager();
        SwitchVersionConfirmFragment fragment = (SwitchVersionConfirmFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new SwitchVersionConfirmFragment();
        BibleApplication application = (BibleApplication) getApplication();
        fragment.setMessage(getString(R.string.reading_confirm),
                getString(R.string.reading_no_chapter_confirm, application.getFullname(version), mTitle), version);
        fragment.show(manager, tag);
    }

    public void confirmSwitchToVersion(String extra) {
        super.switchToVersion(extra);
    }

    private static class Receiver extends BroadcastReceiver {

        private WeakReference<ReadingActivity> mReference;

        public Receiver(ReadingActivity activity) {
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ReadingActivity readingActivity = mReference.get();
            if (readingActivity != null) {
                readingActivity.onReceive(intent);
            }
        }

    }

}
