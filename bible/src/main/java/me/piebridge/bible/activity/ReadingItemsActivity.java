package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.HiddenArrayAdapter;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 16/7/1.
 */
public class ReadingItemsActivity extends AbstractReadingActivity implements AdapterView.OnItemSelectedListener {

    public static final String ITEMS = "items";
    public static final String SEARCH = "search";

    private String search;
    private List<OsisItem> items;

    private Spinner spinner;
    private TextView itemsView;

    private boolean mNoteChanged;
    private boolean mHighlightChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initItems();
        super.onCreate(savedInstanceState);
        showBack(true);
    }

    protected void initItems() {
        Intent intent = getIntent();
        setSearch(intent.getStringExtra(SEARCH));
        setItems(intent.getParcelableArrayListExtra(ITEMS));
    }

    protected void setSearch(String search) {
        this.search = search;
    }

    protected void setItems(List<OsisItem> items) {
        this.items = items;
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
    public Bundle retrieveOsis(int position, String x) {
        OsisItem item = items.get(position);
        String osis = item.toOsis();
        Bundle bundle = super.retrieveOsis(position, osis);
        LogUtils.d("retrieveOsis, position: " + position + ", osis: " + osis);

        if (!bundle.containsKey(CONTENT)) {
            BibleApplication application = (BibleApplication) getApplication();
            String version = application.getVersion();
            LogUtils.w("no " + osis + " in " + version);
            String content = getString(R.string.reading_no_chapter_info, application.getFullname(version), osis);
            bundle.putInt(ID, -1);
            bundle.putString(CURR, osis);
            bundle.putString(OSIS, osis);
            bundle.putString(HUMAN, BibleUtils.getBook(osis));
            bundle.putByteArray(CONTENT, FileUtils.compress(content));
            bundle.putString(HIGHLIGHTED, "");
            bundle.putBundle(NOTES, new Bundle());
        }

        bundle.putString(VERSE_START, item.verseStart);
        bundle.putString(VERSE_END, item.verseEnd);
        bundle.putString(PREV, getOsis(position - 1));
        bundle.putString(NEXT, getOsis(position + 1));
        bundle.putString(SEARCH, search);
        bundle.putString(VERSES, item.verses);
        return bundle;
    }

    protected String getOsis(int index) {
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
        updateSpinner(header, 0);
        initializeVersion(header);
    }

    protected void updateSpinner(View header, int position) {
        ArrayAdapter adapter = new HiddenArrayAdapter(this, R.layout.view_spinner_item, convertItems(items));
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(position);
        spinner.setOnItemSelectedListener(this);
        if (items.size() <= 1) {
            header.findViewById(R.id.dropdown).setVisibility(View.GONE);
        } else {
            header.findViewById(R.id.items_button).setOnClickListener(this);
        }
    }

    @Override
    protected int getContentLayout() {
        return R.layout.activity_reading_items;
    }

    private CharSequence[] convertItems(List<OsisItem> items) {
        int size = items.size();
        CharSequence[] values = new CharSequence[size];
        for (int i = 0; i < size; ++i) {
            OsisItem item = items.get(i);
            values[i] = getTitle(item);
        }
        return values;
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis) {
        int position = bundle.getInt(POSITION);
        String title = getTitle(bundle, getOsis(position));
        itemsView.setText(title);
        updateTaskDescription(title);
    }

    @Override
    protected int getToolbarLayout() {
        return R.id.toolbar_reading_items;
    }

    protected String getTitle(Bundle bundle, String osis) {
        String book = BibleUtils.getBook(osis);
        BibleApplication application = (BibleApplication) getApplication();
        String bookName = application.getHuman(book);
        String verses = bundle.getString(VERSES);
        if (TextUtils.isEmpty(verses)) {
            String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
            return BibleUtils.getBookChapterVerse(bookName, chapterVerse);
        } else {
            return bookName + " " + BibleUtils.getChapter(osis, this) + ":" + verses;
        }
    }

    protected String getTitle(OsisItem item) {
        String book = BibleUtils.getBook(item.toOsis());
        BibleApplication application = (BibleApplication) getApplication();
        String bookName = application.getHuman(book);
        String verses = item.verses;
        if (TextUtils.isEmpty(verses)) {
            String chapterVerse = BibleUtils.getChapterVerse(this, item.toBundle());
            return BibleUtils.getBookChapterVerse(bookName, chapterVerse);
        } else {
            return bookName + " " + BibleUtils.getChapter(item.toOsis(), this) + ":" + verses;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int currentItem = mPager.getCurrentItem();
        LogUtils.d("selected " + position + ", current " + currentItem);
        if (currentItem != position) {
            prepare(position);
        }
    }

    @Override
    protected void prepareOnWork(int position) {
        Bundle bundle = mAdapter.getData(position);
        OsisItem item = items.get(position);
        LogUtils.d("item.osis: " + item.toOsis() + ", curr: " + bundle.getString(CURR));
        if (!ObjectUtils.equals(item.toOsis(), bundle.getString(CURR))) {
            bundle.putAll(retrieveOsis(position, null));
        }
        prepareOnWork(bundle);
    }

    @Override
    protected int loadData(int position, String osis, int count) {
        LogUtils.d("load data, position: " + position);
        Bundle bundle = retrieveOsis(position, osis);
        mAdapter.setData(position, bundle);
        if (count != mAdapter.getCount()) {
            bundle.putInt(ADAPTER_COUNT, count);
        }
        prepareOnWork(bundle);
        return position;
    }

    @Override
    protected void prepareOnMain(Bundle bundle) {
        super.prepareOnMain(bundle);
        mPager.setCurrentItem(bundle.getInt(POSITION));
    }

    @Override
    protected void refreshAdapterOnWork() {
        refreshAdapterOnWork(getCurrentPosition());
    }

    protected void refreshAdapterOnWork(int position) {
        int count = retrieveOsisCount();
        String osis = getOsis(position);
        Bundle bundle = retrieveOsis(position, osis);
        mAdapter.clearData();
        mAdapter.setData(position, bundle);
        preparePrev(position, bundle.getString(PREV));
        prepareNext(position, bundle.getString(NEXT), count);

        LogUtils.d("refreshAdapterOnWork, position: " + position + ", count: " + count);
        afterRefreshAdapterOnWork(position, count);
    }

    @Override
    protected void refreshAdapterOnMain(int position, int count) {
        LogUtils.d("refreshAdapterOnMain, position: " + position + ", count: " + count);
        updateSpinner(findHeader(), position);
        Bundle bundle = mAdapter.getData(position);
        updateHeader(bundle, getOsis(position));
        updateVersion();
        prepareOnMain(position);
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

    @Override
    public void saveNotes(long id, String verses, String content) {
        super.saveNotes(id, verses, content);
        mNoteChanged = true;
    }

    @Override
    public void deleteNote(long id, String verses) {
        super.deleteNote(id, verses);
        mNoteChanged = true;
    }

    @Override
    public void saveHighlight(String verses) {
        super.saveHighlight(verses);
        mHighlightChanged = true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult();
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void finish() {
        setResult();
        super.finish();
    }

    protected void setResult() {
        Intent data = new Intent();
        data.putExtra(NOTE_CHANGED, mNoteChanged);
        data.putExtra(HIGHLIGHT_CHANGED, mHighlightChanged);
        LogUtils.d("set result, data: " + data.getExtras());
        setResult(RESULT_OK, data);
    }

    @Override
    public boolean isChanged(Bundle bundle) {
        return false;
    }

    @Override
    protected boolean shouldRemove() {
        return false;
    }

}