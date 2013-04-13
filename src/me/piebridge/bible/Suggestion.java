/*
 * vim: set sw=4 ts=4:
 * Author: Liu DongMiao <liudongmiao@gmail.com>
 * Created  : TIMESTAMP
 * Modified : TIMESTAMP
 */

package me.piebridge.bible;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

public class Suggestion extends ContentProvider {

    private static final String AUTHORITY = "me.piebridge.bible";
    private static final String TAG = "me.piebridge.bible$Suggestion";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.d(TAG , "uri: " + uri);
        String query = uri.getLastPathSegment();
        try {
            String[] columnNames = { BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_INTENT_DATA };
            MatrixCursor cursor = new MatrixCursor(columnNames);
            Bible bible = Bible.getBible(getContext());
            LinkedHashMap<String, String> suggestions = bible.getOsiss(query);
            int i = 0;
            for (Entry<String, String> entry: suggestions.entrySet()) {
                String[] row = { String.valueOf(i), entry.getKey(), "bible://passage?search=" + entry.getValue() };
                cursor.addRow(row);
                i++;
            }
            if (query != null && !query.equals(SearchManager.SUGGEST_URI_PATH_QUERY)) {
                String[] row = {String.valueOf(i), getContext().getString(R.string.search, new Object[] {query}), "bible://passage?search=" + query };
                cursor.addRow(row);
            }
            return cursor;
        } catch (Exception e) {
            Log.e(TAG, "query", e);
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

}
