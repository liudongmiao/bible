package me.piebridge.bible.component;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.provider.VersionProvider;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/29.
 */
public class VersionComponent {

    private static final int DATABASE_FLAGS = SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS;

    public static final String INTRO = "int";

    private SQLiteDatabase mDatabase = null;
    private SQLiteDatabase oDatabase = null;
    private String mDatabaseVersion = null;
    private final Object databaseLock = new Object();

    private String mFirstBook;
    private String mLastBook;
    private final Map<String, String> mBooks;
    private final Map<String, List<String>> mChapters;

    private final Context mContext;

    public VersionComponent(Context context) {
        this.mContext = context;
        this.mBooks = new LinkedHashMap<>();
        this.mChapters = new LinkedHashMap<>();
    }

    public String getVersion() {
        return mDatabaseVersion;
    }

    public boolean setVersion(String version, boolean force) {
        if (TextUtils.isEmpty(version)) {
            return false;
        }
        File file = getFile(version);
        if (file == null || !file.isFile()) {
            return false;
        }
        if (mDatabase != null && version.equals(mDatabaseVersion) && !force) {
            return true;
        }
        SQLiteDatabase database;
        try {
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, DATABASE_FLAGS);
            LogUtils.d("open database \"" + database.getPath() + "\"");
        } catch (SQLiteException e) {
            LogUtils.d("cannot open " + file, e);
            return false;
        }
        synchronized (databaseLock) {
            oDatabase = mDatabase;
            mDatabase = database;
            mDatabaseVersion = version;
        }
        loadBooks();
        return true;
    }

    public static Map<String, String> loadBooks(SQLiteDatabase database, String[] firstAndLast) {
        Map<String, String> books = new LinkedHashMap<>();
        try (
                Cursor cursor = database.query(VersionProvider.TABLE_BOOKS,
                        VersionProvider.COLUMNS_BOOKS, null, null, null, null, "_id asc")
        ) {
            if (cursor != null) {
                int bookIndex = cursor.getColumnIndex(VersionProvider.COLUMN_OSIS);
                int humanIndex = cursor.getColumnIndex(VersionProvider.COLUMN_HUMAN);
                while (cursor.moveToNext()) {
                    String book = cursor.getString(bookIndex);
                    String human = cursor.getString(humanIndex);
                    books.put(book, human);
                }
                if (firstAndLast != null && firstAndLast.length == 0x2) {
                    if (cursor.moveToFirst()) {
                        firstAndLast[0] = cursor.getString(bookIndex);
                    }
                    if (cursor.moveToLast()) {
                        firstAndLast[1] = cursor.getString(bookIndex);
                    }
                }
            }
        }
        return books;
    }

    private void loadBooks() {
        Map<String, String> books;
        SQLiteDatabase database = acquireDatabase();
        String[] firstAndLast = new String[2];
        try {
            books = loadBooks(database, firstAndLast);
        } finally {
            releaseDatabase(database);
        }
        synchronized (mBooks) {
            mFirstBook = firstAndLast[0];
            mLastBook = firstAndLast[1];
            mBooks.clear();
            mBooks.putAll(books);
        }
        LogUtils.d("first: " + mFirstBook + ", last: " + mLastBook);
        LogUtils.d("books: " + mBooks);
        synchronized (mChapters) {
            mChapters.clear();
        }
    }

    public List<Integer> getVerses(String book, int chapter) {
        List<Integer> verses = new ArrayList<>();
        SQLiteDatabase database = acquireDatabase();
        try (
                Cursor cursor = database.query(VersionProvider.TABLE_VERSE, VersionProvider.COLUMNS_VERSE,
                        "book = ? and verse > ? and verse < ?",
                        new String[] {book, String.valueOf(chapter), String.valueOf(chapter + 1)},
                        null, null, "id")
        ) {
            BigDecimal thousand = new BigDecimal(VersionProvider.THOUSAND);
            if (cursor != null) {
                int verseIndex = cursor.getColumnIndex(VersionProvider.COLUMN_VERSE);
                while (cursor.moveToNext()) {
                    String verse = cursor.getString(verseIndex);
                    verses.add(new BigDecimal(verse).multiply(thousand).intValue() % VersionProvider.THOUSAND);
                }
            }
        } finally {
            releaseDatabase(database);
        }
        return verses;
    }

    public boolean hasChapter(String version, String osis) {
        File file = getFile(version);
        if (file == null) {
            if (mContext instanceof BibleApplication) {
                ((BibleApplication) mContext).deleteVersion(version);
            }
            return false;
        }
        if (!file.isFile()) {
            return false;
        }
        try (
                SQLiteDatabase metadata = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, DATABASE_FLAGS);
                Cursor cursor = metadata.query(VersionProvider.TABLE_CHAPTERS, new String[] {"id"},
                        "reference_osis = ? or reference_osis = ?",
                        new String[] {osis, removeIntro(osis)},
                        null, null, "id", "1")
        ) {
            return cursor != null && cursor.moveToFirst();
        } catch (SQLiteException e) {
            LogUtils.w("cannot open " + file, e);
        }
        return false;
    }

    public String getAnnotation(String osis, String link) {
        if (TextUtils.isEmpty(osis) || TextUtils.isEmpty(link)) {
            return null;
        }
        SQLiteDatabase database = acquireDatabase();
        try (
                Cursor cursor = database.query("annotations", new String[] {"content"}, "osis = ? and link = ?",
                        new String[] {osis, link}, null, null, null, "1")
        ) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getString(0);
            }
        } finally {
            releaseDatabase(database);
        }
        return null;
    }

    public String removeIntro(String osis) {
        final String intro = "." + INTRO;
        if (osis.endsWith(intro)) {
            return osis.substring(0, osis.lastIndexOf(intro)) + ".1";
        } else {
            return osis;
        }
    }

    public SQLiteDatabase acquireDatabase() {
        synchronized (databaseLock) {
            return mDatabase;
        }
    }

    public void releaseDatabase(SQLiteDatabase database) {
        if (oDatabase != null && oDatabase != database) {
            LogUtils.d("close database \"" + oDatabase.getPath() + "\"");
            oDatabase.close();
            oDatabase = null;
        }
    }

    protected File getFile(String version) {
        return VersionsComponent.getFile(mContext, version);
    }

    public void deleteVersion(String version) {
        File file = getFile(version);
        if (file != null && file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public Map<String, String> getBooks() {
        synchronized (mBooks) {
            return this.mBooks;
        }
    }

    public String getFirstBook() {
        return this.mFirstBook;
    }

    public String getLastBook() {
        return this.mLastBook;
    }

    public String getHuman(String book) {
        synchronized (mBooks) {
            String human = this.mBooks.get(book);
            if (TextUtils.isEmpty(human)) {
                return book;
            } else {
                return human;
            }
        }
    }

    public List<String> getChapters(String book) {
        synchronized (mChapters) {
            List<String> chapters = mChapters.get(book);
            if (chapters == null) {
                chapters = fetchChapters(book);
                mChapters.put(book, chapters);
            }
            return chapters;
        }
    }

    private List<String> fetchChapters(String book) {
        SQLiteDatabase database = acquireDatabase();
        List<String> chapters = new ArrayList<>();
        try (
                Cursor cursor = database.query(VersionProvider.TABLE_CHAPTERS,
                        new String[] {"reference_osis"},
                        "reference_osis like ?", new String[] {book + ".%"}, null, null, " id asc")
        ) {
            if (cursor != null) {
                int chapterIndex = book.length() + 1;
                while (cursor.moveToNext()) {
                    String chapterOsis = cursor.getString(0);
                    chapters.add(chapterOsis.substring(chapterIndex));
                }
            }
        }
        LogUtils.d("book: " + book + ", chapters: " + chapters);
        return chapters;
    }

}
