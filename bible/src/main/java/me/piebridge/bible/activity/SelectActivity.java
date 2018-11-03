package me.piebridge.bible.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.CompoundButton;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.component.VersionComponent;
import me.piebridge.bible.fragment.SelectBookFragment;
import me.piebridge.bible.fragment.SelectChapterFragment;
import me.piebridge.bible.fragment.SelectVerseFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;

public class SelectActivity extends ToolbarActivity
        implements ViewPager.OnPageChangeListener, CompoundButton.OnCheckedChangeListener {

    public static final int BOOK = 0;
    public static final int CHAPTER = 1;
    public static final int VERSE = 2;

    public static final String POSITION = "position";
    private static final String SHOW_VERSES = "show_verses";

    private ViewPager mPager;
    private SelectAdapter mAdapter;
    private CompoundButton mSwitch;

    private SelectBookFragment selectBook;
    private SelectChapterFragment selectChapter;
    private SelectVerseFragment selectVerse;

    private String book;
    private String chapter;
    private String verse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();

        String osis = intent.getStringExtra(AbstractReadingActivity.OSIS);
        book = BibleUtils.getBook(osis);
        chapter = BibleUtils.getChapter(osis);

        selectBook = new SelectBookFragment();
        selectChapter = new SelectChapterFragment();
        selectVerse = new SelectVerseFragment();

        selectBook.setItems(prepareBooks(), book);
        selectChapter.setItems(prepareChapters(book), chapter);
        selectVerse.selectItems(prepareVerses(book, chapter));

        mSwitch = findViewById(R.id.verse_switch);
        mSwitch.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SHOW_VERSES, true));
        mSwitch.setOnCheckedChangeListener(this);

        mPager = findViewById(R.id.pager);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(mPager);

        mAdapter = new SelectAdapter(getSupportFragmentManager(), new String[] {
                getString(R.string.reading_book), getString(R.string.reading_chapter), getString(R.string.reading_verse)
        }, new Fragment[] {
                selectBook, selectChapter, selectVerse
        });
        mAdapter.setShowVerses(mSwitch.isChecked());
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
                title = ((BibleApplication) getApplication()).getFullname();
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
        BibleApplication application = (BibleApplication) getApplication();
        return application.getHuman(book);
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
        BibleApplication application = (BibleApplication) getApplication();
        return application.getBooks();
    }

    private Map<String, String> prepareChapters(String book) {
        BibleApplication application = (BibleApplication) getApplication();
        Map<String, String> chapters = new LinkedHashMap<>();
        for (String chapter : application.getChapters(book)) {
            String human = VersionComponent.INTRO.equals(chapter) ? getString(R.string.reading_chapter_intro) : chapter;
            chapters.put(chapter, human);
        }
        return chapters;
    }

    private Map<String, Boolean> prepareVerses(String book, String chapter) {
        if (TextUtils.isEmpty(chapter) || !TextUtils.isDigitsOnly(chapter)) {
            return Collections.emptyMap();
        }
        BibleApplication application = (BibleApplication) getApplication();
        List<String> chapters = application.getChapters(book);
        if (chapters.isEmpty()) {
            LogUtils.w("no chapter for book " + book);
            return Collections.emptyMap();
        }
        boolean hasIntro = VersionComponent.INTRO.equals(chapters.get(0));
        List<Integer> bibleVerses = application.getVerses(book, NumberUtils.parseInt(chapter) + (hasIntro ? 1 : 0));
        if (bibleVerses.isEmpty()) {
            return Collections.emptyMap();
        }
        int max = Collections.max(bibleVerses);
        Map<String, Boolean> verses = new LinkedHashMap<>();
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
        this.chapter = PreferenceManager.getDefaultSharedPreferences(this).getString(book, null);
        if (TextUtils.isEmpty(this.chapter)) {
            Map<String, String> chapters = prepareChapters(book);
            this.chapter = chapters.keySet().iterator().next();
            selectChapter.setItems(chapters, this.chapter);
            updateTitle(CHAPTER);
            mPager.setCurrentItem(CHAPTER);
        } else {
            finishSelect();
        }
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
        Map<String, Boolean> verses = prepareVerses(book, chapter);
        if (mAdapter.getCount() > VERSE && !verses.isEmpty()) {
            selectVerse.selectItems(verses);
            updateTitle(VERSE);
            mPager.setCurrentItem(VERSE);
        } else {
            finishSelect();
        }
    }

    private void finishSelect() {
        setResult();
        finish();
    }

    public void setVerse(String verse) {
        this.verse = verse;
        finishSelect();
    }

    private void setResult() {
        Intent intent = new Intent();
        intent.putExtra(AbstractReadingActivity.OSIS, book + "." + chapter);
        intent.putExtra(AbstractReadingActivity.VERSE, verse);
        setResult(Activity.RESULT_OK, intent);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.verse_switch) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean(SHOW_VERSES, isChecked).apply();
            if (!isChecked && mPager.getCurrentItem() == VERSE) {
                finishSelect();
            } else {
                mAdapter.setShowVerses(isChecked);
            }
        }
    }

    private static class SelectAdapter extends FragmentStatePagerAdapter {

        private boolean showVerses;

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
            return showVerses ? pageTitles.length : pageTitles.length - 1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageTitles[position];
        }

        @Override
        public int getItemPosition(Object object) {
            if (!showVerses && object == fragments[VERSE]) {
                return POSITION_NONE;
            } else {
                return POSITION_UNCHANGED;
            }
        }

        public void setShowVerses(boolean showVerses) {
            this.showVerses = showVerses;
            notifyDataSetChanged();
        }

    }

}
