package me.piebridge.bible.activity;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.design.widget.AppBarLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import me.piebridge.bible.Bible;
import me.piebridge.bible.Provider;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.ReadingAdapter;
import me.piebridge.bible.bridge.ReadingBridge;
import me.piebridge.bible.bridge.ReadingHandler;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ColorUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ObjectUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 15/10/18.
 */
public abstract class AbstractReadingActivity extends DrawerActivity implements ReadingBridge.Bridge, View.OnClickListener, ViewPager.OnPageChangeListener, AppBarLayout.OnOffsetChangedListener {

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
    public static final String FONT_SIZE = "fontSize";
    public static final String FONT_PATH = "fontPath";
    public static final String CROSS = "cross";
    public static final String SHANGTI = "shangti";
    public static final String VERSION = "version";
    public static final String SEARCH = "search";
    public static final String HIGHLIGHTED = "highlighted";
    public static final String RED = "red";
    public static final String COLOR_BACKGROUND = "colorBackground";
    public static final String COLOR_TEXT = "colorText";
    public static final String COLOR_LINK = "colorLink";
    public static final String COLOR_RED = "colorRed";
    public static final String COLOR_SELECTED = "colorSelected";
    public static final String COLOR_HIGHLIGHT = "colorHighlight";
    public static final String COLOR_HIGHLIGHT_SELECTED = "colorHighlightSelected";

    protected static final int POSITION_UNKNOWN = -1;

    private static final int REQUEST_CODE_SELECT = 1001;
    private static final int REQUEST_CODE_VERSION = 1002;
    private static final int FONT_SIZE_DEFAULT = 14;

    protected ViewPager mPager;
    private AppBarLayout mAppBar;

    protected ReadingAdapter mAdapter;

    private View mHeader;

    private Handler handler = new ReadingHandler(this);

    private String colorBackground;
    private String colorText;
    private String colorLink;
    private String colorRed;
    private String colorSelected;
    private String colorHighlight;
    private String colorHighLightSelected;
    private String fontPath;

    protected Bible bible;
    private TextView versionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme();
        resolveColors();
        super.onCreate(savedInstanceState);
        setContentView(getContentLayout());

        mHeader = findHeader();
        mPager = (ViewPager) findViewById(R.id.pager);
        bible = Bible.getInstance(this);
        fontPath = BibleUtils.getFontPath(this);
        mAdapter = new ReadingAdapter(getFragmentManager(), retrieveOsisCount());
        initialize();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        fontPath = BibleUtils.getFontPath(this);
        if (!ObjectUtils.isIdentical(fontPath, mAdapter.getData(getCurrentPosition()).getString(FONT_PATH))) {
            refresh();
        }
    }

    private void resolveColors() {
        colorBackground = ColorUtils.rgba(ColorUtils.resolveColor(this, android.R.attr.colorBackground));
        colorLink = ColorUtils.rgba(ColorUtils.resolveColor(this, android.R.attr.textColorLink));
        int textColorHighlight = ColorUtils.resolveColor(this, android.R.attr.textColorHighlight);
        int textColorPrimary = ColorUtils.resolveColor(this, android.R.attr.textColorPrimary);
        int red = ColorUtils.replaceAlpha(ColorUtils.resolveColor(this, R.attr.colorRed), textColorPrimary);
        int highlight = ColorUtils.replaceAlpha(ColorUtils.resolveColor(this, R.attr.colorYellow), textColorHighlight);
        colorText = ColorUtils.rgba(ColorUtils.fixOpacity(textColorPrimary));
        colorSelected = ColorUtils.rgba(textColorHighlight);
        colorRed = ColorUtils.rgba(red);
        colorHighlight = ColorUtils.rgba(highlight);
        colorHighLightSelected = ColorUtils.blend(highlight, textColorHighlight);
    }

    protected View findHeader() {
        mAppBar = (AppBarLayout) findViewById(R.id.appbar);
        if (mAppBar != null) {
            mAppBar.addOnOffsetChangedListener(this);
        }
        Toolbar toolbar = (Toolbar) findViewById(getToolbarLayout());
        setSupportActionBar(toolbar);
        return toolbar;
    }

    protected final int getCurrentPosition() {
        return mPager.getCurrentItem();
    }

    protected final String getCurrentOsis() {
        return mAdapter.getData(getCurrentPosition()).getString(OSIS);
    }

    protected final void prepare(int position) {
        Bundle bundle = mAdapter.getData(position);
        String osis = bundle.getString(OSIS);
        if (!TextUtils.isEmpty(osis)) {
            updateHeader(bundle, osis);
            prepareNext(position, bundle.getString(NEXT));
            preparePrev(position, bundle.getString(PREV));
        }
    }

    protected void setTheme() {
        ThemeUtils.setTheme(this);
    }

    protected void updateTaskDescription(String label) {
        if (mAppBar != null) {
            mAppBar.setExpanded(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(label));
        }
    }

    protected final void initialize() {
        initializeHeader(mHeader);

        mPager.setAdapter(mAdapter);
        mPager.addOnPageChangeListener(this);

        int position = getInitialPosition();
        String osis = getInitialOsis();
        Bundle bundle = retrieveOsis(position, osis);
        if (position == POSITION_UNKNOWN) {
            position = bundle.getInt(ID) - 1;
        }
        mAdapter.setData(position, bundle);
        updateVersion();
        prepare(position);

        mPager.setCurrentItem(position);
    }

    protected final void updateVersion() {
        if (versionView != null) {
            versionView.setText(bible.getVersionName(bible.getVersion()));
        }
    }

    protected final void initializeVersion(View header) {
        versionView = (TextView) header.findViewById(R.id.version);
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
        Bundle bundle = new Bundle();
        bundle.putString(OSIS, osis);
        Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                bundle.putInt(ID, cursor.getInt(cursor.getColumnIndex(BaseColumns._ID)));
                String curr = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
                bundle.putString(CURR, curr);
                bundle.putString(NEXT, cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_NEXT)));
                bundle.putString(PREV, cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_PREVIOUS)));
                bundle.putString(HUMAN, cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN)));
                bundle.putString(CONTENT, cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CONTENT)));
                bundle.putString(OSIS, curr);
                bundle.putString(HIGHLIGHTED, bible.getHighlight(curr));
                bundle.putStringArray(NOTES, bible.getNoteVerses(curr));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot query " + osis, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String version = bible.getVersion();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        bundle.putInt(FONT_SIZE, sp.getInt(FONT_SIZE + "-" + version, FONT_SIZE_DEFAULT));
        bundle.putBoolean(CROSS, sp.getBoolean(CROSS, false));
        bundle.putBoolean(SHANGTI, sp.getBoolean(SHANGTI, false));
        bundle.putString(VERSION, bible.getVersion());
        bundle.putBoolean(RED, sp.getBoolean(RED, true));
        updateBundle(bundle);
        return bundle;
    }

    private void prepareNext(int position, String osis) {
        int nextPosition = position + 1;
        if (nextPosition < mAdapter.getCount()) {
            prepare(nextPosition, osis);
        }
    }

    private void preparePrev(int position, String osis) {
        int prevPosition = position - 1;
        if (prevPosition >= 0) {
            prepare(prevPosition, osis);
        }
    }

    private void prepare(int position, String osis) {
        Bundle bundle = mAdapter.getData(position);
        bundle.putString(OSIS, osis);
        bundle.putAll(retrieveOsis(position, osis));
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
        // FIXME
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", annotation: " + annotation);
        handler.sendMessage(handler.obtainMessage(ReadingHandler.SHOW_ANNOTATION, new ReadingHandler.Annotation(link, annotation, getCurrentOsis())));
    }

    @Override
    public void showNote(String verse) {
        LogUtils.d("show note, verse: " + verse);
        handler.sendMessage(handler.obtainMessage(ReadingHandler.SHOW_NOTE, new ReadingHandler.Note(verse, getCurrentOsis())));
    }

    @Override
    public void updateBundle(Bundle bundle) {
        bundle.putString(COLOR_BACKGROUND, colorBackground);
        bundle.putString(COLOR_TEXT, colorText);
        bundle.putString(COLOR_LINK, colorLink);
        bundle.putString(COLOR_RED, colorRed);
        bundle.putString(COLOR_HIGHLIGHT, colorHighlight);
        bundle.putString(COLOR_SELECTED, colorSelected);
        bundle.putString(COLOR_HIGHLIGHT_SELECTED, colorHighLightSelected);
        if (!TextUtils.isEmpty(fontPath)) {
            bundle.putString(FONT_PATH, fontPath);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.book_button) {
            select(SelectActivity.BOOK);
        } else if (id == R.id.chapter_button) {
            select(SelectActivity.CHAPTER);
        } else if (id == R.id.version_button) {
            selectVersion();
        }
    }

    private void selectVersion() {
        Intent intent = new Intent(this, SelectVersionActivity.class);
        startActivityForResult(intent, REQUEST_CODE_VERSION);
    }

    private void select(int position) {
        Intent intent = new Intent(this, SelectActivity.class);
        intent.putExtra(SelectActivity.POSITION, position);
        intent.putExtra(OSIS, getCurrentOsis());
        startActivityForResult(intent, REQUEST_CODE_SELECT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT && data != null) {
            jump(data.getStringExtra(OSIS), data.getStringExtra(VERSE));
        } else if (requestCode == REQUEST_CODE_VERSION && data != null) {
            bible.setVersion(data.getStringExtra(VERSION));
            refresh();
        }
    }

    private void refresh() {
        int position = getCurrentPosition();
        String osis = getCurrentOsis();
        mAdapter.setData(position, retrieveOsis(position, osis));
        updateVersion();
        prepare(position);
        reload(position);
    }

    private void reload(int position) {
        reloadData(position);
        if (position > 0) {
            reloadData(position - 1);
        }
        if (position < mAdapter.getCount() - 1) {
            reloadData(position + 1);
        }
    }

    private void reloadData(int position, int verse) {
        ReadingFragment fragment = (ReadingFragment) mAdapter.instantiateItem(mPager, position);
        if (verse > 0) {
            fragment.setForceVerse(verse);
        }
        Bundle bundle = fragment.getArguments();
        if (bundle != null) {
            bundle.putAll(mAdapter.getData(position));
            fragment.reloadData();
        }
    }

    private void reloadData(int position) {
        reloadData(position, 0);
    }

    private void jump(String osis, String verse) {
        Bundle bundle = retrieveOsis(POSITION_UNKNOWN, osis);
        bundle.putString(VERSE, verse);
        bundle.putString(VERSE_START, verse);
        int oldPosition = getCurrentPosition();
        int position = bundle.getInt(ID) - 1;
        mAdapter.setData(position, bundle);
        prepare(position);

        mPager.setCurrentItem(position);

        // if it's cached, then reloaded
        if (Math.abs(oldPosition - position) <= mPager.getOffscreenPageLimit()) {
            reloadData(position, NumberUtils.parseInt(verse));
        }
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

}
