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

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;

import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

public class Result extends Activity
{
    private final String TAG = "me.piebridge.bible$Search";

    private TextView textView = null;
    private ListView listView = null;;

    private String humanfrom;
    private String humanto;
    private String version = null;
    private String query = null;
    private Bible bible;
    private SimpleCursorAdapter adapter = null;

    protected int color;
    BackgroundColorSpan background;
    protected final static int SHOWRESULT = 1;
    protected final static int DIALOG = 1;
    ProgressDialog dialog = null;

    static class BibleHandler extends Handler {
        WeakReference<Result> outerClass;

        BibleHandler(Result activity) {
            outerClass = new WeakReference<Result>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Result theClass = outerClass.get();
            if (theClass == null) {
                return;
            }
            switch (msg.what) {
                case SHOWRESULT:
                    theClass.showResults((Cursor) msg.obj);
                    break;
            }
        }
    }

    private BibleHandler handler = new BibleHandler(this);

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result);
        showDialog(DIALOG);
        Intent intent = getIntent();
        bible = Bible.getBible(getBaseContext());
        version = bible.getVersion();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            String osisfrom = intent.getStringExtra("osisfrom");
            String osisto = intent.getStringExtra("osisto");
            Log.d(TAG, "query: " + query + ", osisfrom: " + osisfrom + ", osisto: " + osisto);
            Integer mHighlightColor = (Integer) Bible.getField(findViewById(R.id.text), TextView.class, "mHighlightColor");
            if (mHighlightColor != null) {
                color = mHighlightColor.intValue();
            } else {
                color = 0x6633B5E5;
            }
            background = new BackgroundColorSpan(color);
            textView = (TextView) findViewById(R.id.text);
            listView = (ListView) findViewById(R.id.list);
            if (version == null) {
                textView.setText(R.string.noversion);
            } else {
                final String books = getQueryBooks(osisfrom, osisto);
                new Thread(new Runnable() {
                    public void run() {
                        doSearch(query, books);
                    }
                }).start();
            }
        } else {
            finish();
        }
    }

    @Override
    public void onResume() {
        version = bible.getVersion();
        super.onResume();
    }

    private void doSearch(String query, String books) {

        Log.d(TAG, "search \"" + query + "\" in version \"" + version + "\"");

        Uri uri = Provider.CONTENT_URI_SEARCH.buildUpon().appendQueryParameter("books", books).appendEncodedPath(query).fragment(version).build();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
        } catch (Exception e) {
        }

        String osis = bible.getOsis(query);
        if (osis != null) {
            String human = bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, osis));
            String chapters = bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, osis));
            String unformatted = getString(R.string.chapters, new Object[] { human, chapters });
            MatrixCursor extras = new MatrixCursor(new String[] { "_id", "book", "human", "verse", "unformatted" });
            extras.addRow(new String[] { "-1", osis, human, "0", unformatted });
            Cursor[] cursors = { extras, cursor };
            cursor = new MergeCursor(cursors);
        }
        handler.sendMessage(handler.obtainMessage(SHOWRESULT, cursor));
    }

    public void showResults(Cursor cursor) {
        try {
            dismissDialog(DIALOG);
        } catch (Throwable t) {
        }
        if (cursor == null) {
            textView.setText(getString(R.string.search_no_results, new Object[] {
                query,
                humanfrom,
                humanto,
                bible.getVersionName(version)
            }));
            return;
        } else {
            int count = cursor.getCount();
            String countString = getResources().getQuantityString(R.plurals.search_results, count, new Object[] {
                count,
                query,
                humanfrom,
                humanto,
                bible.getVersionName(version)
            });
            textView.setText(countString);
        }

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

        adapter = new SimpleCursorAdapter(this,
            R.layout.item, cursor, from, to);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                int verseIndex = cursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE);
                if (columnIndex == verseIndex) {
                    int[] chapterVerse = bible.getChapterVerse(cursor.getString(verseIndex));
                    if (chapterVerse[0] == 0) {
                        return true;
                    }
                    String string = getString(R.string.search_result_verse,
                        new Object[] {chapterVerse[0], chapterVerse[1]});
                    TextView textView = (TextView) view;
                    textView.setText(string);
                    return true;
                }

                if (columnIndex == cursor.getColumnIndexOrThrow(Provider.COLUMN_UNFORMATTED)) {
                    String context = cursor.getString(columnIndex);
                    if (Locale.getDefault().equals(Locale.SIMPLIFIED_CHINESE)) {
                        context = context.replaceAll("「", "“").replaceAll("」", "”");
                        context = context.replaceAll("『", "‘").replaceAll("』", "’");
                    }
                    ((TextView)view).setText(context, TextView.BufferType.SPANNABLE);
                    Spannable span = (Spannable) ((TextView) view).getText();
                    String lowercontext = context.toLowerCase(Locale.US);
                    String lowerquery = query.toLowerCase(Locale.US);
                    int index = -1;
                    while (true) {
                        index = lowercontext.indexOf(lowerquery, index + 1);
                        if (index == -1) {
                            break;
                        }
                        span.setSpan(background, index, index + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    ((TextView)view).setText(span);
                    return true;
                }
                return false;
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    showVerse((Cursor) adapter.getItem(position));
                } catch (Exception e) {
                }
            }
        });
    }

    private boolean showVerse(Cursor verseCursor) {
        String book = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
        String verse = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE));
        int[] chapterVerse = bible.getChapterVerse(verse);
        showChapter(book, chapterVerse[0], chapterVerse[1]);
        return true;
    }

    private void showChapter(String book, int chapter, int verse) {
        Intent intent = new Intent(getApplicationContext(), Chapter.class);
        ArrayList<OsisItem> items = new ArrayList<OsisItem>();
        Log.d(TAG, String.format("book: %s, chapter: %d, verse: %d", book, chapter, verse));
        if (chapter == 0) {
            items.add(new OsisItem(book, 1, 1));
        } else {
            items.add(new OsisItem(book, chapter, verse));
        }
        intent.putParcelableArrayListExtra("osiss", items);
        intent.putExtra("search", query);
        startActivity(intent);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(getApplicationContext(), Search.class));
        return false;
    }

    public String getQueryBooks(String osisfrom, String osisto) {
        int frombook = -1;
        int tobook = -1;
        if (osisfrom != null && !osisfrom.equals("")) {
            frombook = bible.getPosition(Bible.TYPE.OSIS, osisfrom);
        }
        if (osisto != null && !osisto.equals("")) {
            tobook = bible.getPosition(Bible.TYPE.OSIS, osisto);
        }
        if (frombook == -1) {
            frombook = 0;
        }
        if (tobook == -1) {
            tobook = bible.getCount(Bible.TYPE.OSIS) - 1;
        }
        if (tobook < frombook) {
            int swap = frombook;
            frombook = tobook;
            tobook = swap;
        }
        String queryBooks = String.format("'%s'", bible.get(Bible.TYPE.OSIS, frombook));
        for (int i = frombook + 1; i <= tobook; i++) {
            queryBooks += String.format(", '%s'", bible.get(Bible.TYPE.OSIS, i));
        }
        humanfrom = bible.get(Bible.TYPE.HUMAN, frombook);
        humanto = bible.get(Bible.TYPE.HUMAN, tobook);
        return queryBooks;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG:
                if (dialog == null) {
                    dialog = new ProgressDialog(this);
                }
                dialog.setMessage(getString(R.string.searching));
                dialog.setIndeterminate(true);
                dialog.setCancelable(true);
                return dialog;
        }
        return null;
    }

}
