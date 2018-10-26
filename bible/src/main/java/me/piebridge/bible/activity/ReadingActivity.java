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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.lang.ref.WeakReference;

import me.piebridge.bible.Bible;
import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.Provider;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.FontsizeFragment;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingActivity extends AbstractReadingActivity {

    public static final String WEBVIEW_DATA = "webview-data";

    private static final int SIZE = 1189;

    private TextView bookView;
    private TextView chapterView;

    private static final int REQUEST_CODE_SETTINGS = 1190;

    private int mFontSize;

    private BroadcastReceiver receiver;

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
        int osisPosition = bible.getPosition(Bible.TYPE.OSIS, book);
        String bookName = bible.get(Bible.TYPE.BOOK, osisPosition);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        String title = BibleUtils.getBookChapterVerse(bookName, chapterVerse);

        bookView.setText(bookName);
        chapterView.setText(chapterVerse);
        updateTaskDescription(title);

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
        Uri uri = Provider.CONTENT_URI_CHAPTERS.buildUpon().build();
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
        String currentVersion = getCurrentVersion();
        String databaseVersion = bible.getVersion();
        if (currentVersion != null && !currentVersion.equals(databaseVersion)) {
            refreshAdapter();
        }
        if (BibleUtils.isDemoVersion(databaseVersion)) {
            String version = BibleUtils.removeDemo(databaseVersion);
            int id = getResources().getIdentifier(version, "string", BuildConfig.APPLICATION_ID);
            if (id != 0) {
                BibleApplication application = (BibleApplication) getApplication();
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
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(OSIS, osis)
                    .putString(book, chapter)
                    .putString("version", bible.getVersion()).apply();
        }
    }

    @Override
    protected void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            intent.putExtra(WEBVIEW_DATA, currentFragment.getBody());
        }

        mFontSize = getFontSize();
        startActivityForResult(intent, REQUEST_CODE_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            int fontSize = getFontSize();
            if (mFontSize != fontSize) {
                updateFontSize(fontSize);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private int getFontSize() {
        Bible bible = Bible.getInstance(getApplication());
        String key = AbstractReadingActivity.FONT_SIZE + "-" + bible.getVersion();
        return PreferenceManager.getDefaultSharedPreferences(getApplication()).getInt(key, FontsizeFragment.FONTSIZE_DEFAULT);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String version = intent.getStringExtra(AbstractReadingActivity.VERSION);
        if (!TextUtils.isEmpty(version) && !ObjectUtils.equals(version, getCurrentVersion())) {
            if (bible.setVersion(version)) {
                refreshAdapter();
            }
        }
    }

    protected void onReceive(Intent intent) {
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            refreshAdapter();
        }
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
