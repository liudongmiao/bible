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

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
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

    private String osis = "";
    private String book;
    private String chapter;
    private String verse = "";
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
    private int gridviewid = 0;
    protected float scale = 1.0f;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter);
        findViewById(R.id.next).setOnClickListener(this);
        findViewById(R.id.prev).setOnClickListener(this);
        findViewById(R.id.book).setOnClickListener(this);
        findViewById(R.id.chapter).setOnClickListener(this);
        findViewById(R.id.search).setOnClickListener(this);
        findViewById(R.id.version).setOnClickListener(this);

        adapter = new ArrayAdapter<String>(this, R.layout.grid) {
            private LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                TextView textview = null;
                if (view == null) {
                    view = inflater.inflate(R.layout.grid, null);
                }
                textview = (TextView) view.findViewById(R.id.text1);
                if (getItem(position).equals("")) {
                    textview.setVisibility(View.INVISIBLE);
                } else {
                    textview.setText(getItem(position));
                    textview.setVisibility(View.VISIBLE);
                }
                if (gridviewid == R.id.chapter) {
                    textview.setGravity(Gravity.CENTER | Gravity.CENTER_VERTICAL);
                } else {
                    textview.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                }
                if (getItem(position).equals(selected)) {
                    textview.getPaint().setUnderlineText(true);
                } else {
                    textview.getPaint().setUnderlineText(false);
                }
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
        setZoomButtonsController(webview);

        bible = Bible.getBible(getBaseContext());
        Uri uri = getIntent().getData();
        if (uri == null) {
            uri = setUri();
        } else {
            Log.d(TAG, "uri: " + uri);
            verse = String.format("%d", getIntent().getIntExtra("verse", 1));
            Log.d(TAG, "verse: " + verse);
        }
        fontsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("fontsize", 16);
        showUri(uri);
    }

    private Uri setUri()
    {
        osis = PreferenceManager.getDefaultSharedPreferences(this).getString("osis", "null");
        Log.d(TAG, "set osis: " + osis);
        Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
        return uri;
    }

    private void showUri(Uri uri) {
        version = bible.getVersion();
        if (version.equals("")) {
            ((Button)findViewById(R.id.version)).setText(R.string.refreshversion);
            findViewById(R.id.book).setVisibility(View.INVISIBLE);
            findViewById(R.id.chapter).setVisibility(View.INVISIBLE);
            findViewById(R.id.prev).setVisibility(View.INVISIBLE);
            findViewById(R.id.next).setVisibility(View.INVISIBLE);
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
            findViewById(R.id.prev).setVisibility(osis_prev.equals("") ? View.INVISIBLE : View.VISIBLE);
            findViewById(R.id.next).setVisibility(osis_next.equals("") ? View.INVISIBLE : View.VISIBLE);
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
            Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(newOsis).build();
            showUri(uri);
        }
        return true;
    }

    private void setBookChapter(String osis) {
        book = osis.split("\\.")[0];
        chapter = osis.split("\\.")[1];
        Log.d(TAG, "set book chapter, osis: " + osis);

        ((Button)findViewById(R.id.version)).setText(String.valueOf(bible.getVersion()).toUpperCase(Locale.US));
        ((Button)findViewById(R.id.book)).setText(bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, book)));
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
        } else {
            context = context.replaceAll("<sup(.*?)</sup>", "<sup><strong$1</strong></sup>");
        }
        context = context.replaceAll("(<strong>\\D*?(\\d+).*?</strong>)", "<a id=\"" + osis + "_$2\" name=\"$2\"></a><sup>$1</sup>");
        context = context.replaceAll("<sup(.*?>\\D*?(\\d+).*?)</sup>", "<a id=\"" + osis + "_$2\" name=\"$2\"></a><sup><strong$1</strong></sup>");
        context = context.replaceAll("「", "“").replaceAll("」", "”");
        context = context.replaceAll("『", "‘").replaceAll("』", "’");

        fontsize = (int)(fontsize * scale);

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
        body += "<link rel=\"stylesheet\" type=\"text/css\" href=\"reader.css\"/>";
        // body += "<script type=\"text/javascript\" src=\"file:///android_asset/reader.js\"></script>";
        if (verse.equals("")) {
            body += "</head>\n<body>\n<div id=\"content\">\n";
        } else {
            Log.d(TAG, "try jump to verse " + verse);
            body += "</head>\n<body onload=\"window.location.hash='#" + verse + "'\">\n<div id=\"content\">\n";
        }
        verse = "";
        body += context;
        body += "</div>\n</body>\n</html>\n";

        webview.clearCache(true);
        webview.setInitialScale(100);
        scale = 1.0f;
        webview.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
    }

    @Override
    public void onPause() {
        storeOsisVersion();
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        if (bible.getCount(Bible.TYPE.VERSION) == 0 && v.getId() != R.id.version) {
            return;
        }
        switch (v.getId()) {
            case R.id.next:
                Log.d(TAG, "next osis: " + osis_next);
                openOsis(osis_next);
                break;
            case R.id.prev:
                Log.d(TAG, "prev osis: " + osis_prev);
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
                    adapter.add(string.equals(version) ? selected : bible.getVersionResource(string));
                }
                break;
        }

        if (adapter.getCount() > 1) {
            gridview.setVisibility(View.VISIBLE);
            gridview.setSelection(adapter.getPosition(selected));
        } else {
            gridview.setVisibility(View.GONE);
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
                    openOsis(newbook + ".1");
                }
                break;
            case R.id.chapter:
                openOsis(String.format("%s.%d", book, pos + 1));
                break;
            case R.id.version:
                version = bible.get(Bible.TYPE.VERSION, pos);
                Log.d(TAG, "version: " + version);
                bible.setVersion(version);
                Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
                showUri(uri);
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!version.equals(bible.getVersion()) && !osis.equals("")) {
            Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
            showUri(uri);
        }
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
            // let canZoomOut always be true ...
            Field MINIMUM_SCALE_INCREMENT = mZoomManager.getClass().getDeclaredField("MINIMUM_SCALE_INCREMENT");
            MINIMUM_SCALE_INCREMENT.setAccessible(true);
            MINIMUM_SCALE_INCREMENT.set(mZoomManager, -1f);
        } catch (Exception e) {
            Log.e(TAG, "cannot set " + mZoomManager.getClass().getName() + ".MINIMUM_SCALE_INCREMENT to -1", e);
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
            if (visible && mZoomButtonsController != null && fontsize != 1) {
                mZoomButtonsController.setZoomOutEnabled(true);
            }
        }

        public void onZoom(boolean zoomIn) {
            if (fontsize == 1 && !zoomIn) {
                return;
            }
            fontsize += (zoomIn ? 1 : -1);
            Log.d(TAG, "update fontsize to " + fontsize);
            Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
            showUri(uri);
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
}
