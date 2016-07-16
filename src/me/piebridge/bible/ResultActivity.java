package me.piebridge.bible;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.List;

import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/1.
 */
public class ResultActivity extends BaseActivity {

    public static final String ITEMS = "items";
    public static final String SEARCH = "search";

    private String search;
    private List<OsisItem> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        search = intent.getStringExtra(SEARCH);
        items = intent.getParcelableArrayListExtra(ITEMS);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    protected String getInitialOsis() {
        return getOsis(0);
    }

    @Override
    protected int getInitialPosition() {
        return 0;
    }

    @Override
    protected int retrieveOsisCount() {
        return items.size();
    }

    @Override
    public Bundle retrieveOsis(int position, String osis) {
        Bundle bundle = super.retrieveOsis(position, osis);
        OsisItem item = items.get(position);
        bundle.putString(VERSE_START, item.verseStart);
        bundle.putString(VERSE_END, item.verseEnd);
        bundle.putString(PREV, getOsis(position - 1));
        bundle.putString(NEXT, getOsis(position + 1));
        bundle.putString(SEARCH, search);
        return bundle;
    }

    private String getOsis(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index).toOsis();
        } else {
            return "";
        }
    }

    @Override
    protected void initializeHeader(View header) {
        header.findViewById(R.id.items).setBackgroundResource(0);
        View versionView = header.findViewById(R.id.version);
        if (versionView != null) {
            versionView.setOnClickListener(this);
        }
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis, View header) {
        String book = BibleUtils.getBook(osis);
        int osisPosition = bible.getPosition(Bible.TYPE.OSIS, book);
        String bookName = bible.get(Bible.TYPE.BOOK, osisPosition);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        String title = BibleUtils.getBookChapterVerse(bookName, chapterVerse);

        TextView itemsView = (TextView) header.findViewById(R.id.items);
        itemsView.setText(title);
        itemsView.setVisibility(View.VISIBLE);

        TextView versionView = (TextView) header.findViewById(R.id.version);
        if (versionView != null) {
            versionView.setText(bible.getVersionName(bible.getVersion()));
        }

        View reading = header.findViewById(R.id.reading);
        if (reading != null) {
            reading.setVisibility(View.VISIBLE);
        }

        updateTaskDescription(title);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            startReading();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void startReading() {
        Intent intent = new Intent(this, ReadingActivity.class);
        startActivity(intent);
        finish();
    }

}