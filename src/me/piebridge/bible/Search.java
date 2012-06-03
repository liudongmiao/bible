/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2012 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.app.Activity;
import android.app.SearchManager;
import android.os.Bundle;

import android.view.View;
import android.view.View.OnKeyListener;

import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;

import android.content.Intent;

import android.net.Uri;
import android.util.Log;
import android.database.Cursor;

import android.preference.PreferenceManager;

import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;

public class Search extends Activity
{
    private TextView textView = null;
    private ListView listView = null;;

    private String version = null;
    private String query = null;

    private SimpleCursorAdapter adapter = null;

    private boolean refreshed = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        textView = (TextView) findViewById(R.id.text);
        listView = (ListView) findViewById(R.id.list);

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            setVersion();
            query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        }

        if (version == null) {
            textView.setText(R.string.noversion);
        }

    }

    private boolean doSearch(String query) {
        if (version == null) {
            return false;
        }

        Provider.setVersions();
        Log.d(Provider.TAG, "search \"" + query + "\" in version \"" + version + "\"");
        if (Provider.versions.indexOf("rcuvss") >= 0 && Provider.isCJK(query.charAt(0)) && !Provider.isCJKVersion(version)) {
            Log.d(Provider.TAG, "\"" + version + "\" is not a cjk version, change to rcuvss");
            version = "rcuvss";
        } else if (Provider.versions.indexOf("niv") >= 0 && !Provider.isCJK(query.charAt(0)) && Provider.isCJKVersion(version)) {
            Log.d(Provider.TAG, "\"" + version + "\" is a cjk version, change to niv");
            version = "niv";
        }

        Uri uri = Provider.CONTENT_URI_SEARCH.buildUpon().appendEncodedPath(query).fragment(version).build();
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor == null) {
            textView.setText(getString(R.string.search_no_results, new Object[] {query, version}));
            return false;
        } else {
            int count = cursor.getCount();
            String countString = getResources().getQuantityString(R.plurals.search_results,
                    count, new Object[] {count, query, version});
            textView.setText(countString);
        }
        showResults(cursor);
        return true;
    }

    private void closeAdapter() {
        if (adapter != null) {
            Cursor cursor = adapter.getCursor();
            cursor.close();
            adapter = null;
        }
    }

    private void showResults(Cursor cursor) {

        String[] from = new String[] {
            Provider.COLUMN_HUMAN,
                Provider.COLUMN_VERSE,
                Provider.COLUMN_UNFORMATTED,
        };

        int[] to = new int[] {
            R.id.human,
                R.id.verse,
                R.id.unformatted,
        };

        closeAdapter();
        adapter = new SimpleCursorAdapter(this,
                R.layout.result, cursor, from, to);
        adapter.setViewBinder(new ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                int verseIndex = cursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE);
                if (columnIndex == verseIndex) {
                    int[] chapterVerse = Provider.getChapterVerse(cursor.getString(verseIndex));
                    String string = getString(R.string.search_result_verse,
                        new Object[] {chapterVerse[0], chapterVerse[1]});
                    TextView textView = (TextView) view;
                    textView.setText(string);
                    return true;
                }

                return false;
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showVerse(String.valueOf(id));
            }
        });
    }

    private boolean showVerse(String id) {
        Uri uri = Provider.CONTENT_URI_VERSE.buildUpon().appendEncodedPath(id).fragment(version).build();
        Cursor verseCursor = getContentResolver().query(uri, null, null, null, null);

        String book = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
        String verse = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE));
        int[] chapterVerse = Provider.getChapterVerse(verse);
        String osis = book + "." + chapterVerse[0];
        Log.d(Provider.TAG, "show osis: " + osis + ", version: " + version);
        verseCursor.close();

        // TODO: support verse
        Intent chapterIntent = new Intent(getApplicationContext(), Chapter.class);
        Uri data = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
        chapterIntent.setData(data);
        startActivity(chapterIntent);

        return true;
    }

    private void setVersion() {
        Provider.setVersions();
        version = Provider.databaseVersion;
        if (version.equals("")) {
            version = PreferenceManager.getDefaultSharedPreferences(this).getString("version", null);
            if (version != null && Provider.versions.indexOf(version) < 0) {
                version = null;
            }
            if (version == null && Provider.versions.size() > 0) {
                version = Provider.versions.get(0);
            }
        }

        Log.d(Provider.TAG, "set version: " + version);
    }

    private void createMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        MenuItem select = menu.findItem(R.id.select);
        Menu submenu = select.getSubMenu();
        if (version == null) {
            setVersion();
        } else {
            Provider.setVersions();
        }
        for (String string: Provider.versions) {
            MenuItem item = submenu.add(R.id.group, Provider.versions.indexOf(string), Menu.NONE, string);
            item.setCheckable(true);
            if (string.equals(version)) {
                item.setChecked(true);
            }
        }
        submenu.setGroupCheckable(R.id.group, true, true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        createMenu(menu);
        refreshed = false;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (refreshed) {
            refreshed = false;
            menu.clear();
            createMenu(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.search) {
            onSearchRequested();
            return true;
        } else if (item.getItemId() == R.id.refresh) {
            refreshed = true;
            doSearch(query);
            return true;
        }

        if (item.getGroupId() != R.id.group) {
            return super.onOptionsItemSelected(item);
        }

        if (item.isChecked()) {
            item.setChecked(false);
        } else {
            item.setChecked(true);
            version = Provider.versions.get(item.getItemId());
            Log.d(Provider.TAG, "choose version: " + version);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("version", version).commit();
            closeAdapter();
            Provider.closeDatabase();
            doSearch(query);
        }

        return true;
    }

    @Override
    public void onResume() {
        refreshed = true;
        super.onResume();
    }
}
