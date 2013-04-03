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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;

import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.GridView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Locale;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences.Editor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.widget.ZoomButtonsController;

import android.content.Intent;
import android.database.sqlite.SQLiteException;

public class Chapter extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private final String TAG = "me.piebridge.bible$Chapter";

    private Uri uri = null;
    private String osis = "";
    private String book = "";
    private String chapter = "";
    private String verse = "";
    private Object verseLock = new Object();
    private String version = "";

    private String osis_next;
    private String osis_prev;

    private int fontsize = 16;
    private final String link_market = "<a href=\"market://search?q=pub:Liu+DongMiao\">market://search?q=pub:Liu DongMiao</a>";
    private final String link_github = "<a href=\"https://github.com/liudongmiao/bible/downloads\">https://github.com/liudongmiao/bible/downloads</a>";

    private ZoomButtonsController mZoomButtonsController = null;

    private GridView gridview;
    private WebView webview;
    private ArrayAdapter<String> adapter;

    private final int DISTANCE = 100;
    private GestureDetector mGestureDetector = null;

    private Bible bible;
    private String selected = "";
    private String versename = "";
    private int gridviewid = 0;
    protected float scale = 1.0f;
    protected String background = null;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter);
        findViewById(R.id.book).setOnClickListener(this);
        findViewById(R.id.chapter).setOnClickListener(this);
        findViewById(R.id.search).setOnClickListener(this);
        findViewById(R.id.version).setOnClickListener(this);

        adapter = new ArrayAdapter<String>(this, R.layout.grid) {
            private LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                ToggleButton grid = null;
                if (view == null) {
                    view = inflater.inflate(R.layout.grid, null);
                }
                grid = (ToggleButton) view.findViewById(R.id.text1);
                grid.setTextOn(getItem(position));
                grid.setTextOff(getItem(position));
                grid.setChecked(getItem(position).equals(selected));
                grid.setVisibility(getItem(position).equals("") ? View.INVISIBLE : View.VISIBLE);
                return view;
            }
        };

        gridview = (GridView) findViewById(R.id.gridview);
        gridview.setAdapter(adapter);
        gridview.setVisibility(View.GONE);
        gridview.setOnItemClickListener(this);

        setGestureDetector();
        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webview.addJavascriptInterface(new Object() {
            public void setVerse(String string) {
                synchronized(verseLock) {
                    verse = string;
                    Log.d(TAG, "verse from javascript: " + verse);
                    verseLock.notifyAll();
                }
            }

            public void setCopyText(String text) {
                if (!text.equals("")) {
                    String copytext = version.toUpperCase(Locale.US) + " " + bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, book)) + " " + chapter + ":" + text;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setText(copytext);
                    showToast();
                    Log.d(TAG, "copy from javascript: " + copytext);
                }
            }
        }, "android");
        setZoomButtonsController(webview);

        bible = Bible.getBible(getBaseContext());
        uri = getIntent().getData();
        if (uri == null) {
            setUri();
            verse = PreferenceManager.getDefaultSharedPreferences(this).getString("verse", "");
        } else {
            Log.d(TAG, "uri: " + uri);
            verse = String.format("%d", getIntent().getIntExtra("verse", 1));
        }
        Log.d(TAG, "onCreate, verse=" + verse);
    }

    private void getVerse() {
        if (webview.getScrollY() != 0) {
            verse = "";
            webview.loadUrl("javascript:getFirstVisibleVerse();");
            while(true)  {
                synchronized(verseLock) {
                    if (!verse.equals("")) {
                        break;
                    }
                    try {
                        verseLock.wait(3000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }

    private void setUri()
    {
        osis = PreferenceManager.getDefaultSharedPreferences(this).getString("osis", "null");
        Log.d(TAG, "set osis: " + osis);
        uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
    }

    private void showUri(Uri uri) {
        version = bible.getVersion();
        if (version.equals("")) {
            ((TextView)findViewById(R.id.version)).setText(R.string.refreshversion);
            findViewById(R.id.book).setVisibility(View.INVISIBLE);
            findViewById(R.id.chapter).setVisibility(View.INVISIBLE);
            findViewById(R.id.search).setVisibility(View.INVISIBLE);
            showContent("", getString(R.string.noversion, new Object[] {link_market, link_github}));
            return;
        }

        if (uri == null) {
            Log.d(TAG, "show null uri, use default");
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
        }
        Log.d(TAG, "show uri: " + uri);
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
        } catch (SQLiteException e) {
            showContent("", getString(R.string.queryerror));
            return;
        }
        if (cursor != null) {
            cursor.moveToFirst();

            osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
            osis_next = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_NEXT));
            osis_prev = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_PREVIOUS));
            final String human = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
            final String content = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CONTENT));
            cursor.close();

            setBookChapter(osis);
            findViewById(R.id.book).setVisibility(View.VISIBLE);
            findViewById(R.id.chapter).setVisibility(View.VISIBLE);
            findViewById(R.id.search).setVisibility(View.VISIBLE);
            showContent(human + " | " + version, content);
        } else {
            Log.d(TAG, "no such chapter, try first chapter");
            Uri nulluri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
            if (!nulluri.equals(uri)) {
                showUri(nulluri);
            } else {
                showContent("", getString(R.string.queryerror));
            }
        }
    }

    private boolean openOsis(String newOsis) {
        if (newOsis == null || newOsis.equals("")) {
            return false;
        }
        if (!osis.equals(newOsis)) {
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(newOsis).build();
            showUri(uri);
        }
        return true;
    }

    private void setBookChapter(String osis) {
        book = osis.split("\\.")[0];
        chapter = osis.split("\\.")[1];
        Log.d(TAG, "set book chapter, osis: " + osis);

        ((TextView)findViewById(R.id.version)).setText(String.valueOf(bible.getVersion()).toUpperCase(Locale.US));
        ((TextView)findViewById(R.id.book)).setText(bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, book)));
        ((TextView)findViewById(R.id.chapter)).setText(chapter);
    }

    private void storeOsisVersion() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("osis", osis);
        editor.putString("version", version);
        editor.putString("verse", verse);
        editor.putInt("fontsize", fontsize);
        if (!book.equals("") && !chapter.equals("")) {
            editor.putString(book, chapter);
        }
        if (!version.equals("")) {
            editor.putInt("fontsize-" + version, fontsize);
        }
        editor.commit();
    }

    private void showContent(String title, String content) {
        if (!title.equals("")) {
            versename = "pb-" + version + "-" + book.toLowerCase(Locale.US) + "-" + chapter;
        } else {
            versename = "versename";
        }
        String context = content;
        // for biblegateway.com
        context = context.replaceAll("<span class=\"chapternum\">.*?</span>", "<sup class=\"versenum\">1 </sup>");
        context = context.replaceAll("<span class=\"chapternum mid-paragraph\">.*?</span>", "");
        context = context.replaceAll("(<strong>\\D*?(\\d+).*?</strong>)", "<span class=\"pb-verse\" title=\"$2\"><a id=\"" + versename + "-$2\"></a><sup>$1</sup></span>");
        context = context.replaceAll("<sup(.*?>\\D*?(\\d+).*?)</sup>", "<span class=\"pb-verse\" title=\"$2\"><a id=\"" + versename + "-$2\"></a><sup><strong$1</strong></sup></span>");
        context = context.replaceAll("「", "“").replaceAll("」", "”");
        context = context.replaceAll("『", "‘").replaceAll("』", "’");

        fontsize = (int)(fontsize * scale);
        if (fontsize > 32) {
            fontsize = 24;
        }

        String body = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n";
        body += "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n";
        body += "<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n";
        body += "<meta name=\"viewport\" content=\"target-densitydpi=device-dpi, width=device-width, initial-scale=1.0, minimum-scale=0.1, maximum-scale=2\" />\n";
        body += "<style type=\"text/css\">\n";
        body += "body {font-family: serif; line-height: 1.4em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        body += ".trans {display: none;}\n";
        body += ".wordsofchrist {color: red;}\n";
        body += "h1 {font-size: 2em;}\n";
        body += "h2 {font-size: 1.5em;}\n";
        body += background;
        body += "</style>\n";
        body += "<title>" + title + "</title>\n";
        body += "<link rel=\"stylesheet\" type=\"text/css\" href=\"reader.css\"/>\n";
        body += "<script type=\"text/javascript\" src=\"reader.js\"></script>\n";
        if (verse.equals("")) {
            body += "</head>\n<body>\n<div id=\"content\">\n";
        } else {
            Log.d(TAG, "try jump to verse " + verse);
            body += "</head>\n<body onload=\"window.location.hash='#" + versename + "-" + verse + "'\">\n<div id=\"content\">\n";
            verse = "";
        }
        body += context;
        body += "</div>\n</body>\n</html>\n";
        webview.clearCache(true);
        webview.setInitialScale(100);
        scale = 1.0f;
        webview.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
        /*
        {
            String path = "/sdcard/" + versename + ".html";
            try {
                Log.d("write", path);
                java.io.OutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(new java.io.File(path)));
                os.write(body.getBytes());
                os.close();
                return true;
            } catch (Exception e) {
                Log.e("write", path, e);
            }
        }
        */
    }

    @Override
    public void onPause() {
        getVerse();
        Log.d(TAG, "onPause");
        storeOsisVersion();
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        if (bible.getCount(Bible.TYPE.VERSION) == 0 && bible.getDatabase() == null && v.getId() != R.id.version) {
            return;
        }
        switch (v.getId()) {
            case R.id.version:
                getVerse();
            case R.id.book:
            case R.id.chapter:
                showSpinner(v);
                break;
            case R.id.search:
                onSearchRequested();
                break;
        }
    }

    private int getMatt() {
        int matt = bible.getPosition(Bible.TYPE.OSIS, "Matt");
        if (matt > 0 && matt * 2 > bible.getCount(Bible.TYPE.OSIS)) {
                return matt;
        } else {
                return -1;
        }
    }

    private void showSpinner(View v) {
        adapter.clear();
        gridviewid = v.getId();
        switch (v.getId()) {
            case R.id.book:
                int matt = getMatt();
                gridview.setNumColumns(2);
                Log.d(TAG, "book=" + book);
                selected = bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, book));
                if (matt > 0) {
                    for (int id = 0; id < matt; id++) {
                        int right = matt + id;
                        adapter.add(bible.get(Bible.TYPE.OSIS, id).equals(book) ? selected : bible.get(Bible.TYPE.BOOK, id));
                        if (right < bible.getCount(Bible.TYPE.OSIS)) {
                            adapter.add(bible.get(Bible.TYPE.OSIS, right).equals(book) ? selected : bible.get(Bible.TYPE.BOOK, right));
                        } else {
                            adapter.add("");
                        }
                    }
                } else {
                    for (String string: bible.get(Bible.TYPE.BOOK)) {
                        adapter.add(string.equals(book) ? selected : string);
                    }
                }
                break;
            case R.id.chapter:
                gridview.setNumColumns(5);
                selected = chapter;
                for (int i = 1; i <= Integer.parseInt(bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, book))); i++) {
                    adapter.add(String.valueOf(i).equals(chapter) ? selected : String.valueOf(i));
                }
                break;
            case R.id.version:
                gridview.setNumColumns(1);
                bible.checkVersions();
                Log.d(TAG, "version=" + version);
                selected = bible.getVersionResource(version);
                for (String string: bible.get(Bible.TYPE.VERSION)) {
                    Log.d(TAG, "add version " + string);
                    adapter.add(string.equals(version) ? selected : bible.getVersionResource(string));
                }
                break;
        }

        if (adapter.getCount() > 1) {
            gridview.setVisibility(View.VISIBLE);
            gridview.setSelection(adapter.getPosition(selected));
        } else {
            gridview.setVisibility(View.GONE);
            showUri(uri);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        gridview.setVisibility(View.GONE);
        switch (gridviewid) {
            case R.id.book:
                int matt = getMatt();
                String newbook;
                if (matt > 0) {
                    if (pos % 2 == 0) {
                        newbook = bible.get(Bible.TYPE.OSIS, pos / 2);
                    } else {
                        newbook = bible.get(Bible.TYPE.OSIS, matt + pos / 2);
                    }
                } else {
                    newbook = bible.get(Bible.TYPE.OSIS, pos);
                }
                if (!newbook.equals("") && !newbook.equals(book)) {
                    verse = "";
                    storeOsisVersion();
                    chapter = PreferenceManager.getDefaultSharedPreferences(this).getString(newbook, "1");
                    openOsis(newbook + "." + chapter);
                }
                break;
            case R.id.chapter:
                openOsis(String.format("%s.%d", book, pos + 1));
                break;
            case R.id.version:
                storeOsisVersion();
                version = bible.get(Bible.TYPE.VERSION, pos);
                Log.d(TAG, "version: " + version);
                bible.setVersion(version);
                fontsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("fontsize-" + version, fontsize);
                uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
                showUri(uri);
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        fontsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("fontsize-" + bible.getVersion(), 0);
        if (fontsize == 0) {
            fontsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("fontsize", 16);
        }
        if (fontsize > 32) {
            fontsize = 32;
        }
        if (!version.equals(bible.getVersion()) && !osis.equals("")) {
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
        }
        if (background == null) {
            int color = 0x6633B5E5;
            Integer mHighlightColor = (Integer) getField(findViewById(R.id.version), TextView.class, "mHighlightColor");
            if (mHighlightColor != null) {
                color = mHighlightColor.intValue();
            }
            background = String.format(".selected { background: rgba(%d, %d, %d, %.2f); }\n",
                    (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) / 255.0);
            Log.d(TAG, String.format("color: 0x%08x, background: %s", color, background));
        }
        showUri(uri);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e){
        int scrollY = webview.getScrollY();
        if (scrollY == 0 || (float) scrollY >= webview.getContentHeight() * webview.getScale() - webview.getHeight()) {
            setDisplayZoomControls(true);
        } else {
            setDisplayZoomControls(false);
        }

        super.dispatchTouchEvent(e);

        // getActionMasked since api-8
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                scale = webview.getScale();
                break;
        }

        return mGestureDetector.onTouchEvent(e);
    }

    @Override
    public boolean onSearchRequested() {
        if (bible.getCount(Bible.TYPE.VERSION) > 0) {
            startActivity(new Intent(Chapter.this, Search.class));
        }
        return false;
    }

    private Object getField(Object object, final Class<?> clazz, final String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            Log.e(TAG, "no such filed " + object.getClass().getName() + "." + fieldName);
        }
        return null;
    }

    private boolean setButtonsControllerAPI11(WebView webview) {
        Object mZoomManager = null;

        // android 4.1,  webview.mProvider.mZoomManager.getCurrentZoomControl().getControls()
        Object mProvider = getField(webview, WebView.class, "mProvider");
        if (mProvider != null) {
            mZoomManager = getField(mProvider, mProvider.getClass(), "mZoomManager");
        } else {
            // android 4.0, webview.mZoomManager.getCurrentZoomControl().getControls()
            mZoomManager = getField(webview, WebView.class, "mZoomManager");
        }

        if (mZoomManager == null) {
            return false;
        }

        try {
            // let canZoomOut always be true, part1
            Field MINIMUM_SCALE_INCREMENT = mZoomManager.getClass().getDeclaredField("MINIMUM_SCALE_INCREMENT");
            MINIMUM_SCALE_INCREMENT.setAccessible(true);
            MINIMUM_SCALE_INCREMENT.set(mZoomManager, -128.0f);
        } catch (Exception e) {
            Log.e(TAG, "cannot set " + mZoomManager.getClass().getName() + ".MINIMUM_SCALE_INCREMENT to -128.0f", e);
            return false;
        }

        try {
            // let canZoomOut always be true, part2
            Field mInZoomOverview = mZoomManager.getClass().getDeclaredField("mInZoomOverview");
            mInZoomOverview.setAccessible(true);
            mInZoomOverview.set(mZoomManager, false);
        } catch (Exception e) {
            Log.e(TAG, "cannot set " + mZoomManager.getClass().getName() + ".mInZoomOverview to false", e);
            return false;
        }

        if (mZoomButtonsController != null) {
            return true;
        }

        try {
            Method getCurrentZoomControl = mZoomManager.getClass().getDeclaredMethod("getCurrentZoomControl");
            getCurrentZoomControl.setAccessible(true);
            Object mEmbeddedZoomControl = getCurrentZoomControl.invoke(mZoomManager);
            Method getControls = mEmbeddedZoomControl.getClass().getDeclaredMethod("getControls");
            getControls.setAccessible(true);
            mZoomButtonsController = (ZoomButtonsController) getControls.invoke(mEmbeddedZoomControl);
        } catch (Exception e) {
            Log.e(TAG, "cannot call " + mZoomManager.getClass().getName() + ".getCurrentZoomControl().getControls()", e);
            return false;
        }

        return true;
    }

    private boolean setButtonsControllerAPI(WebView webview) {
        if (mZoomButtonsController != null) {
            return true;
        }

        try {
            Method method = WebView.class.getMethod("getZoomButtonsController");
            mZoomButtonsController = (ZoomButtonsController) method.invoke(webview);
            mZoomButtonsController.setOnZoomListener(new ZoomListener());
        } catch (Exception e) {
            Log.e(TAG, "cannot call " + WebView.class.getName() + ".getZoomButtonsController()", e);
            return false;
        }
        return true;
    }

    private class ZoomListener implements ZoomButtonsController.OnZoomListener {
        public void onVisibilityChanged(boolean visible) {
            setZoomButtonsController(webview);
            if (visible && mZoomButtonsController != null) {
                mZoomButtonsController.setZoomOutEnabled(fontsize > 1);
                mZoomButtonsController.setZoomInEnabled(fontsize < 32);
            }
        }

        public void onZoom(boolean zoomIn) {
            if (fontsize == 1 && !zoomIn) {
                return;
            }
            if (fontsize == 32 && zoomIn) {
                return;
            }
            fontsize += (zoomIn ? 1 : -1);
            Log.d(TAG, "update fontsize to " + fontsize);
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
            showUri(uri);
            setZoomButtonsController(webview);
            if (mZoomButtonsController != null) {
                mZoomButtonsController.setZoomOutEnabled(fontsize > 1);
                mZoomButtonsController.setZoomInEnabled(fontsize < 32);
            }
        }
    }

    private boolean setZoomButtonsController(WebView webview) {
        boolean hasZoomButtons = false;
        if (android.os.Build.VERSION.SDK_INT > 10) {
            hasZoomButtons = setButtonsControllerAPI11(webview);
            if (!hasZoomButtons) {
                hasZoomButtons = setButtonsControllerAPI(webview);
            }
        } else {
            hasZoomButtons = setButtonsControllerAPI(webview);
            if (!hasZoomButtons) {
                hasZoomButtons = setButtonsControllerAPI11(webview);
            }
        }

        if (hasZoomButtons) {
            mZoomButtonsController.setOnZoomListener(new ZoomListener());
            return true;
        }

        return false;
    }

    private void setDisplayZoomControls(boolean enable) {
        if (mZoomButtonsController != null) {
            mZoomButtonsController.getContainer().setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    private void setGestureDetector() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(e1.getRawY() - e2.getRawY()) > Math.abs(e1.getRawX() - e2.getRawX())) {
                    return false;
                }
                if (e1.getRawX() - e2.getRawX() > DISTANCE) {
                    Log.d(TAG, "swipe left, next osis: " + osis_next);
                    openOsis(osis_next);
                    return true;
                } else if (e2.getRawX() - e1.getRawX() > DISTANCE) {
                    Log.d(TAG, "swipe right, prev osis: " + osis_prev);
                    openOsis(osis_prev);
                    return true;
                }
                return false;
            }
        });
    }

    private void showToast() {
        Toast.makeText(getApplicationContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }
}
