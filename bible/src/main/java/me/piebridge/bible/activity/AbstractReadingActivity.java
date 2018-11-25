package me.piebridge.bible.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.ReadingAdapter;
import me.piebridge.bible.bridge.ReadingBridge;
import me.piebridge.bible.bridge.ReadingHandler;
import me.piebridge.bible.fragment.AddNotesFragment;
import me.piebridge.bible.fragment.FontsizeFragment;
import me.piebridge.bible.fragment.ProgressFragment;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.fragment.ShowAnnotationFragment;
import me.piebridge.bible.fragment.ShowNotesFragment;
import me.piebridge.bible.provider.VersionProvider;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ChooserUtils;
import me.piebridge.bible.utils.ColorUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ObjectUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 15/10/18.
 */
public abstract class AbstractReadingActivity extends DrawerActivity
        implements ReadingBridge.Bridge, View.OnClickListener, ViewPager.OnPageChangeListener,
        AppBarLayout.OnOffsetChangedListener {

    private static final int LATER = 50;

    private static final int CHECK_DEMO = 1001;
    private static final int INITIALIZE = 1002;
    private static final int INITIALIZE_COUNT = 1003;
    private static final int INITIALIZE_DATA = 1004;
    private static final int PREPARE_DATA = 1005;
    private static final int REFRESH = 1006;
    private static final int PREPARE_POSITION = 1007;
    private static final int PREPARE = 1008;
    private static final int REFRESH_ADAPTER = 1009;
    private static final int SET_VERSION = 1010;
    private static final int CHECK_CHAPTER = 1011;
    private static final int JUMP = 1012;
    private static final int SHOW_PROGRESS = 1013;
    private static final int HIDE_PROGRESS = 1014;

    private static final String FRAGMENT_PROGRESS = "fragment_progress";

    public static final String NOTE_CHANGED = "note_changed";
    public static final String HIGHLIGHT_CHANGED = "highlight_changed";

    private static final String DEFAULT_FONT_FAMILY = "GentiumPlus";

    private ActionMode actionMode;

    public static final String CSS = "css";
    public static final String OSIS = "osis";
    public static final String ID = "id";
    public static final String CURR = "curr";
    public static final String NEXT = "next";
    public static final String PREV = "prev";
    public static final String HUMAN = "human";
    public static final String CONTENT = "content";
    public static final String POSITION = "position";
    public static final String NOTES = "notes";
    public static final String VERSE = "verse";
    public static final String VERSE_START = "verseStart";
    public static final String VERSE_END = "verseEnd";
    public static final String VERSES = "verses";
    public static final String FONT_SIZE = "fontsize";
    public static final String FONT_PATH = "fontPath";
    public static final String CROSS = "cross";
    public static final String SHANGTI = "shangti";
    public static final String VERSION = "version";
    public static final String SEARCH = "search";
    public static final String HIGHLIGHTED = "highlighted";
    public static final String RED = "red";
    public static final String JUSTIFY = "justify";
    public static final String FONT_FAMILY = "fontFamily";

    public static final String COLOR_TEXT = "colorText";
    public static final String COLOR_LINK = "colorLink";
    public static final String COLOR_RED = "colorRed";

    public static final String COLOR_BACKGROUND = "colorBackground";

    protected static final int POSITION_UNKNOWN = -1;

    static final int REQUEST_CODE_SELECT = 1001;
    static final int REQUEST_CODE_VERSION = 1002;
    private static final int FONT_SIZE_DEFAULT = FontsizeFragment.FONTSIZE_DEFAULT;

    private static final String FRAGMENT_ADD_NOTES = "add-notes";

    private static final String FRAGMENT_SHOW_NOTES = "show-notes";

    private static final String FRAGMENT_SHOW_ANNOTATIONS = "show-annotations";

    protected ViewPager mPager;
    private AppBarLayout mAppBar;

    protected ReadingAdapter mAdapter;

    private View mHeader;

    private Handler handler;

    private String mTextColorNormal;
    private String mTextColorLink;
    private String mTextColorRed;

    private int mColorBackground;
    private String mBackground;

    private String fontPath;

    private TextView versionView;

    private boolean mDark;

    private boolean mAutoCopy;

    private volatile boolean initialized;

    private Handler workHandler;

    private Handler mainHandler;

    private String mVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDark = ThemeUtils.isDark(this);
        resolveColors();
        setContentView(getContentLayout());

        handler = new ReadingHandler(this);
        workHandler = new WorkHandler(this);
        mainHandler = new MainHandler(this);

        mHeader = findHeader();
        mPager = findViewById(R.id.pager);
        mPager.addOnPageChangeListener(this);

        fontPath = BibleUtils.getFontPath(this);
        if (shouldRemove()) {
            mAdapter = new ReadingAdapter(getSupportFragmentManager(), 0);
        } else {
            mAdapter = new ReadingAdapter(getSupportFragmentManager(), retrieveOsisCount());
        }
        if (isFake()) {
            mPager.setVisibility(View.GONE);
            mHeader.setVisibility(View.GONE);
        } else {
            doShowProgress();
            initializeHeader(mHeader);
        }
    }

    protected abstract boolean shouldRemove();

    @Override
    public void onRestart() {
        super.onRestart();
        fontPath = BibleUtils.getFontPath(this);
        String oldFontPath = mAdapter.getData(getCurrentPosition()).getString(FONT_PATH);
        if (!ObjectUtils.equals(fontPath, oldFontPath)) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mAutoCopy = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autoCopy", false);
        if (ThemeUtils.isDark(this) != mDark) {
            recreate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!initialized) {
            workHandler.sendEmptyMessage(INITIALIZE);
        }
    }

    @Override
    public void onLoaded() {
        if (initialized) {
            hideProgress();
        }
    }

    @Override
    protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
        workHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    private void resolveColors() {
        mTextColorNormal = ColorUtils.rgba(ColorUtils.resolve(this, R.attr.textColorNormal));
        mTextColorLink = ColorUtils.rgba(ColorUtils.resolve(this, android.R.attr.textColorLink));
        mTextColorRed = ColorUtils.rgba(ColorUtils.resolve(this, R.attr.textColorRed));

        mColorBackground = ColorUtils.resolve(this, android.R.attr.colorBackground);
        mBackground = ColorUtils.rgba(mColorBackground);
    }

    protected View findHeader() {
        mAppBar = findViewById(R.id.appbar);
        if (mAppBar != null) {
            mAppBar.addOnOffsetChangedListener(this);
        }
        Toolbar toolbar = findViewById(getToolbarLayout());
        setSupportActionBar(toolbar);
        return toolbar;
    }

    public final int getCurrentPosition() {
        return mPager.getCurrentItem();
    }

    public final String getCurrentOsis() {
        return mAdapter.getData(getCurrentPosition()).getString(OSIS);
    }

    public final String getCurrentTitle() {
        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment == null) {
            return null;
        } else {
            return currentFragment.getTitle();
        }
    }

    public final ReadingFragment getCurrentFragment() {
        return mAdapter.getFragment(getCurrentPosition());
    }

    public final String getCurrentVersion() {
        ReadingFragment fragment = getCurrentFragment();
        if (fragment != null) {
            Bundle arguments = fragment.getArguments();
            if (arguments != null) {
                return arguments.getString(VERSION);
            }
        }
        return null;
    }

    protected final void prepare(int position) {
        LogUtils.d("prepare position: " + position);
        Bundle bundle = mAdapter.getData(position);
        String osis = bundle.getString(OSIS);
        if (TextUtils.isEmpty(osis)) {
            workHandler.removeMessages(PREPARE_POSITION);
            workHandler.obtainMessage(PREPARE_POSITION, position).sendToTarget();
        } else {
            if (bundle.containsKey(CONTENT)) {
                updateHeader(bundle, osis);
            }
            workHandler.removeMessages(PREPARE_DATA);
            workHandler.obtainMessage(PREPARE_DATA, bundle).sendToTarget();
        }
    }

    protected void prepareOnWork(int position) {
        // do nothing
    }

    protected void prepareOnWork(Bundle bundle) {
        int position = bundle.getInt(POSITION);
        String osis = bundle.getString(OSIS);
        LogUtils.d("prepare on work, position: " + position + ", osis: " + osis);
        preparePrev(position, bundle.getString(PREV));
        prepareNext(position, bundle.getString(NEXT));
        mainHandler.removeMessages(PREPARE);
        mainHandler.obtainMessage(PREPARE, bundle).sendToTarget();
    }

    protected void prepareOnMain(Bundle bundle) {
        int position = bundle.getInt(POSITION);
        String osis = bundle.getString(OSIS);
        LogUtils.d("prepare on main, position: " + position + ", osis: " + osis);
        updateHeader(bundle, osis);
        updateVersion();
        prepareOnMain(position);
        prepareOnMain(position - 1);
        prepareOnMain(position + 1);
    }

    protected void prepareOnMain(int position) {
        if (position >= 0 && position < mAdapter.getCount()) {
            Bundle bundle = mAdapter.getData(position);
            ReadingFragment fragment = mAdapter.getFragment(position);
            LogUtils.d("prepareOnMain, position: " + position
                    + ", title: " + (fragment == null ? null : fragment.getTitle())
                    + ", version: " + (fragment == null ? null : fragment.getVersion()));
            if (fragment != null) {
                if (BibleUtils.putAll(fragment.getArguments(), bundle)) {
                    fragment.reloadData();
                } else {
                    hideProgress();
                }
            }
        }
    }

    protected void updateTaskDescription(String label) {
        if (mAppBar != null) {
            mAppBar.setExpanded(true);
        }
        super.updateTaskDescription(label);
    }

    protected final void initializeOnWork() {
        BibleApplication application = (BibleApplication) getApplication();
        application.setDefaultVersion();
        if (shouldRemove()) {
            mainHandler.obtainMessage(INITIALIZE_COUNT, retrieveOsisCount()).sendToTarget();
        }
        mainHandler.obtainMessage(INITIALIZE_DATA, initializeData()).sendToTarget();
    }

    protected void initializeCount(int size) {
        LogUtils.d("initialize adapter size to " + size);
        mAdapter.setSize(size);
        mAdapter.notifyDataSetChanged();
    }

    protected int initializeData() {
        int position = getInitialPosition();
        String osis = getInitialOsis();
        return loadData(position, osis);
    }

    protected int loadData(int position, String osis) {
        Bundle bundle = retrieveOsis(position, osis);
        if (TextUtils.isEmpty(bundle.getString(CURR))) {
            bundle = retrieveOsis(position, "null");
        }
        if (position == POSITION_UNKNOWN) {
            position = bundle.getInt(ID) - 1;
        }
        LogUtils.d("size: " + mAdapter.getCount());
        mAdapter.setData(position, bundle);
        prepareOnWork(bundle);
        return position;
    }

    protected void initializeData(int position) {
        initialized = true;
        LogUtils.d("initialize position to " + position);
        updateVersion();
        mPager.setAdapter(mAdapter);
        mPager.setCurrentItem(position);
        checkDemoVersion();
    }

    public boolean isInitialized() {
        return initialized;
    }

    void showProgress() {
        mainHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, LATER);
    }

    void doShowProgress() {
        ProgressFragment fragment = (ProgressFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_PROGRESS);
        if (fragment == null) {
            LogUtils.d("show progress");
            fragment = new ProgressFragment();
        } else {
            LogUtils.d("show progress " + fragment);
        }
        fragment.show(getSupportFragmentManager(), FRAGMENT_PROGRESS);
    }

    void hideProgress() {
        mainHandler.removeMessages(SHOW_PROGRESS);
        mainHandler.obtainMessage(HIDE_PROGRESS).sendToTarget();
    }

    void doHideProgress() {
        ProgressFragment fragment = (ProgressFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_PROGRESS);
        if (fragment != null) {
            LogUtils.d("hide progress " + fragment);
            if (!isStopped()) {
                fragment.dismiss();
            }
        }
    }

    protected final void updateVersion() {
        if (versionView != null) {
            BibleApplication application = (BibleApplication) getApplication();
            String version = application.getVersion();
            if (!ObjectUtils.equals(mVersion, version)) {
                versionView.setText(application.getName(version));
            }
        }
    }

    protected final void initializeVersion(View header) {
        versionView = header.findViewById(R.id.version);
        View versionButton = header.findViewById(R.id.version_button);
        if (versionButton != null) {
            versionButton.setOnClickListener(this);
        }
    }

    protected abstract int getContentLayout();

    protected abstract int getToolbarLayout();

    protected abstract void initializeHeader(View header);

    protected abstract void updateHeader(Bundle bundle, String osis);

    protected abstract int retrieveOsisCount();

    protected abstract String getInitialOsis();

    protected abstract int getInitialPosition();

    @Override
    public Bundle retrieveOsis(int position, String osis) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            LogUtils.w("retrieve osis in main looper!");
        }
        Bundle bundle = new Bundle();
        bundle.putString(OSIS, osis);
        if (isFake()) {
            return bundle;
        }
        BibleApplication application = (BibleApplication) getApplication();
        Uri uri = VersionProvider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                bundle.putInt(ID, cursor.getInt(cursor.getColumnIndex(BaseColumns._ID)));
                String curr = getString(cursor, VersionProvider.COLUMN_OSIS);
                bundle.putString(CURR, curr);
                bundle.putString(NEXT, getString(cursor, VersionProvider.COLUMN_NEXT));
                bundle.putString(PREV, getString(cursor, VersionProvider.COLUMN_PREVIOUS));
                bundle.putString(HUMAN, application.getHuman(BibleUtils.getBook(curr)));
                bundle.putByteArray(CONTENT, FileUtils.compress(getString(cursor, VersionProvider.COLUMN_CONTENT)));
                bundle.putString(OSIS, curr);

                bundle.putString(HIGHLIGHTED, application.getHighlight(curr));
                bundle.putBundle(NOTES, application.getNoteVerses(curr));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot query " + osis, e);
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String version = application.getVersion();
        int fontSize = getFontsize(sharedPreferences, version);
        bundle.putString(VERSION, version);
        bundle.putInt(FONT_SIZE, fontSize);
        bundle.putBoolean(CROSS, sharedPreferences.getBoolean(CROSS, false));
        bundle.putBoolean(SHANGTI, sharedPreferences.getBoolean(SHANGTI, false));
        bundle.putBoolean(RED, sharedPreferences.getBoolean(RED, true));
        bundle.putBoolean(JUSTIFY, sharedPreferences.getBoolean(JUSTIFY, false));
        bundle.putString(FONT_FAMILY, sharedPreferences.getString(FONT_FAMILY, DEFAULT_FONT_FAMILY));
        updateBundle(bundle);
        return bundle;
    }

    public boolean isChanged(Bundle bundle) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        BibleApplication application = (BibleApplication) getApplication();
        String version = application.getVersion();
        int fontSize = getFontsize(sharedPreferences, version);
        return !ObjectUtils.equals(bundle.getString(VERSION), version)
                || !ObjectUtils.equals(bundle.getInt(FONT_SIZE), fontSize)
                || !ObjectUtils.equals(bundle.getBoolean(CROSS), sharedPreferences.getBoolean(CROSS, false))
                || !ObjectUtils.equals(bundle.getBoolean(SHANGTI), sharedPreferences.getBoolean(SHANGTI, false))
                || !ObjectUtils.equals(bundle.getBoolean(RED), sharedPreferences.getBoolean(RED, true))
                || !ObjectUtils.equals(bundle.getBoolean(JUSTIFY), sharedPreferences.getBoolean(JUSTIFY, false))
                || !ObjectUtils.equals(bundle.getString(FONT_FAMILY),
                sharedPreferences.getString(FONT_FAMILY, DEFAULT_FONT_FAMILY));
    }

    private int getFontsize(SharedPreferences sharedPreferences, String version) {
        int defaultFontsize = sharedPreferences.getInt(FONT_SIZE + "-default", FONT_SIZE_DEFAULT);
        return sharedPreferences.getInt(FONT_SIZE + "-" + BibleUtils.removeDemo(version), defaultFontsize);
    }

    private String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndexOrThrow(columnName));
    }

    protected void prepareNext(int position, String osis) {
        prepareNext(position, osis, mAdapter.getCount());
    }

    protected void prepareNext(int position, String osis, int count) {
        int nextPosition = position + 1;
        if (nextPosition < count) {
            prepare(nextPosition, osis);
        }
    }

    protected void preparePrev(int position, String osis) {
        int prevPosition = position - 1;
        if (prevPosition >= 0) {
            prepare(prevPosition, osis);
        }
    }

    protected void prepare(int position, String osis) {
        Bundle bundle = mAdapter.getData(position);
        LogUtils.d("prepare position: " + position + ", osis: " + osis);
        if (!ObjectUtils.equals(osis, bundle.getString(CURR))) {
            bundle.putString(OSIS, osis);
            bundle.putAll(retrieveOsis(position, osis));
        }
    }

    @Override
    public void onPageScrollStateChanged(int position) {
        // do nothing
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // do nothing
    }

    @Override
    public void onPageSelected(int position) {
        prepare(position);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reading, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_theme:
                ThemeUtils.toggle(this);
                recreate();
                return true;
            case R.id.search:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", annotation: " + annotation);
        handler.obtainMessage(ReadingHandler.SHOW_ANNOTATION,
                new ReadingHandler.Annotation(link, annotation, getCurrentOsis())).sendToTarget();
    }

    @Override
    public void showNote(String verse) {
        LogUtils.d("show note, verse: " + verse);
        handler.obtainMessage(ReadingHandler.SHOW_NOTE, verse).sendToTarget();
    }

    @Override
    public void updateBundle(Bundle bundle) {
        bundle.putString(COLOR_TEXT, mTextColorNormal);
        bundle.putString(COLOR_LINK, mTextColorLink);
        bundle.putString(COLOR_RED, mTextColorRed);

        bundle.putString(COLOR_BACKGROUND, mBackground);
        if (!TextUtils.isEmpty(fontPath)) {
            bundle.putString(FONT_PATH, fontPath);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.version_button) {
            selectVersion();
        }
    }

    private void selectVersion() {
        Intent intent = new Intent(this, SelectVersionActivity.class);
        startActivityForResult(intent, REQUEST_CODE_VERSION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VERSION && data != null) {
            String version = data.getStringExtra(VERSION);
            if (!ObjectUtils.equals(version, getCurrentVersion())) {
                switchToVersion(version);
            }
        }
    }

    protected void switchToVersion(String version) {
        workHandler.obtainMessage(SET_VERSION, version).sendToTarget();
    }

    protected void checkChapter(String version) {
        LogUtils.d("check chapter in " + version);
        workHandler.obtainMessage(CHECK_CHAPTER, version).sendToTarget();
    }

    protected void refreshAdapter() {
        LogUtils.d("refresh adapter");
        workHandler.obtainMessage(REFRESH_ADAPTER).sendToTarget();
    }

    protected void refreshAdapterOnWork() {
        int count = retrieveOsisCount();
        int position = getCurrentPosition();
        String osis = getCurrentOsis();
        Bundle bundle = retrieveOsis(position, osis);
        if (TextUtils.isEmpty(bundle.getString(CURR))) {
            bundle = retrieveOsis(position, "null");
        }
        position = bundle.getInt(ID) - 1;
        mAdapter.clearData();
        mAdapter.setData(position, bundle);
        preparePrev(position, bundle.getString(PREV));
        prepareNext(position, bundle.getString(NEXT), count);

        LogUtils.d("refreshAdapterOnWork, position: " + position + ", count: " + count);
        afterRefreshAdapterOnWork(position, count);
    }

    protected void afterRefreshAdapterOnWork(int position, int count) {
        mainHandler.obtainMessage(REFRESH_ADAPTER, position, count).sendToTarget();
    }

    protected void refreshAdapterOnMain(int position, int count) {
        LogUtils.d("refreshAdapterOnMain, position: " + position + ", count: " + count);
        Bundle bundle = mAdapter.getData(position);
        String osis = bundle.getString(OSIS);
        updateHeader(bundle, osis);
        updateVersion();
        mAdapter.setSize(count);
        mAdapter.notifyDataSetChanged();
        mPager.setCurrentItem(position);
        prepareOnMain(position);
        if (mainHandler.hasMessages(SHOW_PROGRESS)) {
            hideProgress();
        }
    }

    protected void refresh() {
        workHandler.obtainMessage(REFRESH).sendToTarget();
    }

    protected void refreshOnWork() {
        int position = getCurrentPosition();
        String osis = getCurrentOsis();
        mAdapter.clearData();
        mainHandler.obtainMessage(REFRESH, loadData(position, osis)).sendToTarget();
    }

    protected void refreshOnMain(int position) {
        mPager.setCurrentItem(position);
        hideProgress();
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBar, int offset) {
        View decorView = getWindow().getDecorView();
        // http://stackoverflow.com/questions/31872653
        if (appBar.getHeight() + offset == 0) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            decorView.setSystemUiVisibility(0);
        }
    }

    public void onSelected(boolean highlight, String verses, String content) {
        handler.obtainMessage(ReadingHandler.SHOW_SELECTION,
                new ReadingHandler.Selection(highlight, verses, content)).sendToTarget();
    }

    @Override
    public void saveHighlight(String verses) {
        String osis = getCurrentOsis();
        LogUtils.d("osis: " + osis + ", highlight: " + verses);
        BibleApplication application = (BibleApplication) getApplication();
        application.saveHighlight(osis, verses);
        handler.obtainMessage(ReadingHandler.SHOW_SELECTION,
                new ReadingHandler.Selection(false, null, null)).sendToTarget();
    }

    public void setHighlight(String verses, boolean added) {
        getCurrentFragment().setHighlight(verses, added);
    }

    public void setNote(String verse, boolean added) {
        getCurrentFragment().setNote(verse, added);
    }

    public void addNotes(String verses) {
        handler.obtainMessage(ReadingHandler.ADD_NOTES, verses).sendToTarget();
    }

    public void share(ReadingHandler.Selection selection) {
        handler.obtainMessage(ReadingHandler.SHARE, selection).sendToTarget();
    }

    public void doAddNotes(String verses) {
        String osis = getCurrentOsis();
        LogUtils.d("osis: " + osis + ", update notes: " + verses);
        String verse = BibleUtils.getVerse(verses);
        Bundle note = fetchNote(verse);

        final String tag = FRAGMENT_ADD_NOTES;
        FragmentManager manager = getSupportFragmentManager();
        AddNotesFragment fragment = (AddNotesFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new AddNotesFragment();
        fragment.setNote(verses, note);
        fragment.show(manager, tag);
    }

    public void saveNotes(long id, String verses, String content) {
        String osis = getCurrentOsis();
        String verse = BibleUtils.getVerse(verses);
        BibleApplication application = (BibleApplication) getApplication();
        application.saveNote(id, osis, verse, verses, content);
        getCurrentFragment().getArguments().putBundle(NOTES, application.getNoteVerses(osis));
        setNote(verse, !TextUtils.isEmpty(content));
        getCurrentFragment().selectVerses(verses, false, false);
    }

    public void deleteNote(long id, String verses) {
        String osis = getCurrentOsis();
        String verse = BibleUtils.getVerse(verses);
        BibleApplication application = (BibleApplication) getApplication();
        application.deleteNote(id);
        getCurrentFragment().getArguments().putBundle(NOTES, application.getNoteVerses(osis));
        setNote(verse, false);
    }

    public void doShowNote(String verse) {
        Bundle note = fetchNote(verse);
        if (note == null) {
            return;
        }

        final String tag = FRAGMENT_SHOW_NOTES;
        FragmentManager manager = getSupportFragmentManager();
        ShowNotesFragment fragment = (ShowNotesFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new ShowNotesFragment();
        fragment.setNote(note);
        fragment.show(manager, tag);
    }

    public Bundle fetchNote(String verse) {
        long id = getCurrentFragment().getNote(verse);
        if (id > 0) {
            BibleApplication application = (BibleApplication) getApplication();
            return application.getNote(id);
        } else {
            return null;
        }
    }

    public void doShowAnnotation(ReadingHandler.Annotation annotation) {
        if ("search".equals(annotation.getLink())) {
            search(annotation.getMessage());
            return;
        }
        String message = annotation.getMessage();
        if (TextUtils.isEmpty(message)) {
            BibleApplication application = (BibleApplication) getApplication();
            message = application.getAnnotation(annotation.getOsis(), annotation.getLink());
        }
        if (message != null) {
            doShowAnnotation(annotation.getLink(), message);
        }
    }

    protected void search(String message) {
        BibleApplication application = (BibleApplication) getApplication();
        ArrayList<OsisItem> osisItems = OsisItem.parseSearch(message, application, getCurrentOsis());
        LogUtils.d("search: " + message + ", items: " + osisItems);
        if (!osisItems.isEmpty()) {
            Intent intent = new Intent(this, ReadingCrossActivity.class);
            intent.putParcelableArrayListExtra(ReadingItemsActivity.ITEMS, osisItems);
            startActivity(setFinished(intent, false));
        }
    }

    private void doShowAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", content: " + annotation);

        final String tag = FRAGMENT_SHOW_ANNOTATIONS;
        FragmentManager manager = getSupportFragmentManager();
        ShowAnnotationFragment fragment = (ShowAnnotationFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new ShowAnnotationFragment();
        fragment.setAnnotation(link, annotation);
        fragment.show(manager, tag);
    }

    public void doShowSelection(ReadingHandler.Selection selection) {
        if (selection == null || TextUtils.isEmpty(selection.getVerses())) {
            if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
            }
        } else {
            if (actionMode != null) {
                actionMode.setTag(selection);
                actionMode.invalidate();
            } else {
                ActionMode.Callback callback = new ReadingHandler.SelectionActionMode(this,
                        selection);
                actionMode = startSupportActionMode(callback);
            }
        }
        if (mAutoCopy) {
            String text = getText(selection);
            if (!TextUtils.isEmpty(text)) {
                copy(text);
            }
        }
    }

    private void copy(String text) {
        try {
            //noinspection deprecation
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setText(text);
            }
        } catch (RuntimeException ignore) {
            // do nothing
        }
    }

    private String getText(ReadingHandler.Selection selection) {
        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment == null || TextUtils.isEmpty(selection.getVerses())) {
            return null;
        }
        return getVersionName() + " " + currentFragment.getTitle() + ":"
                + selection.getVerses() + "\n"
                + selection.getContent();
    }

    private String getVersionName() {
        BibleApplication application = (BibleApplication) getApplication();
        String version = application.getVersion();
        String name = application.getFullname(version);
        if (BibleUtils.isCJK(name)) {
            return name;
        } else {
            return application.getName(version);
        }
    }

    public void doShare(ReadingHandler.Selection selection) {
        String text = getText(selection);
        if (!TextUtils.isEmpty(text)) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.setType("text/plain");
            ChooserUtils.startActivityExcludeSelf(this, intent, getString(R.string.action_share_title));
        }
    }

    public int getBackgroundColor() {
        return mColorBackground;
    }

    @Override
    public void onSupportActionModeFinished(@NonNull ActionMode actionMode) {
        if (this.actionMode == actionMode) {
            this.actionMode = null;
        }
        super.onSupportActionModeFinished(actionMode);
    }

    protected void saveOsis() {
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

    private void checkDemoVersion() {
        workHandler.obtainMessage(CHECK_DEMO).sendToTarget();
    }

    void doCheckDemoVersion() {
        BibleApplication application = (BibleApplication) getApplication();
        String databaseVersion = application.getVersion();
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
    public void onBackPressed() {
        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment != null && !TextUtils.isEmpty(currentFragment.getSelectedVerses())) {
            currentFragment.selectVerses(currentFragment.getSelectedVerses(), false, true);
        } else {
            super.onBackPressed();
        }
    }

    protected void onNoChapter(String version) {
        // do nothing
    }

    protected void jump(String osis, String verse) {
        workHandler.obtainMessage(JUMP, new String[] {osis, verse}).sendToTarget();
    }

    protected void jumpOnWork(String osis, String verse) {
        Bundle bundle = retrieveOsis(POSITION_UNKNOWN, osis);
        int verseInt = NumberUtils.parseInt(verse);
        if (verseInt > 0) {
            bundle.putString(VERSE, verse);
            bundle.putString(VERSE_START, verse);
        }
        int position = bundle.getInt(ID) - 1;
        mAdapter.setData(position, bundle);
        preparePrev(position, bundle.getString(PREV));
        prepareNext(position, bundle.getString(NEXT));
        mainHandler.obtainMessage(JUMP, bundle).sendToTarget();
    }

    protected void jumpOnMain(Bundle bundle) {
        int position = bundle.getInt(POSITION);
        String osis = bundle.getString(OSIS);
        updateHeader(bundle, osis);
        updateVersion();
        mPager.setCurrentItem(position);
    }

    private static class MainHandler extends Handler {

        private final WeakReference<AbstractReadingActivity> mReference;

        public MainHandler(AbstractReadingActivity activity) {
            super(activity.getMainLooper());
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            AbstractReadingActivity activity = mReference.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {
                case INITIALIZE_COUNT:
                    activity.initializeCount((Integer) msg.obj);
                    break;
                case INITIALIZE_DATA:
                    activity.initializeData((Integer) msg.obj);
                    break;
                case PREPARE:
                    activity.prepareOnMain((Bundle) msg.obj);
                    break;
                case REFRESH_ADAPTER:
                    activity.refreshAdapterOnMain(msg.arg1, msg.arg2);
                    break;
                case JUMP:
                    activity.jumpOnMain((Bundle) msg.obj);
                    break;
                case SHOW_PROGRESS:
                    if (!hasMessages(HIDE_PROGRESS)) {
                        activity.doShowProgress();
                    }
                    removeMessages(SHOW_PROGRESS);
                    break;
                case HIDE_PROGRESS:
                    activity.doHideProgress();
                    removeMessages(HIDE_PROGRESS);
                    removeMessages(SHOW_PROGRESS);
                    break;
                case REFRESH:
                    activity.refreshOnMain((Integer) msg.obj);
                    break;
                default:
                    break;
            }
        }

    }

    private static class WorkHandler extends Handler {

        private final WeakReference<AbstractReadingActivity> mReference;

        public WorkHandler(AbstractReadingActivity activity) {
            super(newLooper());
            this.mReference = new WeakReference<>(activity);
        }

        private static Looper newLooper() {
            HandlerThread thread = new HandlerThread("Reading");
            thread.start();
            return thread.getLooper();
        }

        @Override
        public void handleMessage(Message msg) {
            AbstractReadingActivity activity = mReference.get();
            if (activity == null) {
                return;
            }
            BibleApplication application = (BibleApplication) activity.getApplication();
            switch (msg.what) {
                case INITIALIZE:
                    activity.initializeOnWork();
                    break;
                case PREPARE_DATA:
                    activity.prepareOnWork((Bundle) msg.obj);
                    break;
                case REFRESH:
                    activity.showProgress();
                    activity.refreshOnWork();
                    break;
                case PREPARE_POSITION:
                    activity.prepareOnWork((Integer) msg.obj);
                    break;
                case CHECK_CHAPTER:
                    activity.showProgress();
                    if (!application.hasChapter((String) msg.obj, activity.getCurrentOsis())) {
                        activity.hideProgress();
                        activity.onNoChapter((String) msg.obj);
                        break;
                    }
                    // fall through
                case SET_VERSION:
                    if (msg.what == SET_VERSION) {
                        activity.showProgress();
                    }
                    application.setVersion((String) msg.obj);
                    // fall through
                case REFRESH_ADAPTER:
                    if (msg.what == REFRESH_ADAPTER) {
                        activity.showProgress();
                    }
                    activity.refreshAdapterOnWork();
                    removeMessages(REFRESH_ADAPTER);
                    break;
                case JUMP:
                    activity.showProgress();
                    String[] osisVerse = (String[]) msg.obj;
                    activity.jumpOnWork(osisVerse[0], osisVerse[1]);
                    break;
                case CHECK_DEMO:
                    activity.doCheckDemoVersion();
                    break;
            }
        }
    }

}
