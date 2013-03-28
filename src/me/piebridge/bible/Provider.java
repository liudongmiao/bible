/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2012, 2013 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.content.UriMatcher;
import android.content.ContentValues;
import android.content.ContentProvider;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import android.net.Uri;

public class Provider extends ContentProvider
{
    private static final String TAG = "me.piebridge.bible$Provider";

    public static final String COLUMN_BOOK = "book";
    public static final String COLUMN_VERSE = "verse";
    public static final String COLUMN_HUMAN = "human";
    public static final String COLUMN_UNFORMATTED = "unformatted";
    public static final String COLUMN_PREVIOUS = "previous";
    public static final String COLUMN_NEXT = "next";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_OSIS = "osis";
    public static final String COLUMN_CHAPTERS = "chapters";

    private static final String TABLE_VERSES = "verses left outer join books on (verses.book = books.osis)";
    private static final String[] COLUMNS_VERSE = {"id as _id", "book", "human", "verse * 1000 as verse", "unformatted"};

    private static final String TABLE_CHAPTERS = "chapters";
    private static final String[] COLUMNS_CHAPTER = {
        "reference_osis as osis",
        "reference_human as human",
        "content",
        "previous_reference_osis as previous",
        "next_reference_osis as next"};

    public static final String TABLE_BOOKS = "books";
    public static final String[] COLUMNS_BOOKS = {"number as _id", "osis", "human", "chapters"};

    public static final String AUTHORITY = "me.piebridge.bible.provider";
    public static final Uri CONTENT_URI_SEARCH = Uri.parse("content://" + AUTHORITY + "/search");
    public static final Uri CONTENT_URI_VERSE = Uri.parse("content://" + AUTHORITY + "/verse");
    public static final Uri CONTENT_URI_CHAPTER = Uri.parse("content://" + AUTHORITY + "/chapter");

    private static final int URI_SEARCH = 0;
    private static final int URI_VERSE = 1;
    private static final int URI_CHAPTER = 2;

    private Bible bible;

    private final UriMatcher uriMatcher = buildUriMatcher();

    private UriMatcher buildUriMatcher() {
        UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(AUTHORITY, "search/*", URI_SEARCH);
        matcher.addURI(AUTHORITY, "verse/#", URI_VERSE);
        matcher.addURI(AUTHORITY, "chapter/*", URI_CHAPTER);

        // TODO: support suggestion
        /*
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT, REFRESH_SHORTCUT);
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT + "/*", REFRESH_SHORTCUT);
           */

        return matcher;
    }

    private Cursor queryVerse(String books, String query) {
        SQLiteDatabase database = bible.getDatabase();
        if (database == null) {
            return null;
        }

        Cursor cursor = database.query(
                TABLE_VERSES,
                COLUMNS_VERSE,
                "unformatted like ? and book in (" + books + ")",
                new String[] { "%" + query + "%"},
                null,
                null,
                "id ASC"
                );

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getVerse(String id) {
        SQLiteDatabase database = bible.getDatabase();

        if (database == null) {
            return null;
        }

        Cursor cursor = database.query(
                TABLE_VERSES,
                COLUMNS_VERSE,
                "_id = ?",
                new String[] {id},
                null,
                null,
                null
                );

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getChapter(String osis) {
        SQLiteDatabase database = bible.getDatabase();

        if (database == null) {
            Log.e(TAG, "database is null");
            return null;
        }

        Cursor cursor = null;

        if (!osis.equals("null")) {
            cursor = database.query(
                TABLE_CHAPTERS,
                COLUMNS_CHAPTER,
                "reference_osis = ?",
                new String[] {osis},
                null,
                null,
                null,
                "1");
        } else {
            cursor = database.query(
                TABLE_CHAPTERS,
                COLUMNS_CHAPTER,
                null,
                null,
                null,
                null,
                null,
                "1");
        }

        if (cursor == null) {
            return null;
        } else if (cursor.getCount() != 1 || !cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    @Override
    public boolean onCreate() {
        bible = Bible.getBible(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (uri == null) {
            return null;
        }
        Log.d(TAG, "query uri: " + uri);
        String version = uri.getFragment();
        if (version != null && !version.equals("") && !bible.setVersion(version)) {
            return null;
        }
        SQLiteDatabase database = bible.getDatabase();
        if (database == null) {
            Log.e(TAG, "database is null");
            return null;
        }
        if (bible.getVersion().equals("")) {
            return null;
        }
        switch (uriMatcher.match(uri)) {
            case URI_SEARCH:
                String query = uri.getLastPathSegment();
                String books = uri.getQueryParameter("books");
                return queryVerse(books, query);
            case URI_VERSE:
                String id = uri.getLastPathSegment();
                return getVerse(id);
            case URI_CHAPTER:
                String osis = uri.getLastPathSegment();
                return getChapter(osis);
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
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
