package me.piebridge.bible.activity;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import me.piebridge.bible.Bible;
import me.piebridge.bible.Provider;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.FontsizeFragment;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                return cursor.getInt(cursor.getColumnIndex(BaseColumns._COUNT));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot get chapter size", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
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
    protected void onStop() {
        saveOsis();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        String currentVersion = getCurrentVersion();
        if (currentVersion != null && currentVersion.equals(bible.getVersion())) {
            refresh();
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

}
