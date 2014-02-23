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

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ZoomButtonsController;

public class Chapter extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private final String TAG = "me.piebridge.bible$Chapter";

    private int index = 0;
    private String[] chapters = null;
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
    private boolean changeVersion = false;

    private String osis_next;
    private String osis_prev;

    private boolean setListener = false;
    public static int FONTSIZE_MIN = 1;
    public static int FONTSIZE_MED = 15;
    public static int FONTSIZE_MAX = 72;
    private int fontsize = FONTSIZE_MED;

    private ZoomButtonsController mZoomButtonsController = null;

    private GridView gridview;
    private TextView bibledata;
    private EditText addnote;
    private WebView webview;
    private View header;
    private ArrayAdapter<String> adapter;

    private final int DISTANCE = 100;
    private GestureDetector mGestureDetector = null;

    private static Bible bible = null;
    private String selected = "";
    private String versename = "";
    private File font;
    private int gridviewid = 0;
    private float scale;
    private float defaultScale;
    protected String copytext = "";
    protected static final int COPYTEXT = 0;
    protected static final int SHOWCONTENT = 1;
    protected static final int SHOWDATA = 2;
    protected static final int SHOWBAR = 3;
    protected static final int DISMISSBAR = 4;
    protected static final int SHOWZOOM = 5;
    protected static final int CHECKBIBLEDATA = 6;
    protected static final int CHECKVIEW = 8;
    protected static final int SHOWHEAD = 9;
    protected static final int HIDEGRID = 10;
    protected static final int SETSELECTED = 11;
    protected static final int SYNCED = 12;
    protected static final int SHOWANNOTATION = 13;

    private boolean red = true;
    private boolean xlink = true;
    private boolean flink = true;
    private boolean nightmode = false;
    private boolean justify = false;
    private boolean pinch = false;
    private boolean hasIntentData = false;
    private String body;

    private final int MENU_SEARCH = 0;
    private final int MENU_SETTINGS = 4;
    private final int MENU_FEEDBACK = 3;
    private final int MENU_VERSIONS = 2;
    private final int MENU_ANNOTATION = 1;

    private static boolean refresh = false;
    private static boolean noback = true;
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String[] message;
            switch (msg.what) {
                case COPYTEXT:
                    boolean selected = (Boolean) msg.obj;
                    if (!"".equals(copytext)) {
                        showSharing(true);
                    }
                    header.findViewById(R.id.bookmark).setSelected(selected);
                    break;
                case SHOWCONTENT:
                    bibledata.setVisibility(View.INVISIBLE);
                    message = (String[]) msg.obj;
                    if (!"".equals(message[0])) {
                        setBookChapter(message);
                    }
                    break;
                case SHOWDATA:
                    bibledata.setText("");
                    showData();
                    break;
                case SHOWBAR:
                    if (progress) {
                        showView(R.id.progress, true);
                        showView(R.id.webview, false);
                    }
                    break;
                case DISMISSBAR:
                    progress = false;
                    showHeader(true);
                    showView(R.id.progress, false);
                    showView(R.id.webview, true);
                    break;
                case SHOWZOOM:
                    if (mZoomButtonsController != null) {
                        mZoomButtonsController.setZoomOutEnabled(fontsize > FONTSIZE_MIN);
                        mZoomButtonsController.setZoomInEnabled(fontsize < FONTSIZE_MAX);
                    }
                    break;
                case CHECKBIBLEDATA:
                    showHeader(false);
                    bibledata.setText(R.string.bibledata);
                    bibledata.setVisibility(View.VISIBLE);
                    break;
                case CHECKVIEW:
                    message = (String[]) msg.obj;
                    showContent(message[0], message[1]);
                    View view = header.findViewById(R.id.extra);
                    if (header.getWidth() == 0) {
                        setBookChapter(message);
                    } else {
                        if (view.getWidth() <= 0) {
                            view.setVisibility(View.GONE);
                        }
                        copytext = "";
                        showSharing(false);
                    }
                    break;
                case SHOWHEAD:
                    showSharing(true);
                    break;
                case HIDEGRID:
                    if (gridview.getVisibility() == View.VISIBLE) {
                        gridview.setVisibility(View.GONE);
                    }
                    break;
                case SETSELECTED:
                    String text = (String) msg.obj;
                    TextView selectedView = (TextView) header.findViewById(R.id.selected);
                    if (text == null || text.length() == 0) {
                        selectedView.setVisibility(View.INVISIBLE);
                    } else {
                        selectedView.setVisibility(View.VISIBLE);
                        selectedView.setText(text);
                    }
                    break;
                case SYNCED:
                    Toast.makeText(Chapter.this, R.string.versionsynced, Toast.LENGTH_SHORT).show();
                    break;
                case SHOWANNOTATION:
                    String[] link_annotation = (String[]) msg.obj;
                    showAnnotation(link_annotation[0], link_annotation[1]);
                    break;
            }
            return false;
        }
    });

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void hideActionBar() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
            getActionBar().hide();
        }
    }

    protected void showAnnotation(String link, String annotation) {
        String title = link;
        if (link.contains("!f.") || link.startsWith("f")) {
            title = getString(R.string.flink);
        } else if (link.contains("!x.") || link.startsWith("c")) {
            title = getString(R.string.xlink);
        }
        // <span verse_id="Sir.2.1" id="Sir.2.1!f.1" class="note f"><span class="fr">2:1–11</span><span class="ft">Serving the Lord is not without its trials (v. 1); but no matter what happens, the genuine believer will remain sincere, steadfast, and faithful (vv. 2–3). Misfortune and humiliation are means of purification to prove one’s worth (vv. 4–5). Ben Sira believed that patience and unwavering trust in God are ultimately rewarded with the benefits of God’s mercy and of lasting joy (vv. 6–11).</span></span>
        annotation = annotation.replaceAll("<span class=\"fr\">(.*?)</span>", "<strong>$1&nbsp;</strong>");
        AlertDialog dialog = new AlertDialog.Builder(Chapter.this).setTitle(title)
                .setMessage(Html.fromHtml(annotation)).setPositiveButton(android.R.string.ok, null).show();
        ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // on MB612, the search and share icon is so dark ...
        if (Build.MODEL.equals("MB612")) {
            setTheme(android.R.style.Theme_Light_NoTitleBar);
        }
        super.onCreate(savedInstanceState);
        hideActionBar();
        setContentView(R.layout.chapter);
        header = getHeader();
        webview = (WebView) findViewById(R.id.webview);
        gridview = (GridView) findViewById(R.id.gridview);
        bibledata = (TextView) findViewById(R.id.bibledata);
        addnote = (EditText) findViewById(R.id.addnote);
        show();

        header.findViewById(R.id.book).setOnClickListener(this);
        header.findViewById(R.id.chapter).setOnClickListener(this);
        header.findViewById(R.id.version).setOnClickListener(this);
        header.findViewById(R.id.items).setOnClickListener(this);
        header.findViewById(R.id.share).setOnClickListener(this);
        header.findViewById(R.id.bookmark).setOnClickListener(this);
        header.findViewById(R.id.note).setOnClickListener(this);
        header.findViewById(R.id.back).setOnClickListener(this);
        header.findViewById(R.id.selected).setOnClickListener(this);
        findViewById(R.id.savenote).setOnClickListener(this);

        final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        adapter = new ArrayAdapter<String>(this, R.layout.grid) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                Integer column = getNumColumns(gridview);
                if (convertView != null && column != null && column.equals(convertView.getTag())) {
                    view = convertView;
                } else {
                    view = inflater.inflate(R.layout.grid, parent, false);
                    view.setTag(column);
                }
                ToggleButton grid = (ToggleButton) view.findViewById(R.id.text1);
                if (gridviewid == R.id.book) {
                    String[] values = getItem(position).split(splitter);
                    if (values.length > 1) {
                        grid.setTextOn(values[1]);
                        grid.setTextOff(values[1]);
                    } else {
                        grid.setTextOn("");
                        grid.setTextOff("");
                    }
                } else {
                    grid.setTextOn(getItem(position));
                    grid.setTextOff(getItem(position));
                }
                grid.setChecked(getItem(position).equals(selected));
                grid.setVisibility(getItem(position).equals("") ? View.INVISIBLE : View.VISIBLE);
                return view;
            }

            @SuppressLint("NewApi")
            private Integer getNumColumns(GridView gridview) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
                    return gridview.getNumColumns();
                } else {
                    return (Integer) Bible.getField(gridview, "mNumColumns");
                }
            }
        };

        gridview.setAdapter(adapter);
        gridview.setVisibility(View.GONE);
        gridview.setOnItemClickListener(this);

        setGestureDetector();
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        // http://stackoverflow.com/questions/3031481#answer-4873626
        webview.setFocusableInTouchMode(false);
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
                boolean selected = false;
                if (!text.equals("")) {
                    String[] fields = text.split("\n");
                    try {
                        if (Integer.parseInt(fields[0]) > 0) {
                            selected = true;
                        }
                    } catch (NumberFormatException e) {
                    }
                    selectverse = fields[1];
                    String content = fields[2];
                    text = selectverse + " " + content;
                    try {
                        if (Bible.isCJK(content.trim().substring(0, 4))) {
                            text = text.replace(" ", "");
                        } else {
                            text = text.replaceAll(" +", " ");
                        }
                    } catch (Exception e) {
                    }
                    copytext = bible.getVersionFullname(version).replace("(" + getString(R.string.demo) + ")", "") + " ";
                    copytext += bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, book)) + " " + chapter + ":" + text;
                    ((android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(copytext);
                    Log.d(TAG, "copy from javascript: " + copytext);
                    // setHighlight(osis, text.split("\n")[0]);
                } else {
                    copytext = "";
                    selectverse = "";
                    if (!"".equals(highlighted)) {
                        selected = true;
                    }
                    // setHighlight(osis, "");
                }
                handler.sendMessage(handler.obtainMessage(SETSELECTED, selectverse));
                handler.sendMessage(handler.obtainMessage(COPYTEXT, selected));
            }

            @JavascriptInterface
            public void setHighlighted(String text) {
                // some translation has no some verses
                highlighted = text;
            }

            @JavascriptInterface
            public void showAnnotation(String link) {
                android.util.Log.d(TAG, "link: " + link);
                String annotation = bible.getAnnotation(link);
                if (annotation != null) {
                    handler.sendMessage(handler.obtainMessage(SHOWANNOTATION, new String[] {link, annotation}));
                }
            }
        }, "android");
        webview.getSettings().setBuiltInZoomControls(true);
        if (!setZoomButtonsController(webview)) {
            webview.getSettings().setBuiltInZoomControls(false);
        }

        osis = PreferenceManager.getDefaultSharedPreferences(this).getString("osis", "null");
        uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();

        setIntentData();
        Log.d(TAG, "onCreate");
        hasIntentData = true;
        font = new File(new File(new File(new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data"), getPackageName()), "files"), "custom.ttf");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!hasIntentData) {
            verse = sp.getString("verse", "");
        }
        refresh = true;
        showSharing(!copytext.equals(""));
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                // possible slow IO
                showUriBackground();
            }
        }).start();
    }

    private void showUriBackground() {
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
            handler.sendMessage(handler.obtainMessage(SHOWCONTENT, new String[] {"", getString(R.string.queryerror) }));
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

            bible.loadAnnotations(osis, xlink || flink);

            handler.sendMessage(handler.obtainMessage(SHOWCONTENT, new String[] {human + " | " + version, content}));
        } else {
            Log.d(TAG, "no such chapter, try first chapter");
            Uri nulluri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(null).fragment(version).build();
            if (!nulluri.equals(uri)) {
                uri = nulluri;
                showUri();
            } else {
                handler.sendMessage(handler.obtainMessage(SHOWCONTENT, new String[] {"", getString(R.string.queryerror) }));
            }
        }
    }

    private boolean openOsis(String newOsis) {
        return openOsis(newOsis, "", "");
    }

    private boolean openOsis(String newOsis, String verse, String end) {
        if (bible == null) {
            return false;
        }
        if (newOsis == null || newOsis.equals("")) {
            if (bible.isDemoVersion(bible.getVersion())) {
                showMoreVersion();
            }
            return false;
        }
        if (!osis.equals(newOsis)) {
            selectverse = "";
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(newOsis).build();
            if ("".equals(this.verse)) {
                this.verse = verse;
            }
            this.end = end;
            changeVersion = false;
            showUri();
        }
        return true;
    }

    private void setBookChapter(String[] message) {
        showHeader(true);
        book = osis.split("\\.")[0];
        if (osis.split("\\.").length > 1) {
            chapter = osis.split("\\.")[1];
            if ("int".equalsIgnoreCase(chapter)) {
                chapter = getString(R.string.intro);
            }
        } else {
            chapter = "0";
        }
        Log.d(TAG, "set book chapter, osis: " + osis);

        setItemText(this.index);
        header.findViewById(R.id.extra).setVisibility(View.VISIBLE);
        ((TextView)header.findViewById(R.id.version)).setText(bible.getVersionName(bible.getVersion()));
        ((TextView)header.findViewById(R.id.book)).setText(bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, book)));
        if (changeVersion) {
            // don't change chapter
        } else if (!"".equals(verse) && !"".equals(end)) {
            ((TextView)header.findViewById(R.id.chapter)).setText(chapter + ":" + verse + "-" + end);
        } else if (!"".equals(verse) || !"".equals(end)) {
            ((TextView)header.findViewById(R.id.chapter)).setText(chapter + ":" + verse + end);
        } else {
            ((TextView)header.findViewById(R.id.chapter)).setText(chapter);
        }
        handler.sendMessageDelayed(handler.obtainMessage(CHECKVIEW, message), 40);
    }

    private void storeOsisVersion() {
        saveHighlight();
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("osis", osis);
        if (!version.endsWith("demo") && !version.equals("")) {
            editor.putString("version", version);
        }
        editor.putString("verse", verse);
        editor.putInt(Settings.FONTSIZE, fontsize);
        if (!book.equals("") && !chapter.equals("")) {
            editor.putString(book, chapter);
        }
        if (!version.equals("")) {
            editor.putInt("fontsize-" + version, fontsize);
        }
        if (!Log.on) {
            editor.remove("log");
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
        context = context.replaceAll("<strong>([^\\d<>]*?(\\d+).*?)</strong>", "<span class=\"pb-verse\" title=\"$2\"><a id=\"" + versename + "-$2\"></a><sup>$1</sup></span>");
        // for bibles.org
        context = context.replaceAll("(<sup.*?>[^\\d<>]*?(\\d+).*?</sup>)", "<span class=\"pb-verse\" title=\"$2\"><a id=\"" + versename + "-$2\"></a>$1</span>");
        // context = context.replaceAll("(<a.*?class=[\"'][^\"']*?notelink.*?>.*?</a>)", "<sup>$1</sup>");
        if (Locale.getDefault().equals(Locale.SIMPLIFIED_CHINESE) || "CCB".equalsIgnoreCase(bible.getVersion()) || bible.getVersion().endsWith("ss")) {
            context = context.replaceAll("「", "“").replaceAll("」", "”");
            context = context.replaceAll("『", "‘").replaceAll("』", "’");
        }

        Log.d(TAG, "will update fontsize " + fontsize + ", scale: " + scale + ", defaultScale: " + defaultScale);
        if (pinch && defaultScale != 0.0f) {
            fontsize = (int)(fontsize * scale / defaultScale);
        }
        if (fontsize < FONTSIZE_MIN) {
            fontsize = FONTSIZE_MIN;
        }
        if (fontsize > FONTSIZE_MAX) {
            fontsize = FONTSIZE_MAX;
        }

        body = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n";
        body += "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n";
        body += "<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n";
        body += "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=5.0\" />\n";
        body += "<style type=\"text/css\">\n";
        if (font.exists()) {
            body += "@font-face { font-family: 'custom'; src: url('" + font.getAbsolutePath() + "'); }\n";
            body += "body {font-family: 'custom', serif; line-height: 1.4em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        } else {
            body += "body {font-family: serif; line-height: 1.4em; font-weight: 100; font-size: " + fontsize + "pt;}\n";
        }
        if (nightmode) {
            body += "body { background-color: black; color: white; }\n";
        } else {
            body += "body { background-color: white; color: black; }\n";
        }
        if (justify) {
            body += "body { text-align: justify; }\n";
            body += "body { text-justify: distribute }\n";
        }
        body += ".trans {display: none;}\n";
        if (red) {
            body += ".wordsofchrist, .woj, .wj {color: red;}\n";
        }
        if (!flink) {
            body += "a.f-link {display: none}\n";
        }
        if (!xlink) {
            body += "a.x-link {display: none}\n";
        }
        body += "h1 {font-size: 2em;}\n";
        body += "h2 {font-size: 1.5em;}\n";
        body += bible.getCSS();
        body += "</style>\n";
        body += "<title>" + title + "</title>\n";
        body += "<link rel=\"stylesheet\" type=\"text/css\" href=\"reader.css\"/>\n";
        body += "<script type=\"text/javascript\">\n";
        highlighted = null;
        body += String.format("var verse_start=%s, verse_end=%s, versename=\"%s\", search=\"%s\", selected=\"%s\"; highlighted=\"%s\";",
                verse.equals("") ? "-1" : verse, end.equals("") ? "-1" : verse, versename, items != null ? search : "",
                selectverse, getHighlight(osis));
        body += "\n</script>\n";
        body += "<script type=\"text/javascript\" src=\"reader.js\"></script>\n";
        body += "</head>\n<body>\n";
        if (!"".equals(title) && bible.getVersion().endsWith("demo")) {
            body += "<div id=\"pb-demo\">" + getString(R.string.noversion) + "</div>\n";
        }
        body += "<div id=\"content\">\n";
        body += context;
        body += "</div>\n</body>\n</html>\n";
        // webview.clearCache(true);
        // webview.setInitialScale(100);
        if (defaultScale == 0.0f) {
            defaultScale = getScale(webview);
        }
        scale = defaultScale;
        webview.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
        handler.sendEmptyMessage(DISMISSBAR);
        if ("".equals(verse)) {
            setDisplayZoomControls(true);
        }
        verse = "";
        search = "";
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
        getVerse();
        Log.d(TAG, "onPause");
        storeOsisVersion();
        hasIntentData = false;
        version = "";
        if (bible != null) {
            bible.getSynced(null);
        }
        super.onPause();
    }

    @Override
    public void onClick(final View v) {
        gridview.setVisibility(View.GONE);
        if (bible.getCount(Bible.TYPE.VERSION) == 0 && bible.getDatabase() == null && v.getId() != R.id.version) {
            return;
        }
        switch (v.getId()) {
            case R.id.version:
                if (!getSynced()) {
                    return;
                }
                getVerse();
                // bible.checkVersions();
            case R.id.book:
            case R.id.chapter:
            case R.id.items:
                showSpinner(v);
                break;
            case R.id.share:
                if (!copytext.equals("")) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, copytext);
                    intent.setType("text/plain");
                    try {
                        startActivity(Intent.createChooser(intent, getString(R.string.share)));
                    } catch (ActivityNotFoundException e) {
                    }
                }
                break;
            case R.id.back:
                copytext = "";
                showSharing(false);
                break;
            case R.id.selected:
                if (!"".equals(selectverse)) {
                    webview.loadUrl("javascript:select('" + selectverse + "', false);");
                    selectverse = "";
                    handler.sendMessage(handler.obtainMessage(SETSELECTED, selectverse));
                }
                break;
            case R.id.bookmark:
                if (v.isSelected()) {
                    String unhighlight;
                    if ("".equals(selectverse)) {
                        unhighlight = highlighted;
                    } else {
                        unhighlight = selectverse;
                    }
                    areYouSure(getString(R.string.deletehighlight, unhighlight), null,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                copytext = "";
                                v.setSelected(false);
                                if ("".equals(selectverse)) {
                                    webview.loadUrl("javascript:highlight('" + highlighted + "', false);");
                                } else {
                                    webview.loadUrl("javascript:highlight('" + selectverse + "', false);");
                                }
                            }
                        });
                } else {
                    if (!"".equals(selectverse)) {
                        v.setSelected(true);
                        webview.loadUrl("javascript:highlight('" + selectverse + "');");
                        webview.loadUrl("javascript:select('" + selectverse + "', false);");
                        selectverse = "";
                        handler.sendMessage(handler.obtainMessage(SETSELECTED, selectverse));
                    }
                }
                break;
            case R.id.note:
                showView(R.id.annotation, true);
                addnote.setText(getNote());
                break;
            case R.id.savenote:
                String note = addnote.getText().toString();
                showView(R.id.annotation, false);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                saveNote(note);
                break;
            default:
                break;
        }
    }

    private String getNote() {
        return "";
    }

    private void saveNote(String note) {
        // TODO
    }

    private int getMatt() {
        int matt = bible.getPosition(Bible.TYPE.OSIS, "Matt");
        if (matt > 0 && matt * 2 > bible.getCount(Bible.TYPE.OSIS)) {
                return matt;
        } else {
                return -1;
        }
    }

    private static final String splitter = "!";
    private String getBook(int pos) {
        if (pos == -1) {
            return "";
        } else {
            return bible.get(Bible.TYPE.OSIS, pos) + splitter + bible.get(Bible.TYPE.HUMAN, pos);
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
                selected = getBook(bible.getPosition(Bible.TYPE.OSIS, book));
                if (matt > 0) {
                    for (int id = 0; id < matt; id++) {
                        int right = matt + id;
                        adapter.add(getBook(id));
                        if (right < bible.getCount(Bible.TYPE.OSIS)) {
                            adapter.add(getBook(right));
                        } else {
                            adapter.add(getBook(-1));
                        }
                    }
                } else {
                    for (int id = 0; id < bible.getCount(Bible.TYPE.OSIS); ++id) {
                        adapter.add(getBook(id));
                    }
                }
                break;
            case R.id.chapter:
                gridview.setNumColumns(5);
                selected = chapter;
                chapters = bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, book)).split(",");
                for (String chapter : chapters) {
                    if ("int".equalsIgnoreCase(chapter)) {
                        adapter.add(getString(R.string.intro));
                    } else {
                        adapter.add(chapter);
                    }
                }
                break;
            case R.id.version:
                gridview.setNumColumns(1);
                version = bible.getVersion();
                Log.d(TAG, "version=" + version);
                selected = bible.getVersionFullname(version);
                for (String string: bible.get(Bible.TYPE.VERSION)) {
                    Log.d(TAG, "add version " + string);
                    adapter.add(bible.getVersionFullname(string));
                }
                adapter.add(getString(R.string.manageversion));
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
        // FIXME: replace item.chapter
        return book + " " + item.chapter + (item.verse.equals("") ? "" : ":" + item.verse) + (item.end.equals("") ? "" : "-" + item.end);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, final int pos, final long id) {
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
                if (chapters != null && pos < chapters.length) {
                    openOsis(String.format("%s.%s", book, chapters[pos]));
                }
                break;
            case R.id.version:
                show();
                storeOsisVersion();
                if (pos >= bible.getCount(Bible.TYPE.VERSION)) {
                    showMoreVersion();
                } else {
                    final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                    version = bible.get(Bible.TYPE.VERSION, pos);
                    Log.d(TAG, "version: " + version);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            bible.setVersion(version);
                            fontsize = sp.getInt("fontsize-" + version, fontsize);
                            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
                            changeVersion = true;
                            showUri();
                        }
                    }).start();
                }
                break;
            case R.id.items:
                selectverse = "";
                if (items != null && pos < items.size()) {
                    showItem(pos);
                } else {
                    showView(R.id.book, true);
                    showView(R.id.chapter, true);
                    showView(R.id.items, false);
                    items.clear();
                    header.findViewById(R.id.book).performClick();
                }
                break;
        }
    }

    private void areYouSure(String title, String message, DialogInterface.OnClickListener handler) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.yes, handler)
                .setNegativeButton(android.R.string.no, null).create().show();
    }

    private void resume() {
        String wanted = "";
        String current = ((TextView) header.findViewById(R.id.version)).getText().toString();
        if (bible != null) {
            wanted = bible.getVersionName(bible.getVersion());
        }
        if (refresh || !wanted.equals(current)) {
            refresh = false;
            handler.sendEmptyMessage(CHECKBIBLEDATA);
            if (bible == null) {
                bible = Bible.getBible(getBaseContext());
            } else {
                bible.checkLocale();
                bible.checkBibleData(false, null);
            }
            Log.d(TAG, "will set version: " + version);
            if ("".equals(version)) {
                version = bible.getVersion();
            }
            if (version.endsWith("demo")) {
                bible.setDefaultVersion();
            }
            handler.sendEmptyMessage(SHOWDATA);
        }
        handler.sendEmptyMessage(DISMISSBAR);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume, items: " + items);
        new Thread(new Runnable() {
            @Override
            public void run() {
                resume();
            }
        }).start();
    }

    private void showData() {
        version = bible.getVersion();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        red = sp.getBoolean(Settings.RED, true);
        flink = sp.getBoolean(Settings.FLINK, true);
        xlink = sp.getBoolean(Settings.XLINK, true);
        nightmode = sp.getBoolean(Settings.NIGHTMODE, false);
        justify = sp.getBoolean(Settings.JUSTIFY, true);
        pinch = sp.getBoolean(Settings.PINCH, true);
        fontsize = sp.getInt(Settings.FONTSIZE + "-" + version, 0);
        if (fontsize == 0) {
            fontsize = sp.getInt(Settings.FONTSIZE, FONTSIZE_MED);
        }
        if (fontsize > FONTSIZE_MAX) {
            fontsize = FONTSIZE_MAX;
        }
        if (!osis.equals("")) {
            uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
        }
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
        if (scrollY <= 0) {
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

    boolean getSynced() {
        boolean synced = bible.getSynced(new Runnable() {
            @Override
            public void run() {
                handler.sendEmptyMessage(SYNCED);
            }
        });
        if (!synced) {
            Toast.makeText(this, R.string.versionsyncing, Toast.LENGTH_SHORT).show();
        }
        return synced;
    }
    @Override
    public boolean onSearchRequested() {
        if (!getSynced()) {
            return true;
        }
        startActivity(new Intent(this, Search.class));
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

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!isInside(R.id.items, e)
                    && !isInside(R.id.book, e)
                    && !isInside(R.id.chapter, e)
                    && !isInside(R.id.version, e)) {
                    handler.sendEmptyMessage(HIDEGRID);
                }
                return false;
            }
        });
    }

    private boolean isInside(int viewId, MotionEvent e) {
        float rawX = e.getRawX();
        float rawY = e.getRawY();
        int[] position = new int[2];
        View view = header.findViewById(viewId);
        view.getLocationOnScreen(position);
        if (rawX < position[0] || rawX > position[0] + view.getWidth() ||
            rawY < position[1] || rawY > position[1] + view.getHeight()) {
            return false;
        }
        return true;
    }

    private void showView(int resId, boolean enable) {
        findViewById(resId).setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    private void showSharing(boolean show) {
        if (!show) {
            header.findViewById(R.id.reading).setVisibility(View.VISIBLE);
            header.findViewById(R.id.annotation).setVisibility(View.GONE);
        } else {
            header.findViewById(R.id.annotation).setVisibility(View.VISIBLE);
            header.findViewById(R.id.reading).setVisibility(View.GONE);
            if (!"".equals(highlighted)) {
                header.findViewById(R.id.bookmark).setSelected(true);
            } else {
                header.findViewById(R.id.bookmark).setSelected(false);
            }
        }

        updateOptionsMenu();
    }

    public void setItemText(int index) {
        if (items != null && index >= 0 && index < items.size()) {
            OsisItem item = items.get(index);
            String book = bible.get(Bible.TYPE.BOOK, bible.getPosition(Bible.TYPE.OSIS, item.book));
            if (!Bible.isCJK(book)) {
                book += " ";
            }
            book += item.chapter;
            if (!item.verse.equals("") && !item.end.equals("")) {
                book += ":" + item.verse + "-" + item.end;
            } else if (!item.verse.equals("") || !item.end.equals("")) {
                book += ":" + item.verse + item.end;
            }
            ((TextView)header.findViewById(R.id.items)).setText(book);
            header.findViewById(R.id.extra).setVisibility(View.GONE);
        }
    }

    public void showItem(int index) {
        selectverse = "";
        saveHighlight();
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
        // shouldn't happen in normal launchMode
        // refresh = true;
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
        } else if (header.findViewById(R.id.annotation).getVisibility() != View.GONE) {
            copytext = "";
            showSharing(false);
        } else {
            setRefresh(true);
            if (noback) {
                noback = false;
                Toast.makeText(this, R.string.noback, Toast.LENGTH_LONG).show();
            }
            super.onBackPressed();
        }
    }

    private volatile boolean progress = true;

    private void show() {
        progress = true;
        handler.sendEmptyMessageDelayed(SHOWBAR, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, MENU_SEARCH, MENU_SEARCH, android.R.string.search_go).setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, MENU_SETTINGS, MENU_SETTINGS, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(Menu.NONE, MENU_FEEDBACK, MENU_FEEDBACK, R.string.help).setIcon(android.R.drawable.ic_menu_help);
        menu.add(Menu.NONE, MENU_VERSIONS, MENU_VERSIONS, R.string.manageversion).setIcon(android.R.drawable.ic_menu_more);
        menu.add(Menu.NONE, MENU_ANNOTATION, MENU_ANNOTATION, R.string.annotation).setIcon(R.drawable.ic_menu_share);
        setupMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case MENU_SEARCH:
                onSearchRequested();
                break;
            case MENU_SETTINGS:
                storeOsisVersion();
                intent = new Intent(this, Settings.class);
                intent.putExtra("body", body);
                startActivityIfNeeded(intent, -1);
                break;
            case MENU_VERSIONS:
                showMoreVersion();
                break;
            case MENU_FEEDBACK:
                bible.email(this);
                break;
            case MENU_ANNOTATION:
                handler.sendEmptyMessage(SHOWHEAD);
                break;
        }
        return false;
    }

    private void showMoreVersion() {
        startActivity(new Intent(this, Versions.class));
    }

    public static void setRefresh(boolean refreshing) {
        refresh = refreshing;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private View getHeader() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return findViewById(R.id.header);
        }
        ActionBar actionBar = getActionBar();
        if (actionBar == null) {
            return null;
        }
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.header);
        return actionBar.getCustomView();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupMenu(Menu menu) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return;
        }
        boolean annotation = header.findViewById(R.id.annotation).getVisibility() == View.VISIBLE;
        if (annotation) {
            menu.clear();
            return;
        }
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == MENU_SEARCH && !annotation) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            } else {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void showHeader(boolean show) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            header.setVisibility(show ? View.VISIBLE : View.GONE);
        } else {
            ActionBar actionBar = getActionBar();
            if (show) {
                actionBar.show();
            } else {
                actionBar.hide();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void updateOptionsMenu() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            invalidateOptionsMenu();
        }
    }

    public static final String HIGHLIGHT = "highlight";

    String highlighted = null;
    String selectverse = "";

    @SuppressLint("InlinedApi")
    private String getHighlight(String osis) {
        if (highlighted == null) {
            SharedPreferences sp = getSharedPreferences(HIGHLIGHT, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
            highlighted = sp.getString(osis, "");
        }
        return highlighted;
    }

    @SuppressLint("InlinedApi")
    private void saveHighlight() {
        final Editor editor = getSharedPreferences(HIGHLIGHT, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).edit();
        if ("".equals(highlighted)) {
            editor.remove(osis);
        } else {
            editor.putString(osis, highlighted);
        }
        editor.commit();
    }

}
