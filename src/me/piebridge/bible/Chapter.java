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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;

import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;

import android.util.Log;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.text.method.LinkMovementMethod;

public class Chapter extends Activity {

    private String osis;
    private String book;
    private String chapter;
    private String version = null;

    private Button button_next;
    private Button button_prev;

    private TextView text_book;
    private TextView text_chapter;
    private Spinner spinner_book;
    private Spinner spinner_chapter;
    private ArrayAdapter<String> adapter_book;
    private ArrayAdapter<String> adapter_chapter;

    private boolean refreshed = false;
    private ArrayList<String> chapters = new ArrayList<String>();

    private boolean bookChanged = true;

    private WebView webview = null;
    private int fontsize = 16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter);

        button_next = (Button) findViewById(R.id.next);
        button_prev = (Button) findViewById(R.id.prev);

        text_book = (TextView) findViewById(R.id.text_book);
        text_chapter = (TextView) findViewById(R.id.text_chapter);

        text_book.setClickable(true);
        text_chapter.setClickable(true);

        spinner_book = (Spinner) findViewById(R.id.book);
        spinner_book.setPromptId(R.string.choosebook);
        spinner_book.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String newbook = Provider.osiss.get(pos);
                if (!newbook.equals(book)) {
                    Log.d(Provider.TAG, "book adapter selected book: " + newbook + ", old book: " + book);
                    openOsis(newbook + ".1");
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinner_chapter = (Spinner) findViewById(R.id.chapter);
        spinner_chapter.setPromptId(R.string.choosechapter);
        spinner_chapter.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String newchapter = chapters.get(pos);
                if (!newchapter.equals(chapter)) {
                    Log.d(Provider.TAG, "chapter adapter selected chapter: " + newchapter + ", old chapter: " + chapter);
                    openOsis(book + "." + newchapter);
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        button_prev.setVisibility(View.INVISIBLE);
        button_next.setVisibility(View.INVISIBLE);

        Uri uri = getIntent().getData();

        if (uri == null) {
            uri = setUri();
        } else {
            Log.d(Provider.TAG, "uri: " + uri);
            setVersion();
        }

        if (version != null) {
            webview = (WebView) findViewById(R.id.webview);
            showUri(uri);
        } else {
            setContentView(R.layout.main);
            TextView textView = (TextView)findViewById(R.id.text);
            textView.setText(R.string.noversion);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
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

        fontsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("fontsize", 16);
        Log.d(Provider.TAG, "set fontsize: " + fontsize);
    }

    private Uri setUri()
    {
        osis = PreferenceManager.getDefaultSharedPreferences(this).getString("osis", "null");
        Log.d(Provider.TAG, "set osis: " + osis);

        setVersion();
        Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();

        return uri;
    }

    private void showUri(Uri uri) {
        if (uri == null) {
            Log.d(Provider.TAG, "show null uri, use default");
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
        }
        Log.d(Provider.TAG, "show uri: " + uri);
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();

            osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
            final String human = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
            final String osis_next = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_NEXT));
            final String osis_prev = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_PREVIOUS));
            final String content = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CONTENT));
            cursor.close();

            setBookChapter(osis);

            button_prev.setVisibility(View.INVISIBLE);
            if (!osis_prev.equals("")) {
                button_prev.setVisibility(View.VISIBLE);
                button_prev.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Log.d(Provider.TAG, "prev osis: " + osis_prev);
                        openOsis(osis_prev);
                    }
                });
            }

            button_next.setVisibility(View.INVISIBLE);
            if (!osis_next.equals("")) {
                button_next.setVisibility(View.VISIBLE);
                button_next.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Log.d(Provider.TAG, "next osis: " + osis_next);
                        openOsis(osis_next);
                    }
                });
            }

            showContent(content);
        } else {
            Log.d(Provider.TAG, "no such chapter, try first chapter");
            Uri nulluri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
            showUri(nulluri);
        }
    }

    private void storeFontSize() {
        if (webview != null && webview.getScale() != 1.0) {
            fontsize = (int)(fontsize * webview.getScale() + 0.5f);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putInt("fontsize", fontsize).commit();
            Log.d(Provider.TAG, "set fontsize to " + fontsize);
        }
    }

    private boolean openOsis(String newOsis) {
        storeFontSize();
        if (Provider.versionChanged || bookChanged) {
            bookChanged = false;
            Provider.versionChanged = false;
        }
        if (!osis.equals(newOsis)) {
            if (!book.equals(newOsis.split("\\.")[0])) {
                bookChanged = true;
            }
            Uri uri = Uri.withAppendedPath(Provider.CONTENT_URI_CHAPTER, newOsis);
            showUri(uri);
        }
        return true;
    }

    private void setBookChapter(String osis) {
        book = osis.split("\\.")[0];
        chapter = osis.split("\\.")[1];
        Log.d(Provider.TAG, "set book chapter, osis: " + osis);

        text_book.setText(Provider.books.get(Provider.osiss.indexOf(book)));
        text_chapter.setText(chapter);

        updateSpinnerBook(false);
        updateSpinnerChapter(false);
    }

    private void updateChapters(String count) {
        int value = Integer.parseInt(count);
        chapters.clear();
        for (int i = 1; i <= value; i++) {
            chapters.add(String.valueOf(i));
        }
    }

    private void storeOsisVersion() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("osis", osis).commit();
        if (version != null) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("version", version).commit();
        }
    }

    private void showContent(String content) {
        String context = content;
        // TODO: support translate notes
        context = context.replaceAll("title=\"[^\"]*\"", "");
        context = context.replaceAll("「", "“").replaceAll("」", "’");
        context = context.replaceAll("『", "‘").replaceAll("』", "’");
        // define quote class for quote
        context = context.replaceAll("([“”‘’])", "<span class=\"quote\">$1</span>");

        String body = "<!doctype html>\n<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n";
        body += "<style type=\"text/css\">\n";
        body += "body {line-height: 1.5em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        body += ".trans {display: none;}\n";
        body += ".wordsofchrist {color: red;}\n";
        body += ".quote {font-family: serif;}\n";
        body += "h1 {font-size: 2em;}\n";
        body += "h2 {font-size: 1.5em;}\n";
        body += "</style>\n";
        // TODO: support javascript
        // body += "<link rel=\"stylesheet\" href=\"file:///android_asset/reader.css\">";
        // body += "<script type=\"text/javascript\" src=\"file:///android_asset/reader.js\"></script>";
        body += "</head>\n<body>\n<div>\n";
        body += context;
        body += "</div>\n</body>\n</html>\n";

        // http://code.google.com/p/anddaaven/source/detail?r=1ca9566a994b
        // http://code.google.com/p/android/issues/detail?id=16839
        webview.clearCache(true);

        webview.loadData(body, "text/html", "utf-8");
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.computeScroll();
    }

    @Override
    public void onPause() {
        storeFontSize();
        storeOsisVersion();
        super.onPause();
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
            storeFontSize();
            onSearchRequested();
            return true;
        } else if (item.getItemId() == R.id.refresh) {
            storeFontSize();
            refreshed = true;
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("osis", osis).commit();
            if (webview != null) {
                webview.clearView();
            }
            showUri(setUri());
            return true;
        }

        if (item.getGroupId() != R.id.group) {
            return super.onOptionsItemSelected(item);
        }

        if (item.isChecked()) {
            item.setChecked(false);
        } else {
            storeFontSize();
            item.setChecked(true);
            version = Provider.versions.get(item.getItemId());
            Log.d(Provider.TAG, "choose version: " + version);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("osis", osis).commit();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("version", version).commit();
            Provider.closeDatabase();
            showUri(setUri());
        }

        return true;
    }

    @Override
    public void onResume() {
        Provider.versionChanged = true;
        Provider.setVersions();
        super.onResume();
    }

    public void onClickBook(View v) {
        updateSpinnerBook(true);
        spinner_book.performClick();
    }

    public void onClickChapter(View v) {
        updateSpinnerChapter(true);
        spinner_chapter.performClick();
    }

    private void updateSpinnerBook(boolean force) {
        if (Provider.versionChanged || force) {
            Log.d(Provider.TAG, "update books");
            adapter_book = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, Provider.books);
            adapter_book.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner_book.setAdapter(adapter_book);
            spinner_book.setSelection(Provider.osiss.indexOf(book));
        }
    }

    private void updateSpinnerChapter(boolean force) {
        if (Provider.versionChanged || bookChanged || force) {
            updateChapters(Provider.chapters.get(Provider.osiss.indexOf(book)));

            Log.d(Provider.TAG, "update chapters");
            adapter_chapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, chapters);
            adapter_chapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner_chapter.setAdapter(adapter_chapter);
            spinner_chapter.setSelection(chapters.indexOf(chapter));
        }
    }
}
