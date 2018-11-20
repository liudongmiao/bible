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
        LogUtils.d("ReadingActivity, onCreate");
        selectVersion(getIntent());
        super.onCreate(savedInstanceState);
        receiver = new Receiver(this);
    }

    @Override
    protected boolean shouldRemove() {
        return true;
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
        super.saveOsis();
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

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            ReadingFragment currentFragment = getCurrentFragment();
            if (currentFragment != null) {
                intent.putExtra(WEBVIEW_DATA, FileUtils.compress(currentFragment.getBody()));
            }
        }
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            ReadingFragment currentFragment = getCurrentFragment();
            if (currentFragment != null && isChanged(currentFragment.getArguments())) {
                LogUtils.d("settings changed, refresh");
                refresh();
            }
        } else if (requestCode == REQUEST_CODE_ANNOTATION && data != null) {
            if (data.getBooleanExtra(NOTE_CHANGED, false) || data.getBooleanExtra(HIGHLIGHT_CHANGED, false)) {
                LogUtils.d("annotation changed, refresh");
                refresh();
            }
        } else if (requestCode == REQUEST_CODE_OSIS && data != null) {
            String osis = data.getStringExtra(OSIS);
            if (!TextUtils.isEmpty(osis)) {
                LogUtils.d("continue reading " + osis);
                int position = getCurrentPosition();
                Bundle bundle = retrieveOsis(position, osis);
                if (!TextUtils.isEmpty(bundle.getString(CURR))) {
                    position = bundle.getInt(ID) - 1;
                    mAdapter.notifyDataSetChanged();
                    refresh(position, bundle, true);
                    mPager.setCurrentItem(position);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) { // from VersionsActivity
        LogUtils.d("onNewIntent: intent: " + intent);
        super.onNewIntent(intent);
        if (selectVersion(intent)) {
            refreshAdapter();
        }
    }

    private boolean selectVersion(Intent intent) {
        String version = intent.getStringExtra(AbstractReadingActivity.VERSION);
        if (!TextUtils.isEmpty(version) && !ObjectUtils.equals(version, getCurrentVersion())) {
            BibleApplication application = (BibleApplication) getApplication();
            return application.setVersion(version);
        } else {
            return false;
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
