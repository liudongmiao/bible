package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.List;

import me.piebridge.bible.Bible;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/1.
 */
public class ReadingItemsActivity extends AbstractReadingActivity {

    public static final String ITEMS = "items";
    public static final String SEARCH = "search";

    private String search;
    private List<OsisItem> items;

    private TextView itemsView;
    private TextView versionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        search = intent.getStringExtra(SEARCH);
        items = intent.getParcelableArrayListExtra(ITEMS);
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
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
        itemsView = (TextView) header.findViewById(R.id.items);
        itemsView.setVisibility(View.VISIBLE);
        itemsView.setBackgroundResource(0);

        versionView = (TextView) header.findViewById(R.id.version);
        if (versionView != null) {
            versionView.setOnClickListener(this);
        }
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis, View header) {
        String title = getTitle(bundle, osis);
        itemsView.setText(title);
        if (versionView != null) {
            versionView.setText(bible.getVersionName(bible.getVersion()));
            updateTaskDescription(title);
        }
    }

    protected String getTitle(Bundle bundle, String osis) {
        String book = BibleUtils.getBook(osis);
        int osisPosition = bible.getPosition(Bible.TYPE.OSIS, book);
        String bookName = bible.get(Bible.TYPE.BOOK, osisPosition);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        return BibleUtils.getBookChapterVerse(bookName, chapterVerse);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home && Intent.ACTION_VIEW.equals(getIntent().getStringExtra(Intent.EXTRA_REFERRER))) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            startActivity(launchIntent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}