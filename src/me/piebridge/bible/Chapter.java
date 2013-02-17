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
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import java.util.ArrayList;

import android.util.Log;
import android.preference.PreferenceManager;
import android.content.SharedPreferences.Editor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.widget.ZoomButtonsController;

import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;

public class Chapter extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener {

    private String osis;
    private String book;
    private String chapter;
    private String verse = "";
    private String version = null;

    private String osis_next;
    private String osis_prev;

    private int fontsize = 16;
    private final String link_market = "<a href=\"market://search?q=pub:Liu+DongMiao\">market://search?q=pub:Liu DongMiao</a>";
    private final String link_github = "<a href=\"https://github.com/liudongmiao/bible/downloads\">https://github.com/liudongmiao/bible/downloads</a>";

    private ZoomButtonsController mZoomButtonsController = null;
    private static ArrayList<String> abbrs = new ArrayList<String>();
    private static ArrayList<String> versions = new ArrayList<String>();

    private Spinner spinner;
    private WebView webview;
    private ArrayAdapter<String> adapter;
    private GestureDetector mGestureDetector;

    private static final int DISTANCE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter);
        findViewById(R.id.next).setOnClickListener(this);
        findViewById(R.id.prev).setOnClickListener(this);
        findViewById(R.id.book).setOnClickListener(this);
        findViewById(R.id.chapter).setOnClickListener(this);
        findViewById(R.id.search).setOnClickListener(this);
        findViewById(R.id.version).setOnClickListener(this);

        spinner = new Spinner(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        ((ViewGroup) findViewById(R.id.book).getParent()).addView(spinner, 0, 0);
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1.getRawX() - e2.getRawX() > DISTANCE) {
                    Log.d(Provider.TAG, "swipe left, next osis: " + osis_next);
                    openOsis(osis_next);
                    return true;
                } else if (e2.getRawX() - e1.getRawX() > DISTANCE) {
                    Log.d(Provider.TAG, "swipe right, prev osis: " + osis_prev);
                    openOsis(osis_prev);
                    return true;
                }
                return false;
            }
        });

        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

        try {
            // webview.getZoomButtonsController()
            Method method = WebView.class.getMethod("getZoomButtonsController");
            mZoomButtonsController = (ZoomButtonsController) method.invoke(webview);
            mZoomButtonsController.setOnZoomListener(new ZoomListener());
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
                mZoomButtonsController = (ZoomButtonsController) getControls.invoke(mEmbeddedZoomControl);
                mZoomButtonsController.setOnZoomListener(new ZoomListener());
                // let canZoomOut always be true ...
                Field MINIMUM_SCALE_INCREMENT = mZoomManager.getClass().getDeclaredField("MINIMUM_SCALE_INCREMENT");
                MINIMUM_SCALE_INCREMENT.setAccessible(true);
                MINIMUM_SCALE_INCREMENT.set(mZoomManager, -1f);
            } catch (Exception e2) {
                Log.d(Provider.TAG, "", e2);
            }
        } catch (Exception e3) {
            Log.d(Provider.TAG, "", e3);
        }

        Uri uri = getIntent().getData();
        if (uri == null) {
            uri = setUri();
        } else {
            Log.d(Provider.TAG, "uri: " + uri);
            setVersion();
            verse = String.format("%d", getIntent().getIntExtra("verse", 1));
            Log.d(Provider.TAG, "verse: " + verse);
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
            findViewById(R.id.prev).setVisibility(osis_prev.equals("") ? View.INVISIBLE : View.VISIBLE);
            findViewById(R.id.next).setVisibility(osis_next.equals("") ? View.INVISIBLE : View.VISIBLE);
            showContent(human + " | " + version, content);
        } else {
            Log.d(Provider.TAG, "no such chapter, try first chapter");
            Uri nulluri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
            showUri(nulluri);
        }
    }

    private boolean openOsis(String newOsis) {
        if (newOsis == null || newOsis.equals("")) {
            return false;
        }
        if (!osis.equals(newOsis)) {
            Uri uri = Uri.withAppendedPath(Provider.CONTENT_URI_CHAPTER, newOsis);
            showUri(uri);
        }
        return true;
    }

    private void setBookChapter(String osis) {
        book = osis.split("\\.")[0];
        chapter = osis.split("\\.")[1];
        Log.d(Provider.TAG, "set book chapter, osis: " + osis);

        ((Button)findViewById(R.id.version)).setText(String.valueOf(Provider.databaseVersion).toUpperCase());
        ((Button)findViewById(R.id.book)).setText(Provider.books.get(Provider.osiss.indexOf(book)));
        ((Button)findViewById(R.id.chapter)).setText(chapter);
    }

    private void storeOsisVersion() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("osis", osis);
        if (version != null) {
            editor.putString("version", version);
        }
        editor.commit();
    }

    private void showContent(String title, String content) {
        String context = content;
        // for biblegateway.com
        context = context.replaceAll("<span class=\"chapternum\">.*?</span>", "<sup class=\"versenum\">1 </sup>");
        context = context.replaceAll("<span class=\"chapternum mid-paragraph\">.*?</span>", "");
        if (!verse.equals("")) {
            // generate verse anchor
            context = context.replaceAll("(<strong>\\D*?(\\d+).*?</strong>)", "<a name=\"$2\"></a>$1");
            context = context.replaceAll("<sup(.*?>\\D*?(\\d+).*?)</sup>", "<a name=\"$2\"></a><strong$1</strong>");
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
        verse = "";
        body += context;
        body += "</div>\n</body>\n</html>\n";

        webview.clearCache(true);
        webview.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
    }

    @Override
    public void onPause() {
        storeOsisVersion();
        super.onPause();
    }

    private void initVersion() {
        Resources res = getResources();
        TypedArray abbr = res.obtainTypedArray(R.array.abbr);
        TypedArray version = res.obtainTypedArray(R.array.version);

        for (int i = 0; i < abbr.length(); i++) {
            abbrs.add(abbr.getString(i));
            versions.add(version.getString(i));
        }
    }

    public static String getVersion(String string) {
        int index = abbrs.indexOf(string);
        if (index == -1) {
            return string;
        }
        return versions.get(index);
    }

    @Override
    public void onResume() {
        Provider.setVersions();
        initVersion();
        super.onResume();
    }

    private class ZoomListener implements ZoomButtonsController.OnZoomListener {
        public void onVisibilityChanged(boolean visible) {
            if (visible && mZoomButtonsController != null && fontsize != 1) {
                mZoomButtonsController.setZoomOutEnabled(true);
            }
        }

        public void onZoom(boolean zoomIn) {
            if (fontsize == 1 && !zoomIn) {
                return;
            }
            fontsize += (zoomIn ? 1 : -1);
            Log.d(Provider.TAG, "update fontsize to " + fontsize);
            PreferenceManager.getDefaultSharedPreferences(Chapter.this).edit().putInt("fontsize", fontsize).commit();
            showUri(Uri.withAppendedPath(Provider.CONTENT_URI_CHAPTER, osis));
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.next:
                Log.d(Provider.TAG, "next osis: " + osis_next);
                openOsis(osis_next);
                break;
            case R.id.prev:
                Log.d(Provider.TAG, "prev osis: " + osis_prev);
                openOsis(osis_prev);
                break;
            case R.id.book:
            case R.id.chapter:
            case R.id.version:
                showSpinner(v);
                break;
            case R.id.search:
                onSearchRequested();
                break;
        }
    }

    private void showSpinner(View v) {
        int promptId = 0;
        int selected = 0;
        ArrayList<String> strings = null;

        adapter.clear();
        switch (v.getId()) {
            case R.id.book:
                selected = Provider.osiss.indexOf(book);
                promptId = R.string.choosebook;
                for (String string: Provider.books) {
                    adapter.add(string);
                }
                break;
            case R.id.chapter:
                selected = Integer.parseInt(chapter) - 1;
                promptId = R.string.choosechapter;
                for (int i = 1; i <= Integer.parseInt(Provider.chapters.get(Provider.osiss.indexOf(book))); i++) {
                    adapter.add(String.valueOf(i));
                }
                break;
            case R.id.version:
                Provider.setVersions();
                selected = Provider.versions.indexOf(version);
                promptId = R.string.chooseversion;
                for (String string: Provider.versions) {
                    adapter.add(getVersion(string));
                }
                break;
        }

        spinner.setId(promptId);
        spinner.setPromptId(promptId);
        spinner.setSelection(selected);
        spinner.performClick();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        switch (spinner.getId()) {
            case R.string.choosebook:
                openOsis(Provider.osiss.get(pos) + ".1");
                break;
            case R.string.choosechapter:
                openOsis(String.format("%s.%d", book, pos + 1));
                break;
            case R.string.chooseversion:
                version = Provider.versions.get(pos);
                storeOsisVersion();
                Provider.closeDatabase();
                showUri(setUri());
                break;
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e){
        super.dispatchTouchEvent(e);
        return mGestureDetector.onTouchEvent(e);
    }

    @Override
    public boolean onSearchRequested() {
        if (Provider.versions.size() > 0) {
            startActivity(new Intent(Chapter.this, Search.class));
        }
        return false;
    }
}
