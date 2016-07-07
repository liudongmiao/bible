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

import java.util.ArrayList;
import java.util.Locale;

public class Result extends Activity
{
    private final String TAG = "me.piebridge.bible$Search";

    private TextView textView = null;
    private ListView listView = null;

    private String humanfrom;
    private String humanto;
    private String version = null;
    private String query = null;
    private String books = null;
    private Bible bible = null;
    private String osisfrom = null;
    private String osisto = null;
    private SimpleCursorAdapter adapter = null;

    private String prevQuery = null;
    private String prevBooks = null;
    private String prevVersion = null;

    protected final static int color = 0x6633B5E5;
    protected final static int SHOWRESULT = 1;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
            case SHOWRESULT:
                showResults((Cursor) msg.obj);
                break;
            }
            return false;
        }
    });

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result);
        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            osisfrom = intent.getStringExtra("osisfrom");
            osisto = intent.getStringExtra("osisto");
            Log.d(TAG, "query: " + query + ", osisfrom: " + osisfrom + ", osisto: " + osisto);
            textView = (TextView) findViewById(R.id.text);
            listView = (ListView) findViewById(R.id.list);
            show();
        } else {
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        new Thread(new Runnable() {
            public void run() {
                if (bible == null) {
                    bible = Bible.getInstance(getBaseContext());
                }
                version = bible.getVersion();
                books = getQueryBooks(osisfrom, osisto);
                if (!query.equals(prevQuery) || !books.equals(prevBooks) || !version.equals(prevVersion)) {
                    doSearch(query, books);
                }
            }
        }).start();
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
            String unformatted = getString(R.string.chapters, new Object[] { human, chapters.split(",").length });
            MatrixCursor extras = new MatrixCursor(new String[] { "_id", "book", "human", "verse", "unformatted" });
            extras.addRow(new String[] { "-1", osis, human, "0", unformatted });
            Cursor[] cursors = { extras, cursor };
            cursor = new MergeCursor(cursors);
        }
        prevQuery = query;
        prevBooks = books;
        prevVersion = version;
        handler.sendMessage(handler.obtainMessage(SHOWRESULT, cursor));
    }

    @SuppressWarnings("deprecation")
    public void showResults(Cursor cursor) {
        dismiss();
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
                    String book = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
                    String string = getString(R.string.search_result_verse,
                        new Object[] {getChapter(book, chapterVerse[0]), chapterVerse[1]});
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
                        span.setSpan(new BackgroundColorSpan(color), index, index + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
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
                    Log.e(TAG, "", e);
                }
            }
        });
    }

    private String getChapter(String book, int chapterId) {
        String[] chapters = bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, book)).split(",");
        String chapter;
        if (chapterId < 1) {
            chapter = chapters[0];
        } else if (chapterId > chapters.length) {
            chapter = chapters[chapters.length - 1];
        } else {
            chapter = chapters[chapterId - 1];
        }
        return chapter;

    }

    private boolean showVerse(Cursor verseCursor) {
        String book = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
        String verse = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE));
        int[] chapterVerse = bible.getChapterVerse(verse);
        showChapter(book, getChapter(book, chapterVerse[0]), chapterVerse[1]);
        return true;
    }

    private void showChapter(String book, String chapter, int verse) {
        Intent intent = new Intent(this, Chapter.class);
        ArrayList<OsisItem> items = new ArrayList<OsisItem>();
        Log.d(TAG, String.format("book: %s, chapter: %s, verse: %d", book, chapter, verse));
        if (chapter.equals("0")) {
            items.add(new OsisItem(book, 1));
        } else {
            items.add(new OsisItem(book, chapter, verse > 0 ? String.valueOf(verse) : ""));
        }
        intent.putParcelableArrayListExtra("osiss", items);
        intent.putExtra("search", query);
        startActivity(intent);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(this, Search.class));
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

    private void show() {
        findViewById(R.id.progress).setVisibility(View.VISIBLE);
        textView.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);
    }

    private void dismiss() {
        findViewById(R.id.progress).setVisibility(View.GONE);
        textView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.VISIBLE);
    }

}
