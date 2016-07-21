package me.piebridge.bible.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.Bible;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.SelectBookFragment;
import me.piebridge.bible.fragment.SelectChapterFragment;
import me.piebridge.bible.fragment.SelectVerseFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ThemeUtils;

public class SelectActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener {

    public static final int BOOK = 0;
    public static final int CHAPTER = 1;
    public static final int VERSE = 2;

    public static final String POSITION = "position";

    private ViewPager mPager;
    private SelectAdapter mAdapter;

    private SelectBookFragment selectBook;
    private SelectChapterFragment selectChapter;
    private SelectVerseFragment selectVerse;

    private String book;
    private String chapter;
    private String verse;

    private Bible bible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        bible = Bible.getInstance(this);

        String osis = intent.getStringExtra(AbstractReadingActivity.OSIS);
        book = BibleUtils.getBook(osis);
        chapter = BibleUtils.getChapter(osis);

        selectBook = new SelectBookFragment();
        selectChapter = new SelectChapterFragment();
        selectVerse = new SelectVerseFragment();

        selectBook.setItems(prepareBooks(), book);
        selectChapter.setItems(prepareChapters(book), chapter);
        selectVerse.selectItems(prepareVerses(book, chapter));

        mPager = (ViewPager) findViewById(R.id.pager);
        ((TabLayout) findViewById(R.id.tabs)).setupWithViewPager(mPager);

        mAdapter = new SelectAdapter(getSupportFragmentManager(), new String[] {
                getString(R.string.book), getString(R.string.chapter), getString(R.string.verse)
        }, new Fragment[] {
                selectBook, selectChapter, selectVerse
        });
        mPager.addOnPageChangeListener(this);
        mPager.setAdapter(mAdapter);

        int position = intent.getIntExtra(POSITION, BOOK);
        mPager.setCurrentItem(position);
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
            String human = Bible.INTRO.equals(chapter) ? getString(R.string.intro) : chapter;
            chapters.put(chapter, human);
        }
        return chapters;
    }

    private Map<String, Boolean> prepareVerses(String book, String chapter) {
        if (Bible.INTRO.equals(chapter)) {
            return Collections.emptyMap();
        }
        int position = bible.getPosition(Bible.TYPE.OSIS, book);
        boolean hasInt = bible.get(Bible.TYPE.CHAPTER, position).startsWith(Bible.INTRO);
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
        selectChapter.setItems(prepareChapters(book), chapter);
        updateTitle(CHAPTER);
        mPager.setCurrentItem(CHAPTER);
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
        Map<String, Boolean> verses = prepareVerses(book, chapter);
        if (mAdapter.getCount() >= VERSE && !verses.isEmpty()) {
            selectVerse.selectItems(verses);
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
        intent.putExtra(AbstractReadingActivity.OSIS, book + "." + chapter);
        intent.putExtra(AbstractReadingActivity.VERSE, verse);
        setResult(Activity.RESULT_OK, intent);
    }

    private static class SelectAdapter extends FragmentStatePagerAdapter {

        private final String[] pageTitles;
        private final Fragment[] fragments;

        public SelectAdapter(FragmentManager fm, String[] pageTitles, Fragment[] fragments) {
            super(fm);
            this.pageTitles = pageTitles;
            this.fragments = fragments;
        }

        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return pageTitles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageTitles[position];
        }

    }

}
