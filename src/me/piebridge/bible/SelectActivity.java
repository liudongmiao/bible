package me.piebridge.bible;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.fragment.SelectBook;
import me.piebridge.bible.fragment.SelectChapter;
import me.piebridge.bible.fragment.SelectVerse;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ThemeUtils;

public class SelectActivity extends FragmentActivity implements ViewPager.OnPageChangeListener {

    public static final int BOOK = 0;
    public static final int CHAPTER = 1;
    private static final int VERSE = 2;

    public static final String POSITION = "position";

    private String[] mPageTitles;

    private ViewPager mPager;
    private ScreenSlidePagerAdapter mAdapter;

    private SelectBook selectBook;
    private SelectChapter selectChapter;
    private SelectVerse selectVerse;

    private String book;
    private String chapter;
    private String verse;

    private Bible bible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        bible = Bible.getInstance(this);

        mPageTitles = new String[] {
                getString(R.string.book), getString(R.string.chapter), getString(R.string.verse)
        };

        mPager = (ViewPager) findViewById(R.id.pager);

        mAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager.setOnPageChangeListener(this);
        mPager.setAdapter(mAdapter);

        selectBook = new SelectBook();
        selectChapter = new SelectChapter();
        selectVerse = new SelectVerse();

        Intent intent = getIntent();
        int position = intent.getIntExtra(POSITION, 0);
        mPager.setCurrentItem(position);

        String osis = intent.getStringExtra(BaseActivity.OSIS);
        book = BibleUtils.getBook(osis);
        chapter = BibleUtils.getChapter(osis);
        selectBook.setBooks(prepareBooks(), book);
        selectChapter.setData(prepareChapters(book), chapter);
        selectVerse.setVerses(prepareVerses(book, chapter));

        updateTitle(position);
    }

    private void updateTitle(int position) {
        String title;
        switch (position) {
            case BOOK:
                title = getString(R.string.books);
                break;
            case CHAPTER:
                title = getBookName();
                break;
            case VERSE:
                String bookName = getBookName();
                title = BibleUtils.getBookChapterVerse(bookName, chapter);
                break;
            default:
                return;
        }
        setTitle(title);
    }

    private String getBookName() {
        int position = bible.getPosition(Bible.TYPE.OSIS, book);
        return bible.get(Bible.TYPE.HUMAN, position);
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
        updateTitle(position);
    }

    private Map<String, String> prepareBooks() {
        Map<String, String> books = new LinkedHashMap<String, String>();
        int count = bible.getCount(Bible.TYPE.OSIS);
        for (int index = 0; index < count; ++index) {
            String osis = bible.get(Bible.TYPE.OSIS, index);
            String human = bible.get(Bible.TYPE.HUMAN, index);
            books.put(osis, human);
        }
        return books;
    }

    private Map<String, String> prepareChapters(String book) {
        int position = bible.getPosition(Bible.TYPE.OSIS, book);
        Map<String, String> chapters = new LinkedHashMap<String, String>();
        for (String chapter : bible.get(Bible.TYPE.CHAPTER, position).split(",")) {
            String human = "int".equalsIgnoreCase(chapter) ? getString(R.string.intro) : chapter;
            chapters.put(chapter, human);
        }
        return chapters;
    }

    private Map<String, Boolean> prepareVerses(String book, String chapter) {
        if ("int".equals(chapter)) {
            return Collections.emptyMap();
        }
        int position = bible.getPosition(Bible.TYPE.OSIS, book);
        boolean hasInt = bible.get(Bible.TYPE.CHAPTER, position).startsWith("int");
        List<Integer> bibleVerses = bible.getVerses(book, NumberUtils.parseInt(chapter) + (hasInt ? 1 : 0));
        if (bibleVerses.isEmpty()) {
            return Collections.emptyMap();
        }
        int max = bibleVerses.get(bibleVerses.size() - 1);
        Map<String, Boolean> verses = new LinkedHashMap<String, Boolean>();
        if (max == bibleVerses.size()) {
            for (int i = 1; i <= max; ++i) {
                verses.put(String.valueOf(i), true);
            }
        } else {
            boolean[] selectable = new boolean[max + 1];
            for (Integer bibleVerse : bibleVerses) {
                selectable[bibleVerse] = true;
            }
            for (int i = 1; i <= max; ++i) {
                verses.put(String.valueOf(i), selectable[i]);
            }
        }
        return verses;
    }

    public void setBook(String book) {
        this.book = book;
        this.chapter = PreferenceManager.getDefaultSharedPreferences(this).getString(chapter, "1");
        selectChapter.setData(prepareChapters(book), chapter);
        updateTitle(CHAPTER);
        mPager.setCurrentItem(CHAPTER);
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
        Map<String, Boolean> verses = prepareVerses(book, chapter);
        if (mAdapter.getCount() >= VERSE && !verses.isEmpty()) {
            selectVerse.setVerses(verses);
            updateTitle(VERSE);
            mPager.setCurrentItem(VERSE);
        } else {
            setResult();
            finish();
        }
    }

    public void setVerse(String verse) {
        this.verse = verse;
        setResult();
        finish();
    }

    private void setResult() {
        Intent intent = new Intent();
        intent.putExtra(BaseActivity.OSIS, book + "." + chapter);
        intent.putExtra(BaseActivity.VERSE, verse);
        setResult(Activity.RESULT_OK, intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {

        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case BOOK:
                    return selectBook;
                case CHAPTER:
                    return selectChapter;
                case VERSE:
                    return selectVerse;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return mPageTitles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mPageTitles[position];
        }

    }

}
