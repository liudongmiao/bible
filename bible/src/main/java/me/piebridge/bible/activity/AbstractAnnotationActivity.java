package me.piebridge.bible.activity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.component.AnnotationComponent;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

import static me.piebridge.bible.activity.AbstractReadingActivity.HIGHLIGHT_CHANGED;
import static me.piebridge.bible.activity.AbstractReadingActivity.NOTE_CHANGED;

/**
 * Created by thom on 2018/11/6.
 */
public abstract class AbstractAnnotationActivity extends ToolbarActivity implements View.OnClickListener,
        SearchView.OnQueryTextListener, SearchView.OnCloseListener {

    private static final String KEY_SORT_ANNOTATION = "sort_annoation";

    private static final int SEARCH = 0;
    private static final int RESULT = 1;

    private static final int REQUEST_CODE_VERSION = 1002;

    private static final int REQUEST_CODE_ANNOTATION = 1003;

    private boolean mNoteChanged;
    private boolean mHighlightChanged;

    private String mQuery;

    private RecyclerView recyclerView;
    private DividerItemDecoration itemDecoration;
    private TextView versionView;

    private Handler workHandler;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentLayout());
        showBack(true);

        recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemDecoration = new DividerItemDecoration(this, LinearLayoutManager.VERTICAL);

        versionView = findViewById(R.id.version);
        if (versionView != null) {
            findViewById(R.id.version_button).setOnClickListener(this);
        }

        SearchView mSearchView = findViewById(R.id.searchView);
        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnCloseListener(this);
        }

        mainHandler = new MainHandler(this);
        workHandler = new WorkHandler(this);

        updateVersion();
        search();
    }

    @Override
    protected int getToolbarActionbarId() {
        return R.id.toolbar_actionbar;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.annotation, menu);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { // sort by book requires instr, since 5.X
            // https://developer.android.com/reference/android/database/sqlite/package-summary
            menu.removeItem(R.id.action_sort_by_book);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.action_sort_by_book:
                return updateSort(AnnotationComponent.SORT_BOOK);
            case R.id.action_sort_by_time:
                return updateSort(AnnotationComponent.SORT_TIME);
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
        closeAdapter();
        super.finish();
    }

    private void setResult() {
        Intent data = new Intent();
        data.putExtra(NOTE_CHANGED, mNoteChanged);
        data.putExtra(HIGHLIGHT_CHANGED, mHighlightChanged);
        LogUtils.d("set result, data: " + data.getExtras());
        setResult(RESULT_OK, data);
    }

    private boolean updateSort(String newSort) {
        String sort = getSort();
        if (!ObjectUtils.equals(sort, newSort)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(KEY_SORT_ANNOTATION, newSort).apply();
            search();
        }
        return true;
    }

    protected void search() {
        LogUtils.d(getClass().getSimpleName() + ", search");
        workHandler.sendEmptyMessage(SEARCH);
    }

    protected abstract int getContentLayout();

    protected abstract Cursor search(String book, String query, String order);

    protected void doSearch() {
        Cursor cursor = search(getBook(), getQuery(), getSort());
        mainHandler.obtainMessage(RESULT, cursor).sendToTarget();
    }

    protected void showResult(Cursor cursor) {
        LogUtils.d(getClass().getSimpleName() + ", showResult");
        if (cursor == null) {
            if (recyclerView.getItemDecorationCount() > 0) {
                recyclerView.removeItemDecoration(itemDecoration);
            }
            updateAdapter(new NoResultAdapter());
        } else {
            if (recyclerView.getItemDecorationCount() == 0) {
                recyclerView.addItemDecoration(itemDecoration);
            }
            updateAdapter(new ResultAdapter(this, cursor));
        }
    }

    private void updateAdapter(RecyclerView.Adapter newAdapter) {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        recyclerView.setAdapter(newAdapter);
        if (adapter instanceof ResultAdapter) {
            ((ResultAdapter) adapter).close();
        }
    }

    private void closeAdapter() {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof ResultAdapter) {
            ((ResultAdapter) adapter).close();
        }
    }

    protected String getQuery() {
        return mQuery;
    }

    private String getSort() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // sort by book requires instr, since 5.X
            return PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(KEY_SORT_ANNOTATION, AnnotationComponent.SORT_TIME);
        } else {
            return AnnotationComponent.SORT_TIME;
        }
    }

    private String getBook() {
        return null;
    }

    protected final String getHuman(String book) {
        BibleApplication application = (BibleApplication) getApplication();
        return application.getHuman(book);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.version_button) {
            Intent intent = new Intent(this, SelectVersionActivity.class);
            startActivityForResult(intent, REQUEST_CODE_VERSION);
        } else if (v instanceof CardView) {
            OsisItem item = (OsisItem) v.getTag();
            LogUtils.d("show item: " + item);
            if (item != null) {
                showItem(item);
            }
        }
    }

    protected final void updateVersion() {
        if (versionView != null) {
            BibleApplication application = (BibleApplication) getApplication();
            versionView.setText(application.getName(application.getVersion()));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtils.d("requestCode: " + requestCode + ", resultCode: " + resultCode);
        if (requestCode == REQUEST_CODE_VERSION && data != null) {
            BibleApplication application = (BibleApplication) getApplication();
            String version = data.getStringExtra(AbstractReadingActivity.VERSION);
            if (!TextUtils.isEmpty(version) && !ObjectUtils.equals(version, application.getVersion())) {
                if (application.setVersion(version)) {
                    updateVersion();
                    search();
                }
            }
        } else if (requestCode == REQUEST_CODE_ANNOTATION && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                extras.size();
                LogUtils.d("extra: " + extras);
            }
            if (data.getBooleanExtra(NOTE_CHANGED, false)) {
                mNoteChanged = true;
                onNoteChanged();
            }
            if (data.getBooleanExtra(HIGHLIGHT_CHANGED, false)) {
                mHighlightChanged = true;
                onHighlightChanged();
            }
        }
    }

    protected abstract void onHighlightChanged();

    protected abstract void onNoteChanged();

    private void showItem(OsisItem item) {
        Intent intent = new Intent(this, ReadingItemsActivity.class);
        ArrayList<OsisItem> items = new ArrayList<>();
        if (TextUtils.isEmpty(item.chapter)) {
            BibleApplication application = (BibleApplication) getApplication();
            String book = item.book;
            for (String chapter : application.getChapters(book)) {
                items.add(new OsisItem(book, chapter));
            }
        } else {
            items.add(item);
        }
        intent.putParcelableArrayListExtra(ReadingItemsActivity.ITEMS, items);
        startActivityForResult(intent, REQUEST_CODE_ANNOTATION);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        try {
            return true;
        } finally {
            mQuery = query;
            search();
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mQuery = newText;
        return false;
    }

    @Override
    public boolean onClose() {
        try {
            return false;
        } finally {
            mQuery = null;
            search();
        }
    }

    protected String prepareContent(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(AnnotationComponent.COLUMN_CONTENT));
    }

    private static class WorkHandler extends Handler {

        private final WeakReference<AbstractAnnotationActivity> mReference;

        public WorkHandler(AbstractAnnotationActivity activity) {
            super(newLooper());
            this.mReference = new WeakReference<>(activity);
        }

        private static Looper newLooper() {
            HandlerThread thread = new HandlerThread("Query");
            thread.start();
            return thread.getLooper();
        }

        @Override
        public void handleMessage(Message msg) {
            AbstractAnnotationActivity activity = mReference.get();
            switch (msg.what) {
                case SEARCH:
                    activity.doSearch();
                    break;
            }
        }
    }

    private static class MainHandler extends Handler {

        private final WeakReference<AbstractAnnotationActivity> mReference;

        public MainHandler(AbstractAnnotationActivity activity) {
            super(activity.getMainLooper());
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            AbstractAnnotationActivity activity = mReference.get();
            switch (msg.what) {
                case RESULT:
                    activity.showResult((Cursor) msg.obj);
                    break;
            }
        }

    }

    private static class ResultAdapter extends RecyclerView.Adapter {

        private final WeakReference<AbstractAnnotationActivity> mReference;

        private final Cursor mCursor;
        private final String mQuery;
        private final int mColor;

        private final int columnOsisIndex;
        private final int columnVersesIndex;
        private final int columnDateIndex;

        private boolean closed;

        public ResultAdapter(AbstractAnnotationActivity activity, Cursor cursor) {
            this.mReference = new WeakReference<>(activity);
            this.mCursor = cursor;
            this.mQuery = activity.getQuery();
            this.mColor = ContextCompat.getColor(activity, R.color.blue_alpha);

            this.columnOsisIndex = mCursor.getColumnIndex(AnnotationComponent.COLUMN_OSIS);
            this.columnVersesIndex = mCursor.getColumnIndex(AnnotationComponent.COLUMN_VERSES);
            this.columnDateIndex = mCursor.getColumnIndex(AnnotationComponent.COLUMN_UPDATETIME);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            CardView cardView = (CardView) inflater.inflate(R.layout.item_annotation, parent, false);
            cardView.setOnClickListener(ObjectUtils.requireNonNull(mReference.get()));
            return new AnnotationViewHolder(cardView);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (closed) {
                LogUtils.w("onBindViewHolder on closed adapter");
                return;
            }
            AbstractAnnotationActivity activity = ObjectUtils.requireNonNull(mReference.get());
            mCursor.moveToPosition(position);
            AnnotationViewHolder annotationViewHolder = (AnnotationViewHolder) holder;
            bindHuman(activity, annotationViewHolder);
            bindVerse(activity, annotationViewHolder);
            bindDate(annotationViewHolder);
            bindContent(activity, annotationViewHolder);
        }

        private void bindHuman(AbstractAnnotationActivity activity, AnnotationViewHolder holder) {
            String osis = mCursor.getString(columnOsisIndex);
            String book = BibleUtils.getBook(osis);
            holder.humanView.setText(activity.getHuman(book));
        }

        private void bindVerse(AbstractAnnotationActivity activity, AnnotationViewHolder holder) {
            String osis = mCursor.getString(columnOsisIndex);
            String book = BibleUtils.getBook(osis);
            String chapter = BibleUtils.getChapter(osis);
            String verses = mCursor.getString(columnVersesIndex);
            String string = activity.getString(R.string.annotation_verses, chapter, verses);
            holder.verseView.setText(string);
            OsisItem osisItem = new OsisItem(book, chapter);
            osisItem.verseStart = BibleUtils.getVerse(verses);
            osisItem.verses = verses;
            holder.cardView.setTag(osisItem);
        }

        private void bindDate(AnnotationViewHolder holder) {
            CharSequence text = null;
            if (!mCursor.isNull(columnDateIndex)) {
                try {
                    text = DateUtils.formatSameDayTime(mCursor.getLong(columnDateIndex) * 1000,
                            System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT);
                } catch (RuntimeException e) {
                    LogUtils.d("cannot format time", e);
                }
            }
            holder.dateView.setText(text);
        }


        private void bindContent(AbstractAnnotationActivity activity, AnnotationViewHolder holder) {
            String content = activity.prepareContent(mCursor);
            if (TextUtils.isEmpty(content)) {
                BibleApplication application = (BibleApplication) activity.getApplication();
                String bookChapterVerse = holder.humanView.getText() + " " + holder.verseView.getText();
                content = activity.getString(R.string.annotation_no_book, bookChapterVerse, application.getFullname());
                holder.contentView.setText(content);
            } else {
                content = BibleUtils.fix((BibleApplication) activity.getApplication(), content);
                holder.contentView.setText(content, TextView.BufferType.SPANNABLE);
                if (!TextUtils.isEmpty(mQuery)) {
                    selectQuery(holder.contentView, content);
                }
            }
        }

        private void selectQuery(TextView textView, String content) {
            Spannable span = (Spannable) textView.getText();
            String lowerContent = content.toLowerCase(Locale.US);
            int index = -1;
            int queryLength = mQuery.length();
            while ((index = lowerContent.indexOf(mQuery, index + 1)) >= 0) {
                span.setSpan(new BackgroundColorSpan(mColor), index, index + queryLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        @Override
        public int getItemCount() {
            if (closed) {
                LogUtils.w("getItemCount on closed adapter");
                return 0;
            }
            return mCursor.getCount();
        }

        public void close() {
            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
            }
            closed = true;
        }

    }

    private static class AnnotationViewHolder extends RecyclerView.ViewHolder {

        final CardView cardView;

        final TextView humanView;

        final TextView verseView;

        final TextView dateView;

        final TextView contentView;

        public AnnotationViewHolder(CardView view) {
            super(view);
            this.cardView = view;
            this.humanView = view.findViewById(R.id.human);
            this.verseView = view.findViewById(R.id.verse);
            this.dateView = view.findViewById(R.id.date);
            this.contentView = view.findViewById(R.id.content);
        }
    }


}
