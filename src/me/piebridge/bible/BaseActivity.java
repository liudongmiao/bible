package me.piebridge.bible;

import android.app.ActionBar;
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
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ColorUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 15/10/18.
 */
public abstract class BaseActivity extends FragmentActivity implements ReadingBridge.Bridge, View.OnClickListener, ViewPager.OnPageChangeListener {

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

    protected ViewPager mPager;

    protected ReadingAdapter mAdapter;

    private View mHeader;

    private Handler handler = new ReadingHandler(this);

    private int background;

    private String colorBackground;
    private String colorText;
    private String colorLink;
    private String colorRed;
    private String colorSelected;
    private String colorHighlight;
    private String colorHighLightSelected;
    private String fontPath;

    // https://material.google.com/style/color.html
    private static final int RED_200 = 0xef9a9a;
    private static final int RED_500 = 0xf44336;

    // yellow
    private static final int YELLOW_200 = 0xfff59d;
    private static final int YELLOW_500 = 0xffeb3b;

    protected Bible bible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        updateTheme();
        resolveColors();
        super.onCreate(savedInstanceState);
        setContentView(getContentLayout());

        mHeader = findHeader();
        mPager = (ViewPager) findViewById(R.id.pager);
        bible = Bible.getInstance(this);
        fontPath = BibleUtils.getFontPath(this);
        mAdapter = new ReadingAdapter(getSupportFragmentManager(), retrieveOsisCount());
        initialize();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        fontPath = BibleUtils.getFontPath(this);
        if (!BibleUtils.equals(fontPath, mAdapter.getData(getCurrentPosition()).getString(FONT_PATH))) {
            refresh();
        }
    }

    private void resolveColors() {
        background = resolveColor(android.R.attr.colorBackground);
        colorBackground = ColorUtils.rgba(background);
        colorLink = ColorUtils.rgba(resolveColor(android.R.attr.textColorLink));
        int textColorHighlight = resolveColor(android.R.attr.textColorHighlight);
        int textColorPrimary = resolveColor(android.R.attr.textColorPrimary);
        int red;
        int highlight;
        if (ThemeUtils.isDark(this)) {
            red = ColorUtils.replaceAlpha(RED_200, textColorPrimary);
            highlight = ColorUtils.replaceAlpha(YELLOW_200, textColorHighlight);
        } else {
            red = ColorUtils.replaceAlpha(RED_500, textColorPrimary);
            highlight = ColorUtils.replaceAlpha(YELLOW_500, textColorHighlight);
        }
        colorText = ColorUtils.rgba(textColorPrimary);
        colorSelected = ColorUtils.rgba(textColorHighlight);
        colorRed = ColorUtils.rgba(red);
        colorHighlight = ColorUtils.rgba(highlight);
        colorHighLightSelected = ColorUtils.blend(highlight, textColorHighlight);
    }

    public int getBackground() {
        return background;
    }

    protected View findHeader() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return findViewById(R.id.header);
        } else {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
                actionBar.setCustomView(R.layout.header);
                return actionBar.getCustomView();
            } else {
                return findViewById(R.id.header);
            }
        }
    }

    protected void updateHeader(Bundle bundle, String osis, View header) {
        String book = BibleUtils.getBook(osis);
        int osisPosition = bible.getPosition(Bible.TYPE.OSIS, book);
        String bookName = bible.get(Bible.TYPE.BOOK, osisPosition);
        TextView bookView = (TextView) header.findViewById(R.id.book);
        bookView.setVisibility(View.VISIBLE);
        bookView.setText(bookName);

        TextView chapterView = (TextView) header.findViewById(R.id.chapter);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        chapterView.setText(chapterVerse);
        chapterView.setVisibility(View.VISIBLE);

        TextView versionView = (TextView) header.findViewById(R.id.version);
        versionView.setText(bible.getVersionName(bible.getVersion()));

        header.findViewById(R.id.reading).setVisibility(View.VISIBLE);

        updateTaskDescription(BibleUtils.getBookChapterVerse(bookName, chapterVerse));
    }

    protected void updateTaskDescription(String label) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(label));
        }
    }

    public void initialize() {
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
        prepare(position);

        mPager.setCurrentItem(position);
    }

    protected void initializeHeader(View header) {
        header.findViewById(R.id.book).setOnClickListener(this);
        header.findViewById(R.id.chapter).setOnClickListener(this);
        header.findViewById(R.id.version).setOnClickListener(this);
    }

    protected int getContentLayout() {
        return R.layout.reading;
    }

    protected void updateTheme() {
        ThemeUtils.setTheme(this);
    }

    protected abstract int retrieveOsisCount();

    protected abstract String getInitialOsis();

    protected abstract int getInitialPosition();

    protected int getCurrentPosition() {
        return mPager.getCurrentItem();
    }

    protected String getCurrentOsis() {
        return mAdapter.getData(getCurrentPosition()).getString(OSIS);
    }

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
        bundle.putInt(FONT_SIZE, sp.getInt(Settings.FONTSIZE + "-" + version, 0xf));
        bundle.putBoolean(CROSS, sp.getBoolean(Settings.XLINK, false));
        bundle.putBoolean(SHANGTI, sp.getBoolean(Settings.SHANGTI, false));
        bundle.putString(VERSION, bible.getVersion());
        bundle.putBoolean(RED, sp.getBoolean(Settings.RED, true));
        // for color
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
        return bundle;
    }

    private void prepare(int position) {
        Bundle bundle = mAdapter.getData(position);
        String osis = bundle.getString(OSIS);
        if (!TextUtils.isEmpty(osis)) {
            updateHeader(bundle, osis, mHeader);
            prepareNext(position, bundle.getString(NEXT));
            preparePrev(position, bundle.getString(PREV));
        }
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

    @SuppressWarnings("deprecation")
    private int resolveColor(int resId) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(resId, tv, true);
            int colorId = tv.resourceId;
            return getResources().getColor(colorId);
        } else {
            return getDefaultColor(resId);
        }
    }

    private int getDefaultColor(int resId) {
        // for gingerbread
        switch (resId) {
            case android.R.attr.colorBackground:
                return 0xffffffff;
            case android.R.attr.textColorLink:
                return 0xff5c5cff;
            case android.R.attr.textColorHighlight:
                return 0x66ff9200;
            case android.R.attr.textColorPrimary:
                return 0xde000000;
            default:
                return 0;
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
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(COLOR_BACKGROUND, background);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey(COLOR_BACKGROUND) && savedInstanceState.getInt(COLOR_BACKGROUND) != background) {
            reload(getCurrentPosition());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            menu.add(Menu.NONE, R.string.switch_theme, Menu.NONE, R.string.switch_theme);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.string.switch_theme) {
            return switchTheme();
        }
        return true;
    }

    protected boolean switchTheme() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            ThemeUtils.switchTheme(this);
            recreate();
        }
        return true;
    }

    @Override
    public void showAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", annotation: " + annotation);
        handler.sendMessage(handler.obtainMessage(ReadingHandler.SHOW_ANNOTATION, new String[] {link, annotation, getCurrentOsis()}));
    }

    @Override
    public void showNote(String verse) {
        LogUtils.d("show note, verse: " + verse);
        handler.sendMessage(handler.obtainMessage(ReadingHandler.SHOW_NOTE, new String[] {verse, getCurrentOsis()}));
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.book) {
            select(SelectActivity.BOOK);
        } else if (id == R.id.chapter) {
            select(SelectActivity.CHAPTER);
        } else if (id == R.id.version) {
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

    private void reloadData(int position) {
        ReadingFragment fragment = (ReadingFragment) mAdapter.instantiateItem(mPager, position);
        Bundle bundle = fragment.getArguments();
        if (bundle != null) {
            bundle.putAll(mAdapter.getData(position));
            fragment.reloadData();
        } else {
            LogUtils.d("reloadData, bundle: null, position: " + position);
        }
    }

    private void jump(String osis, String verse) {
        Bundle bundle = retrieveOsis(POSITION_UNKNOWN, osis);
        bundle.putString(VERSE, verse);
        int position = bundle.getInt(ID) - 1;
        mAdapter.setData(position, bundle);
        prepare(position);

        mPager.setCurrentItem(position);
    }

}
