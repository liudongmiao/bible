package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import java.util.List;

import me.piebridge.bible.Bible;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.ItemsAdapter;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/1.
 */
public class ReadingItemsActivity extends AbstractReadingActivity implements AdapterView.OnItemSelectedListener {

    public static final String ITEMS = "items";
    public static final String SEARCH = "search";

    private String search;
    private List<OsisItem> items;

    private Spinner itemsView;

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
        OsisItem item = items.get(position);
        Bundle bundle = super.retrieveOsis(position, item.toOsis());
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
        itemsView = (Spinner) header.findViewById(R.id.items);
        ItemsAdapter adapter = new ItemsAdapter(itemsView, this, R.layout.view_spinner_item, convertItems(items));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        itemsView.setAdapter(adapter);
        itemsView.setSelection(0);
        itemsView.setOnItemSelectedListener(this);
        if (items.size() <= 1) {
            header.findViewById(R.id.dropdown).setVisibility(View.GONE);
            itemsView.setEnabled(false);
        }

        initializeVersion(header);
    }

    @Override
    protected int getContentLayout() {
        return R.layout.activity_reading_items;
    }

    private CharSequence[] convertItems(List<OsisItem> items) {
        int size = items.size();
        CharSequence[] values = new CharSequence[size];
        for (int i = 0; i < size; ++i) {
            Bundle bundle = items.get(i).toBundle();
            values[i] = getTitle(bundle, bundle.getString(AbstractReadingActivity.OSIS));
        }
        return values;
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis) {
        String title = getTitle(bundle, osis);
        itemsView.setSelection(getCurrentPosition());
        updateTaskDescription(title);
    }

    @Override
    protected int getToolbarLayout() {
        return R.id.toolbar_reading_items;
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Bundle bundle = retrieveOsis(position, null);
        mAdapter.setData(position, bundle);
        prepare(position);
        mPager.setCurrentItem(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // do nothing
    }

}