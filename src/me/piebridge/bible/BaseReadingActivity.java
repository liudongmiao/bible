package me.piebridge.bible;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 15/10/18.
 */
public abstract class BaseReadingActivity extends FragmentActivity implements ReadingBridge.Bridge {

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
    public static final String VERSE_START = "verseStart";
    public static final String VERSE_END = "verseEnd";
    public static final String FONT_SIZE = "fontSize";
    public static final String CROSS = "cross";
    public static final String SHANGTI = "shangti";
    public static final String VERSION = "version";
    public static final String SEARCH = "search";
    public static final String SELECTED = "selected";
    public static final String HIGHLIGHTED = "highlighted";
    public static final String RED = "red";
    public static final String NIGHT = "night";

    protected static final int POSITION_UNKNOWN = -1;

    private ViewPager mPager;

    protected ReadingAdapter mAdapter;

    private View header;

    private Handler handler = new ReadingHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        updateTheme();
        super.onCreate(savedInstanceState);
        setContentView(getContentLayout());
        header = findHeader();
        mPager = (ViewPager) findViewById(R.id.pager);
        mAdapter = new ReadingAdapter(getSupportFragmentManager(), retrieveOsisCount());
        mPager.setAdapter(mAdapter);
        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                prepare(position);
            }
        });
        initialize();
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

    protected void updateHeader(Bundle bundle) {
        String osis = bundle.getString(OSIS);
        if (TextUtils.isEmpty(osis)) {
            return;
        }

        Bible bible = Bible.getInstance(this);
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

        updateTaskDescription(bookName, chapterVerse);
    }

    protected void updateTaskDescription(String bookName, String chapterVerse) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            StringBuilder sb = new StringBuilder(bookName);
            if (!Bible.isCJK(bookName)) {
                sb.append(" ");
            }
            sb.append(chapterVerse);
            setTaskDescription(new ActivityManager.TaskDescription(sb.toString()));
        }
    }

    public void initialize() {
        int position = getInitialPosition();
        String osis = getInitialOsis();
        Bundle bundle = retrieveOsis(position, osis);
        if (position == POSITION_UNKNOWN) {
            position = bundle.getInt(ID) - 1;
        }
        mPager.setCurrentItem(position);
        mAdapter.getItem(position).getArguments().putAll(bundle);
        prepare(position);
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
        return getFragment(getCurrentPosition()).getArguments().getString(OSIS);
    }

    private ReadingFragment getFragment(int position) {
        return mAdapter.getItem(position);
    }

    public Bundle retrieveOsis(int position, String osis) {
        Bible bible = Bible.getInstance(this);
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
        bundle.putBoolean(NIGHT, ThemeUtils.isDark(this));
        return bundle;
    }

    public void onOpenedOsis(Fragment fragment) {
        int position = fragment.getArguments().getInt(POSITION);
        if (position == getCurrentPosition()) {
            prepare(position);
        }
    }

    private void prepare(int position) {
        Fragment fragment = getFragment(position);
        Bundle bundle = fragment.getArguments();
        updateHeader(bundle);
        if (bundle.containsKey(OSIS)) {
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
        ReadingFragment fragment = getFragment(position);
        fragment.getArguments().putString(OSIS, osis);
        fragment.reloadIfNeeded();
    }

    @Override
    public void setVerse(String verse) {
        // TODO
    }

    @Override
    public void setHighlighted(String highlighted) {
        // TODO
    }

    @Override
    public void showAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", annotation: " + annotation);
        handler.sendMessage(handler.obtainMessage(ReadingHandler.SHOW_ANNOTATION, new String[] {link, annotation, getCurrentOsis()}));
    }

    @Override
    public void showNote(String versenum) {
        // TODO
    }

    @Override
    public void setCopyText(String text) {
        // TODO
    }

}
