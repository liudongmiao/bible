package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import java.util.List;

import me.piebridge.bible.Bible;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.HiddenArrayAdapter;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/1.
 */
public class ReadingItemsActivity extends AbstractReadingActivity implements AdapterView.OnItemSelectedListener {

    public static final String ITEMS = "items";
    public static final String SEARCH = "search";
    public static final String FINISHED = "finished";

    private String search;
    private List<OsisItem> items;

    private Spinner spinner;
    private TextView itemsView;

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
        itemsView = header.findViewById(R.id.items);
        spinner = header.findViewById(R.id.spinner);
        ArrayAdapter adapter = new HiddenArrayAdapter(this, R.layout.view_spinner_item, convertItems(items));
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(this);
        if (items.size() <= 1) {
            header.findViewById(R.id.dropdown).setVisibility(View.GONE);
        } else {
            header.findViewById(R.id.items_button).setOnClickListener(this);
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
        itemsView.setText(title);
        updateTaskDescription(title);
    }

    @Override
    protected int getToolbarLayout() {
        return R.id.toolbar_reading_items;
    }

    protected String getTitle(Bundle bundle, String osis) {
        String book = BibleUtils.getBook(osis);
        int osisPosition = bible.getPosition(Bible.TYPE.OSIS, book);
        if (osisPosition < 0) {
            return null;
        }
        String bookName = bible.get(Bible.TYPE.BOOK, osisPosition);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        return BibleUtils.getBookChapterVerse(bookName, chapterVerse);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Bundle extras = getIntent().getExtras();
                if (extras != null && extras.containsKey(FINISHED)) {
                    if (extras.getBoolean(FINISHED, false)) {
                        startActivity(new Intent(this, SearchActivity.class));
                    }
                    finish();
                    return true;
                }
                break;
            default:
                break;
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

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.items_button) {
            spinner.performClick();
        } else {
            super.onClick(v);
        }
    }

}