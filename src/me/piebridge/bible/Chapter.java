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
import android.app.AlertDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.GridView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Locale;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.widget.ZoomButtonsController;
import android.content.Intent;
import android.database.sqlite.SQLiteException;

import java.io.File;

import android.os.Environment;

public class Chapter extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private final String TAG = "me.piebridge.bible$Chapter";

    private int index = 0;
    private ArrayList<OsisItem> items = null;
    private String search = null;
    private Uri uri = null;
    private String osis = "";
    private String book = "";
    private String chapter = "";
    private String verse = "";
    private String end = "";
    private Object verseLock = new Object();
    private String version = "";

    private String osis_next;
    private String osis_prev;

    private int fontsize = 16;
    private boolean onzoom = false;
    private boolean setListener = false;
    private int FONTSIZE_MIN = 1;
    private int FONTSIZE_MED = 32;
    private int FONTSIZE_MAX = 80;
    private final String link_github = "<a href=\"https://github.com/liudongmiao/bible/tree/master/apk\">https://github.com/liudongmiao/bible/tree/master/apk</a>";

    private ZoomButtonsController mZoomButtonsController = null;

    private GridView gridview;
    private WebView webview;
    private ArrayAdapter<String> adapter;

    private final int DISTANCE = 100;
    private GestureDetector mGestureDetector = null;

    private Bible bible = null;
    private String selected = "";
    private String versename = "";
    private File font;
    private int gridviewid = 0;
    protected float scale = 1.0f;
    protected String background = null;
    protected String copytext = "";
    protected static final int COPYTEXT = 0;
    protected static final int SHOWCONTENT = 1;
    protected static final int SHOWDATA = 2;
    protected static final int SHOWBAR = 3;
    protected static final int DISMISSBAR = 4;
    protected static final int SHOWZOOM = 5;

    private boolean hasIntentData = false;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COPYTEXT:
                    checkShare();
                    break;
                case SHOWCONTENT:
                    String[] message = (String[]) msg.obj;
                    if (!"".equals(message[0])) {
                        setBookChapter();
                    }
                    _showContent(message[0], message[1]);
                    break;
                case SHOWDATA:
                    _showData();
                    break;
                case SHOWBAR:
                    _show();
                    break;
                case DISMISSBAR:
                    _dismiss();
                    break;
                case SHOWZOOM:
                    if (mZoomButtonsController != null) {
                        mZoomButtonsController.setZoomOutEnabled(fontsize > FONTSIZE_MIN);
                        mZoomButtonsController.setZoomInEnabled(fontsize < FONTSIZE_MAX);
                    }
                    break;
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // on MB612, the search and share icon is so dark ...
        if (Build.MODEL.equals("MB612")) {
            setTheme(android.R.style.Theme_Light_NoTitleBar);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter);
        findViewById(R.id.book).setOnClickListener(this);
        findViewById(R.id.chapter).setOnClickListener(this);
        findViewById(R.id.search).setOnClickListener(this);
        findViewById(R.id.version).setOnClickListener(this);
        findViewById(R.id.share).setOnClickListener(this);
        findViewById(R.id.items).setOnClickListener(this);

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
        gridview.setOnItemLongClickListener(this);

        setGestureDetector();
        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webview.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setVerse(String string) {
                synchronized(verseLock) {
                    verse = string;
                    Log.d(TAG, "verse from javascript: " + verse);
                    verseLock.notifyAll();
                }
            }

            @SuppressWarnings("deprecation")
            @JavascriptInterface
            public void setCopyText(String text) {
                if (!text.equals("")) {
                    copytext = bible.getVersionFullname(version).replace("(" + getString(R.string.demo) + ")", "") + " ";
                    copytext += bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, book)) + " " + chapter + ":" + text;
                    ((android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(copytext);
                    Log.d(TAG, "copy from javascript: " + copytext);
                } else {
                    copytext = "";
                }
                handler.sendEmptyMessage(COPYTEXT);
            }
        }, "android");
        webview.getSettings().setBuiltInZoomControls(true);
        if (!setZoomButtonsController(webview)) {
            webview.getSettings().setBuiltInZoomControls(false);
        }

        osis = PreferenceManager.getDefaultSharedPreferences(this).getString("osis", "null");
        uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();

        setIntentData();
        int color = 0x6633B5E5;
        Integer mHighlightColor = (Integer) Bible.getField(findViewById(R.id.version), TextView.class, "mHighlightColor");
        if (mHighlightColor != null) {
            color = mHighlightColor.intValue();
        }
        background = String.format("background: rgba(%d, %d, %d, %.2f);",
                (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) / 255.0);
        Log.d(TAG, "onCreate");
        hasIntentData = true;
        font = new File(new File(new File(new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data"), getPackageName()), "files"), "custom.ttf");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!hasIntentData) {
            verse = sp.getString("verse", "");
        }
        show();
        showData();
    }

    private void getVerse() {
        if (webview.getScrollY() != 0) {
            verse = "";
            webview.loadUrl("javascript:getFirstVisibleVerse();");
            synchronized(verseLock) {
                if (verse.equals("")) {
                    try {
                        verseLock.wait(3000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    private void showUri() {
        show();
        new Thread(new Runnable() {
            public void run() {
                _showUri();
            }
        }).start();
    }

    private void _showUri() {
        version = bible.getVersion();
        Log.d(TAG, "showuri: " + uri);
        if (uri == null) {
            Log.d(TAG, "show null uri, use default");
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
        } catch (SQLiteException e) {
            showContent("", getString(R.string.queryerror));
            return;
        } catch (Exception e) {
        }
        if (cursor != null) {
            cursor.moveToFirst();

            osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
            osis_next = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_NEXT));
            osis_prev = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_PREVIOUS));
            final String human = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
            final String content = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CONTENT));
            cursor.close();

            showContent(human + " | " + version, content);
        } else {
            Log.d(TAG, "no such chapter, try first chapter");
            Uri nulluri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
            if (!nulluri.equals(uri)) {
                uri = nulluri;
                showUri();
            } else {
                showContent("", getString(R.string.queryerror));
            }
        }
    }

    private boolean openOsis(String newOsis) {
        return openOsis(newOsis, "", "");
    }

    private boolean openOsis(String newOsis, String verse, String end) {
        if (newOsis == null || newOsis.equals("")) {
            return false;
        }
        if (!osis.equals(newOsis)) {
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(newOsis).build();
            if ("".equals(this.verse)) {
                this.verse = verse;
            }
            this.end = end;
            showUri();
        }
        return true;
    }

    private void setBookChapter() {
        book = osis.split("\\.")[0];
        if (osis.split("\\.").length > 1) {
            chapter = osis.split("\\.")[1];
        } else {
            chapter = "0";
        }
        Log.d(TAG, "set book chapter, osis: " + osis);

        setItemText(this.index);
        ((TextView)findViewById(R.id.version)).setText(bible.getVersionName(bible.getVersion()));
        ((TextView)findViewById(R.id.book)).setText(bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, book)));
        if (!"".equals(verse) && !"".equals(end)) {
            ((TextView)findViewById(R.id.chapter)).setText(chapter + ":" + verse + "-" + end);
        } else if (!"".equals(verse) || !"".equals(end)) {
            ((TextView)findViewById(R.id.chapter)).setText(chapter + ":" + verse + end);
        } else {
            ((TextView)findViewById(R.id.chapter)).setText(chapter);
        }
    }

    private void storeOsisVersion() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("osis", osis);
        if (!version.endsWith("demo") && !version.equals("")) {
            editor.putString("version", version);
        }
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

    private boolean showed = false;
    private void showContent(String title, String content) {
        handler.sendMessage(handler.obtainMessage(SHOWCONTENT, new String[] {title, content }));
        showed = true;
    }

    private void _showContent(String title, String content) {
        if (!title.equals("")) {
            versename = "pb-" + version + "-" + book.toLowerCase(Locale.US) + "-" + chapter;
        } else {
            versename = "versename";
        }
        copytext = "";
        showView(R.id.share, !copytext.equals(""));
        String context = content;
        // for biblegateway.com
        context = context.replaceAll("<span class=\"chapternum\">.*?</span>", "<sup class=\"versenum\">1 </sup>");
        context = context.replaceAll("<span class=\"chapternum mid-paragraph\">.*?</span>", "");
        context = context.replaceAll("(<strong>\\D*?(\\d+).*?</strong>)", "<span class=\"pb-verse\" title=\"$2\"><a id=\"" + versename + "-$2\"></a><sup>$1</sup></span>");
        context = context.replaceAll("<sup(.*?>\\D*?(\\d+).*?)</sup>", "<span class=\"pb-verse\" title=\"$2\"><a id=\"" + versename + "-$2\"></a><sup><strong$1</strong></sup></span>");
        if (Locale.getDefault().equals(Locale.SIMPLIFIED_CHINESE)) {
            context = context.replaceAll("「", "“").replaceAll("」", "”");
            context = context.replaceAll("『", "‘").replaceAll("』", "’");
        }

        Log.d(TAG, "will update fontsize " + fontsize + ", scale: " + scale + ", onzoom: " + onzoom);
        if (onzoom) {
            onzoom = false;
        } else {
            fontsize = (int)(fontsize * scale);
        }
        if (fontsize > FONTSIZE_MAX) {
            fontsize = FONTSIZE_MED;
        }

        String body = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n";
        body += "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n";
        body += "<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n";
        body += "<meta name=\"viewport\" content=\"target-densitydpi=device-dpi, width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=2.0\" />\n";
        body += "<style type=\"text/css\">\n";
        if (font.exists()) {
            body += "@font-face { font-family: 'custom'; src: url('" + font.getAbsolutePath() + "'); }\n";
            body += "body {font-family: 'custom', serif; line-height: 1.4em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        } else {
            body += "body {font-family: serif; line-height: 1.4em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        }
        body += ".trans {display: none;}\n";
        body += ".wordsofchrist {color: red;}\n";
        body += "h1 {font-size: 2em;}\n";
        body += "h2 {font-size: 1.5em;}\n";
        body += ".selected {" + background + "}\n";
        body += ".highlight {" + background + "}\n";
        body += "</style>\n";
        body += "<title>" + title + "</title>\n";
        body += "<link rel=\"stylesheet\" type=\"text/css\" href=\"reader.css\"/>\n";
        body += "<script type=\"text/javascript\">\n";
        body += String.format("var verse_start=%s, verse_end=%s, versename=\"%s\", search=\"%s\";", verse.equals("") ? "-1" : verse, end.equals("") ? "-1" : verse, versename, items != null ? search : "");
        verse = "";
        search = "";
        body += "\n</script>\n";
        body += "<script type=\"text/javascript\" src=\"reader.js\"></script>\n";
        body += "</head>\n<body>\n";
        if (!"".equals(title) && bible.getVersion().endsWith("demo")) {
            String link_market = "<a href=\"market://search?q=" + getString(R.string.bibledatalink) + "&c=apps\">market://search?q=" + getString(R.string.bibledatahuman) + "&c=apps</a>";
            body += "<div id=\"pb-demo\">" + getString(R.string.noversion, new Object[] {link_market, link_github}) + "</div>\n";
        }
        body += "<div id=\"content\">\n";
        body += context;
        body += "</div>\n</body>\n</html>\n";
        webview.clearCache(true);
        webview.setInitialScale(100);
        scale = 1.0f;
        webview.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
        dismiss();
        /*
        {
            File path = new File(Environment.getExternalStorageDirectory(), versename + ".html");
            try {
                Log.d("write", path.getAbsolutePath());
                java.io.OutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(path));
                os.write(body.getBytes());
                os.close();
            } catch (Exception e) {
                Log.e("write", path.getAbsolutePath(), e);
            }
        }
        */
    }

    @Override
    public void onPause() {
        showed = false;
        getVerse();
        Log.d(TAG, "onPause");
        storeOsisVersion();
        hasIntentData = false;
        version = "";
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        gridview.setVisibility(View.GONE);
        if (bible.getCount(Bible.TYPE.VERSION) == 0 && bible.getDatabase() == null && v.getId() != R.id.version) {
            return;
        }
        switch (v.getId()) {
            case R.id.version:
                getVerse();
                bible.checkVersions();
            case R.id.book:
            case R.id.chapter:
            case R.id.items:
                showSpinner(v);
                break;
            case R.id.search:
                onSearchRequested();
                break;
            case R.id.share:
                if (!copytext.equals("")) {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, copytext);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.share)));
                }
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
                selected = bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, book));
                if (matt > 0) {
                    for (int id = 0; id < matt; id++) {
                        int right = matt + id;
                        adapter.add(bible.get(Bible.TYPE.HUMAN, id));
                        if (right < bible.getCount(Bible.TYPE.OSIS)) {
                            adapter.add(bible.get(Bible.TYPE.HUMAN, right));
                        } else {
                            adapter.add("");
                        }
                    }
                } else {
                    for (String string: bible.get(Bible.TYPE.HUMAN)) {
                        adapter.add(string);
                    }
                }
                break;
            case R.id.chapter:
                gridview.setNumColumns(5);
                selected = chapter;
                String chapters = bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, book));
                int maxchapter = 1;
                try {
                    maxchapter = Integer.parseInt(chapters);
                } catch (Exception e) {
                }
                for (int i = 1; i <= maxchapter; i++) {
                    adapter.add(String.valueOf(i));
                }
                break;
            case R.id.version:
                gridview.setNumColumns(1);
                Log.d(TAG, "version=" + version);
                selected = bible.getVersionFullname(version);
                for (String string: bible.get(Bible.TYPE.VERSION)) {
                    Log.d(TAG, "add version " + string);
                    adapter.add(bible.getVersionFullname(string));
                }
                adapter.add(getString(R.string.more));
                break;
            case R.id.items:
                gridview.setNumColumns(1);
                Log.d(TAG, "version=" + version);
                for (OsisItem item: items) {
                    adapter.add(formatOsisItem(item));
                }
                adapter.add(getString(R.string.otherbook));
                selected = formatOsisItem(items.get(index));
                break;
        }

        if (adapter.getCount() > 0) {
            gridview.setVisibility(View.VISIBLE);
            gridview.setSelection(adapter.getPosition(selected));
        } else {
            gridview.setVisibility(View.GONE);
            showUri();
        }
    }

    protected String formatOsisItem(OsisItem item) {
        String book = bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, item.book));
        return book + " " + item.chapter + (item.verse.equals("") ? "" : ":" + item.verse) + (item.end.equals("") ? "" : "-" + item.end);
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
                if (pos >= bible.getCount(Bible.TYPE.VERSION)) {
                    String link_market = "<a href=\"market://search?q=" + getString(R.string.bibledatalink) + "&c=apps\">market://search?q=" + getString(R.string.bibledatahuman) + "&c=apps</a>";
                    String body = "<div id=\"pb-demo\">" + getString(R.string.moreversion, new Object[] {link_market, link_github, "<a href=\"mailto:liudongmiao@gmail.com\">Liu DongMiao</a>"}) + "</div>\n";
                    showContent("", body);
                    return;
                }
                version = bible.get(Bible.TYPE.VERSION, pos);
                Log.d(TAG, "version: " + version);
                bible.setVersion(version);
                fontsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("fontsize-" + version, fontsize);
                uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
                showUri();
                break;
            case R.id.items:
                if (items != null && pos < items.size()) {
                    showItem(pos);
                } else {
                    showView(R.id.book, true);
                    showView(R.id.chapter, true);
                    showView(R.id.items, false);
                    items.clear();
                    findViewById(R.id.book).performClick();
                }
                break;
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
        gridview.setVisibility(View.GONE);
        switch (gridviewid) {
        case R.id.version:
            if (bible.getCount(Bible.TYPE.VERSION) == 1) {
                return false;
            }
            if (bible.isDemoVersion(version)) {
                return false;
            }
            if (pos >= bible.getCount(Bible.TYPE.VERSION)) {
                return false;
            }
            if (bible.get(Bible.TYPE.VERSION, pos).equals(bible.getVersion())) {
                return false;
            }
            final String delete = bible.get(Bible.TYPE.VERSION, pos);
            areYouSure(
                    getString(R.string.deleteversion, bible.getVersionName(delete)),
                    getString(R.string.deleteversiondetail, bible.getVersionFullname(delete)),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            bible.deleteVersion(delete);
                            bible.checkVersions();
                            findViewById(R.id.version).performClick();
                        }
                    });
            return true;
        default:
            return false;
        }
    }

    private void areYouSure(String title, String message, DialogInterface.OnClickListener handler) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.yes, handler)
                .setNegativeButton(android.R.string.no, null).create().show();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume, items: " + items);
    }

    private void showData() {
        new Thread(new Runnable() {
            public void run() {
                if (bible == null) {
                    bible = Bible.getBible(getBaseContext());
                }
                bible.checkVersions();
                Log.d(TAG, "will set version: " + version);
                if (!"".equals(version)) {
                    bible.setVersion(version);
                }
                handler.sendEmptyMessage(SHOWDATA);
            }
        }).start();
    }

    private void _showData() {
        version = bible.getVersion();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        fontsize = sp.getInt("fontsize-" + version, 0);
        if (fontsize == 0) {
            fontsize = sp.getInt("fontsize", 16);
        }
        if (fontsize > FONTSIZE_MAX) {
            fontsize = FONTSIZE_MAX;
        }
        if (!osis.equals("")) {
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
        }
        showView(R.id.search, true);
        if (items == null || items.size() == 0) {
            showView(R.id.items, false);
            showView(R.id.book, true);
            showView(R.id.chapter, true);
            showUri();
        } else {
            showView(R.id.items, true);
            showView(R.id.book, false);
            showView(R.id.chapter, false);
            if (this.index > -1 && this.index < items.size()) {
                showItem(this.index);
            } else {
                showItem(0);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private float getScale(WebView webview) {
        return webview.getScale();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e){
        int scrollY = webview.getScrollY();
        if (scrollY == 0 || (float) scrollY >= webview.getContentHeight() * getScale(webview) - webview.getHeight()) {
            setDisplayZoomControls(true);
        } else {
            setDisplayZoomControls(false);
        }

        super.dispatchTouchEvent(e);

        // getActionMasked since api-8
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                scale = getScale(webview);
                break;
        }

        return mGestureDetector.onTouchEvent(e);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(Chapter.this, Search.class));
        return false;
    }

   ZoomButtonsController getZoomButtonsController(WebView webview) {
        Object mZoomManager;
        Object mProvider = Bible.getField(webview, "mProvider");
        // since api-16
        if (mProvider != null) {
            mZoomManager = Bible.getField(mProvider, "mZoomManager");
        } else if ((mZoomManager = Bible.getField(webview, "mZoomManager")) == null) {
            try {
                Method method = webview.getClass().getMethod("getZoomButtonsController");
                return (ZoomButtonsController) method.invoke(webview);
            } catch (Exception e) {
                return null;
            }
        }

        // since api-19
        if (mProvider != null && mZoomManager == null) {
            Object mAwContents = Bible.getField(mProvider, "mAwContents");
            Object mZoomControls = Bible.getField(mAwContents, "mZoomControls");
            try {
                Method getControls = mZoomControls.getClass().getDeclaredMethod("getZoomController");
                getControls.setAccessible(true);
                return (ZoomButtonsController) getControls.invoke(mZoomControls);
            } catch (Exception e) {
                return null;
            }
        }

        try {
            Method getCurrentZoomControl = mZoomManager.getClass().getDeclaredMethod("getCurrentZoomControl");
            getCurrentZoomControl.setAccessible(true);
            Object mEmbeddedZoomControl = getCurrentZoomControl.invoke(mZoomManager);
            Method getControls = mEmbeddedZoomControl.getClass().getDeclaredMethod("getControls");
            getControls.setAccessible(true);
            return (ZoomButtonsController) getControls.invoke(mEmbeddedZoomControl);
        } catch (Exception e) {
            Log.e(TAG, "cannot call " + mZoomManager.getClass().getName() + ".getCurrentZoomControl().getControls()", e);
        }

        return null;
    }

    private class ZoomListener implements ZoomButtonsController.OnZoomListener {

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                handler.sendEmptyMessageDelayed(SHOWZOOM, 0);
            }
        }

        @Override
        public void onZoom(boolean zoomIn) {
            if (fontsize == FONTSIZE_MIN && !zoomIn) {
                return;
            }
            if (fontsize == FONTSIZE_MAX && zoomIn) {
                return;
            }
            fontsize += (zoomIn ? 1 : -1);
            Log.d(TAG, "update fontsize to " + fontsize + ", zoomIn: " + zoomIn);
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
            showUri();
            handler.sendEmptyMessageDelayed(SHOWZOOM, 250);
            onzoom = true;
        }
    }

    private boolean setZoomButtonsController(WebView webview) {
        if (mZoomButtonsController == null) {
            setListener = false;
            mZoomButtonsController = getZoomButtonsController(webview);
            Log.d(TAG, "mZoomButtonsController: " + mZoomButtonsController);
        }

        if (mZoomButtonsController != null) {
            if (!setListener) {
                mZoomButtonsController.setOnZoomListener(new ZoomListener());
                setListener = true;
            }
            mZoomButtonsController.setZoomOutEnabled(fontsize > FONTSIZE_MIN);
            mZoomButtonsController.setZoomInEnabled(fontsize < FONTSIZE_MAX);
            return true;
        }

        return false;
    }

    private void setDisplayZoomControls(boolean enable) {
        if (mZoomButtonsController != null) {
            mZoomButtonsController.getContainer().setVisibility(enable ? View.VISIBLE : View.GONE);
            if (enable) {
                handler.sendEmptyMessageDelayed(SHOWZOOM, 250);
            }
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
                    Log.d(TAG, "swipe right, show next item");
                    showItem(index + 1);
                    return true;
                } else if (e2.getRawX() - e1.getRawX() > DISTANCE) {
                    Log.d(TAG, "swipe right, show prev item");
                    showItem(index - 1);
                    return true;
                }
                return false;
            }
        });
    }

    private void showView(int resId, boolean enable) {
        findViewById(resId).setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    private void checkShare() {
        showView(R.id.share, !copytext.equals(""));
    }

    public void setItemText(int index) {
        if (items != null && index >= 0 && index < items.size()) {
            OsisItem item = items.get(index);
            String book = bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, item.book)) + item.chapter;
            if (!item.verse.equals("") && !item.end.equals("")) {
                book += ":" + item.verse + "-" + item.end;
            } else if (!item.verse.equals("") || !item.end.equals("")) {
                book += ":" + item.verse + item.end;
            }
            ((TextView)findViewById(R.id.items)).setText(book);
        }
    }

    public void showItem(int index) {
        osis = "";
        if (items == null || items.size() < 2) {
            showView(R.id.items, false);
            showView(R.id.book, true);
            showView(R.id.chapter, true);
            if (items == null || index < 0 || index >= items.size()) {
                openOsis(this.index > index ? osis_prev : osis_next);
            } else {
                OsisItem item = items.get(0);
                if (item.chapter.equals("")) {
                    item.chapter = PreferenceManager.getDefaultSharedPreferences(this).getString(item.book, "1");
                }
                Log.d(TAG, "item.book: " + item.book + ", item.chapter: " + item.chapter);
                openOsis(item.book + "." + item.chapter, item.verse, item.end);
            }
        } else if (index >= 0 && index < items.size()) {
            showView(R.id.items, true);
            showView(R.id.book, false);
            showView(R.id.chapter, false);
            this.index = index;
            setItemText(index);
            OsisItem item = items.get(index);
            Log.d(TAG, String.format("book: %s, chapter: %s, verse: %s, end: %s", item.book, item.chapter, item.verse, item.end));
            if (item.chapter.equals("")) {
                item.chapter = PreferenceManager.getDefaultSharedPreferences(this).getString(item.book, "1");
            }
            openOsis(item.book + "." + item.chapter, item.verse, item.end);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        getVerse();
        if (items != null && items.size() > 0) {
            bundle.putInt("index", index);
            bundle.putParcelableArrayList("osiss", items);
        }
        bundle.putString("verse", verse);
        Log.d(TAG, "onSaveInstanceState, save index: " + index + ", verse: " + verse);
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        if (items == null || items.size() == 0) {
            items = bundle.getParcelableArrayList("osiss");
            index = bundle.getInt("index");
        }
        verse = bundle.getString("verse");
        Log.d(TAG, "onRestoreInstanceState, restore index: " + index + ", verse: " + verse);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        setIntentData();
        hasIntentData = true;
    }

    private void setIntentData() {
        Intent intent = getIntent();
        if (null != intent.getStringExtra("version")) {
            version = intent.getStringExtra("version");
        }
        if (null != intent.getStringExtra("search")) {
            search = intent.getStringExtra("search");
        }
        if (null != intent.getParcelableArrayListExtra("osiss")) {
            index = 0;
            items = intent.getParcelableArrayListExtra("osiss");
        }
    }

    @Override
    public void onBackPressed() {
        if (gridview.getVisibility() != View.GONE) {
            gridview.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }

    private volatile boolean progress = true;

    private void show() {
        progress = true;
        /*
         * http://en.wikipedia.org/wiki/Frame_rate
         *
         * while single-millisecond visual stimulus may have a perceived duration
         * between 100ms and 400ms due to persistence of vision in the visual cortex.
         */
        handler.sendEmptyMessageDelayed(SHOWBAR, showed ? 250 : 0);
    }

    private void _show() {
        if (progress) {
            showView(R.id.header, showed);
            showView(R.id.progress, true);
            showView(R.id.webview, false);
        }
    }

    private void dismiss() {
        handler.sendEmptyMessage(DISMISSBAR);
        progress = false;
    }

    private void _dismiss() {
        showView(R.id.header, true);
        showView(R.id.progress, false);
        showView(R.id.webview, true);
    }

}
