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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.widget.ZoomButtonsController;

public class Chapter extends Activity {

    private String osis;
    private String book;
    private String chapter;
    private String version = null;

    private Button button_book;
    private Button button_chapter;
    private Spinner spinner_book;
    private Spinner spinner_chapter;

    private String osis_next;
    private String osis_prev;
    private Button button_next;
    private Button button_prev;
    private Button button_refresh;

    private ArrayAdapter<String> adapter_book;
    private ArrayAdapter<String> adapter_chapter;

    private boolean refreshed = false;
    private ArrayList<String> chapters = new ArrayList<String>();

    private boolean bookChanged = true;

    private WebView webview = null;
    private int fontsize = 16;
    private String verse = "";
    private final String link_market = "<a href=\"market://search?q=pub:Liu+DongMiao\">market://search?q=pub:Liu DongMiao</a>";
    private final String link_github = "<a href=\"https://github.com/liudongmiao/bible/downloads\">https://github.com/liudongmiao/bible/downloads</a>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter);

        button_next = (Button) findViewById(R.id.next);
        button_next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(Provider.TAG, "next osis: " + osis_next);
                openOsis(osis_next);
            }
        });

        button_prev = (Button) findViewById(R.id.prev);
        button_prev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(Provider.TAG, "prev osis: " + osis_prev);
                openOsis(osis_prev);
            }
        });

        button_refresh = (Button) findViewById(R.id.refresh);
        button_refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refresh();
            }
        });

        button_book = (Button) findViewById(R.id.button_book);
        button_book.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateSpinnerBook(true);
                spinner_book.performClick();
            }
        });

        button_chapter = (Button) findViewById(R.id.button_chapter);
        button_chapter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateSpinnerChapter(true);
                spinner_chapter.performClick();
            }
        });

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

        Uri uri = getIntent().getData();

        if (uri == null) {
            uri = setUri();
        } else {
            Log.d(Provider.TAG, "uri: " + uri);
            setVersion();
            verse = "" + getIntent().getIntExtra("verse", 1);
            Log.d(Provider.TAG, "verse: " + verse);
        }

        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setUseWideViewPort(true);

        try {
            // webview.getZoomButtonsController()
            Method method = WebView.class.getMethod("getZoomButtonsController");
            ZoomButtonsController zoomController = (ZoomButtonsController) method.invoke(webview);
            zoomController.setOnZoomListener(new ZoomListener());
        } catch (NoSuchMethodException e1) {
            try {
                // webview.mZoomManager.getCurrentZoomControl().getControls()
                Field field = WebView.class.getDeclaredField("mZoomManager");
                field.setAccessible(true);
                Object mZoomManager = field.get(webview);
                Method getCurrentZoomControl = mZoomManager.getClass().getDeclaredMethod("getCurrentZoomControl");
                getCurrentZoomControl.setAccessible(true);
                Object mEmbeddedZoomControl = getCurrentZoomControl.invoke(mZoomManager);
                Method getControls = mEmbeddedZoomControl.getClass().getDeclaredMethod("getControls");
                getControls.setAccessible(true);
                ZoomButtonsController zoomController = (ZoomButtonsController) getControls.invoke(mEmbeddedZoomControl);
                zoomController.setOnZoomListener(new ZoomListener());
            } catch (Exception e2) {
                Log.d(Provider.TAG, "", e2);
            }
        } catch (Exception e3) {
            Log.d(Provider.TAG, "", e3);
        }
        showUri(uri);
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
        if (version == null) {
            showContent("", getString(R.string.noversion, new Object[] {link_market, link_github}));
            return;
        }
        if (uri == null) {
            Log.d(Provider.TAG, "show null uri, use default");
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
        }
        Log.d(Provider.TAG, "show uri: " + uri);
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();

            osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
            osis_next = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_NEXT));
            osis_prev = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_PREVIOUS));
            final String human = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
            final String content = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CONTENT));
            cursor.close();

            setBookChapter(osis);
            button_prev.setVisibility(osis_prev.equals("") ? View.INVISIBLE : View.VISIBLE);
            button_next.setVisibility(osis_next.equals("") ? View.INVISIBLE : View.VISIBLE);
            showContent(human + " | " + version, content);
        } else {
            Log.d(Provider.TAG, "no such chapter, try first chapter");
            Uri nulluri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
            showUri(nulluri);
        }
    }

    private boolean openOsis(String newOsis) {
        verse = "";
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

        button_book.setText(Provider.books.get(Provider.osiss.indexOf(book)));
        button_chapter.setText(chapter);

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

    private void showContent(String title, String content) {
        String context = content;
        // for biblegateway.com
        context = context.replaceAll("<span class=\"chapternum\">.*?</span>", "<sup class=\"versenum\">1 </sup>");
        context = context.replaceAll("<span class=\"chapternum mid-paragraph\">.*?</span>", "");
        if (!verse.equals("")) {
            // generate verse anchor
            context = context.replaceAll("(<strong>(\\d+).*?</strong>)", "<a name=\"$2\"></a>$1");
            context = context.replaceAll("<sup(.*?>(\\d+).*?)</sup>", "<a name=\"$2\"></a><strong$1</strong>");
        } else {
            context = context.replaceAll("<sup(.*?)</sup>", "<strong$1</strong>");
        }
        context = context.replaceAll("「", "“").replaceAll("」", "”");
        context = context.replaceAll("『", "‘").replaceAll("』", "’");

        String body = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">";
        body += "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">";
        body += "<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n";
        body += "<style type=\"text/css\">\n";
        body += "body {font-family: serif; line-height: 1.4em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        body += ".trans {display: none;}\n";
        body += ".wordsofchrist {color: red;}\n";
        body += "h1 {font-size: 2em;}\n";
        body += "h2 {font-size: 1.5em;}\n";
        body += "</style>\n";
        body += "<title>" + title + "</title>\n";
        // TODO: support verse click-choose
        body += "<link rel=\"stylesheet\" type=\"text/css\" href=\"reader.css\">";
        // body += "<script type=\"text/javascript\" src=\"file:///android_asset/reader.js\"></script>";
        if (verse.equals("")) {
            body += "</head>\n<body>\n<div>\n";
        } else {
            Log.d(Provider.TAG, "try jump to verse " + verse);
            body += "</head>\n<body onload=\"window.location.hash='#" + verse + "'\">\n<div>\n";
        }
        body += context;
        body += "</div>\n</body>\n</html>\n";

        // http://code.google.com/p/anddaaven/source/detail?r=1ca9566a994b
        // http://code.google.com/p/android/issues/detail?id=16839
        webview.clearCache(true);

        webview.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
        webview.computeScroll();
    }

    @Override
    public void onPause() {
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

    private void refresh() {
        refreshed = true;
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("osis", osis).commit();
        showUri(setUri());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.search) {
            onSearchRequested();
            return true;
        } else if (item.getItemId() == R.id.refresh) {
            refresh();
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

    public void onClickChapter(View v) {
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
        if (Provider.osiss.indexOf(book) == -1) {
            Provider.setVersions();
        }

        if (Provider.osiss.indexOf(book) == -1) {
            return;
        }

        if (Provider.versionChanged || bookChanged || force) {
            updateChapters(Provider.chapters.get(Provider.osiss.indexOf(book)));

            Log.d(Provider.TAG, "update chapters");
            adapter_chapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, chapters);
            adapter_chapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner_chapter.setAdapter(adapter_chapter);
            spinner_chapter.setSelection(chapters.indexOf(chapter));
        }
    }

    private class ZoomListener implements ZoomButtonsController.OnZoomListener {
        public void onVisibilityChanged(boolean visible) {
        }

        public void onZoom(boolean zoomIn) {
            fontsize += (zoomIn ? 1 : -1);
            if (fontsize == 0) {
                fontsize = 1;
            }
            Log.d(Provider.TAG, "update fontsize to " + fontsize);
            PreferenceManager.getDefaultSharedPreferences(Chapter.this).edit().putInt("fontsize", fontsize).commit();
            refresh();
        }
    }
}
