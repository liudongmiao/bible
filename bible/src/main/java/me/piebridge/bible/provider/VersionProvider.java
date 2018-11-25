package me.piebridge.bible.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.text.TextUtils;

import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.utils.LogUtils;

public class VersionProvider extends ContentProvider {

    public static final int THOUSAND = 1000;

    public static final String COLUMN_BOOK = "book";
    public static final String COLUMN_VERSE = "verse";
    public static final String COLUMN_HUMAN = "human";
    public static final String COLUMN_UNFORMATTED = "unformatted";
    public static final String COLUMN_PREVIOUS = "previous";
    public static final String COLUMN_NEXT = "next";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_OSIS = "osis";
    public static final String COLUMN_CHAPTERS = "chapters";

    public static final String TABLE_VERSES = "verses left outer join books on (verses.book = books.osis)";
    public static final String[] COLUMNS_VERSES = {"id as _id", "book", "human", "verse", "unformatted"};

    public static final String TABLE_VERSE = "verses";
    public static final String[] COLUMNS_VERSE = {"verse"};

    public static final String TABLE_CHAPTERS = "chapters";
    public static final String[] COLUMNS_CHAPTER =
            {"id as _id", "reference_osis as osis", "reference_human as human", "content",
                    "previous_reference_osis as previous", "next_reference_osis as next"};
    public static final String[] COLUMNS_CHAPTERS = {"count(id) as _count"};

    public static final String TABLE_BOOKS = "books";
    public static final String[] COLUMNS_BOOKS = {"number as _id", "osis", "human", "chapters"};

    public static final String AUTHORITY = "me.piebridge.bible.provider";
    public static final Uri CONTENT_URI_BOOK = Uri.parse("content://" + AUTHORITY + "/book");
    public static final Uri CONTENT_URI_SEARCH = Uri.parse("content://" + AUTHORITY + "/search");
    public static final Uri CONTENT_URI_VERSE = Uri.parse("content://" + AUTHORITY + "/verse");
    public static final Uri CONTENT_URI_CHAPTER = Uri.parse("content://" + AUTHORITY + "/chapter");
    public static final Uri CONTENT_URI_CHAPTERS = Uri.parse("content://" + AUTHORITY + "/chapters");

    private static final int URI_SEARCH = 0;
    private static final int URI_VERSE = 1;
    private static final int URI_CHAPTER = 2;
    private static final int URI_CHAPTERS = 3;
    private static final int URI_BOOK = 4;

    private BibleApplication application;

    private final UriMatcher uriMatcher = buildUriMatcher();

    private UriMatcher buildUriMatcher() {
        UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(AUTHORITY, "search/*", URI_SEARCH);
        matcher.addURI(AUTHORITY, "verse/#", URI_VERSE);
        matcher.addURI(AUTHORITY, "chapter/*", URI_CHAPTER);
        matcher.addURI(AUTHORITY, "chapters", URI_CHAPTERS);
        matcher.addURI(AUTHORITY, "book/*", URI_BOOK);
        return matcher;
    }

    private Cursor queryBook(String query, String books) {
        SQLiteDatabase database = application.acquireDatabase();
        if (database == null) {
            return null;
        }

        Cursor cursor = null;
        try {
            StringBuilder selection = new StringBuilder();
            selection.append("(");
            selection.append("human like ?");
            String osis = application.getOsis(query);
            if (!TextUtils.isEmpty(osis)) {
                selection.append(" or osis = '");
                selection.append(osis);
                selection.append("'");
            }
            selection.append(") ");
            if (!TextUtils.isEmpty(books)) {
                selection.append("and osis in (");
                selection.append(books);
                selection.append(")");
            }
            cursor = database.query(TABLE_BOOKS, COLUMNS_BOOKS,
                    selection.toString(),
                    new String[] {"%" + query + "%"}, null, null, "number ASC");
        } catch (SQLiteException e) {
            LogUtils.w("cannot search " + query, e);
        } finally {
            application.releaseDatabase(database);
        }

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor queryVerse(String query, String books) {
        SQLiteDatabase database = application.acquireDatabase();
        if (database == null) {
            return null;
        }

        Cursor cursor;
        try {
            cursor = database.query(TABLE_VERSES, COLUMNS_VERSES,
                    TextUtils.isEmpty(books) ? "unformatted like ?" : "unformatted like ? and book in (" + books + ")",
                    new String[] {"%" + query + "%"}, null, null, "id ASC");
        } finally {
            application.releaseDatabase(database);
        }

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getVerse(String id) {
        SQLiteDatabase database = application.acquireDatabase();
        if (database == null) {
            return null;
        }

        Cursor cursor;
        try {
            cursor = database.query(TABLE_VERSES, COLUMNS_VERSES,
                    "_id = ?", new String[] {id}, null, null, null);
        } finally {
            application.releaseDatabase(database);
        }

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getChapter(String osis) {
        SQLiteDatabase database = application.acquireDatabase();
        if (database == null) {
            return null;
        }

        Cursor cursor;
        try {
            if (!osis.equals("null")) {
                cursor = database.query(TABLE_CHAPTERS, COLUMNS_CHAPTER,
                        "reference_osis = ? or reference_osis = ?",
                        new String[] {osis, application.removeIntro(osis)},
                        null, null, "id", "1");
            } else {
                cursor = database.query(TABLE_CHAPTERS, COLUMNS_CHAPTER,
                        null, null, null, null, null, "1");
            }
        } finally {
            application.releaseDatabase(database);
        }

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getChapters() {
        SQLiteDatabase database = application.acquireDatabase();
        if (database == null) {
            return null;
        }

        Cursor cursor;
        try {
            cursor = database.query(TABLE_CHAPTERS, COLUMNS_CHAPTERS,
                    null, null, null, null, null);
        } finally {
            application.releaseDatabase(database);
        }

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context instanceof BibleApplication) {
            LogUtils.d("onCreate " + AUTHORITY);
            application = (BibleApplication) context;
            return true;
        } else {
            LogUtils.d("context " + context + " is not application");
            return false;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (application == null) {
            return null;
        }

        String version = uri.getFragment();
        if (TextUtils.isEmpty(version)) {
            LogUtils.d("query uri: " + uri + ", current version: " + application.getVersion());
        } else {
            LogUtils.d("query uri: " + uri + ", version: " + version);
            String normalizedVersion = version.toLowerCase(Locale.US);
            if (!normalizedVersion.equalsIgnoreCase(application.getVersion()) && !application.setVersion(normalizedVersion)) {
                LogUtils.w("cannot switch to version " + normalizedVersion);
            }
        }

        switch (uriMatcher.match(uri)) {
            case URI_SEARCH:
                return queryVerse(uri.getLastPathSegment(), uri.getQueryParameter("books"));
            case URI_VERSE:
                String id = uri.getLastPathSegment();
                return getVerse(id);
            case URI_CHAPTER:
                String osis = uri.getLastPathSegment();
                return getChapter(osis);
            case URI_CHAPTERS:
                return getChapters();
            case URI_BOOK:
                return queryBook(uri.getLastPathSegment(), uri.getQueryParameter("books"));
            default:
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

}
