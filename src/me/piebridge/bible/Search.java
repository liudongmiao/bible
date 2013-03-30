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

import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;

import android.content.Intent;
import android.content.Context;

import android.net.Uri;
import android.database.Cursor;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.util.ArrayList;
import java.util.Locale;

import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.BaseAdapter;
import android.widget.ArrayAdapter;

public class Search extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener, TextView.OnEditorActionListener
{
    private final String TAG = "me.piebridge.bible$Search";

    private TextView textView = null;
    private ListView listView = null;;

    private final int SEARCH_CUSTOM = -1;
    private final int SEARCH_ALL = 0;
    private final int SEARCH_OLD = 1;
    private final int SEARCH_NEW = 2;
    private final int SEARCH_GOSPEL = 3;

    private Spinner spinner;
    private int searchtype;
    private ArrayAdapter<String> spinneradapter;

    private int frombook;
    private int tobook;
    private String version = null;
    private String query = null;
    private boolean searching = false;

    private Bible bible;
    private SimpleCursorAdapter adapter = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        bible = Bible.getBible(getBaseContext());
        version = bible.getVersion();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        } else {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            query = sp.getString("query", "");
            searchtype = sp.getInt("searchtype", SEARCH_ALL);
            frombook = sp.getInt("frombook", 0);
            tobook = sp.getInt("tobook", bible.getCount(Bible.TYPE.OSIS) - 1);
            showSearch();
        }
    }

    @Override
    public void onResume() {
        version = bible.getVersion();
        super.onResume();
    }

    private boolean doSearch(String query) {
        searching = false;
        setContentView(R.layout.result);
        textView = (TextView) findViewById(R.id.text);
        listView = (ListView) findViewById(R.id.list);
        if (version == null) {
            textView.setText(R.string.noversion);
            return false;
        }

        Log.d(TAG, "search \"" + query + "\" in version \"" + version + "\"");

        Uri uri = Provider.CONTENT_URI_SEARCH.buildUpon().appendQueryParameter("books", getQueryBooks()).appendEncodedPath(query).fragment(version).build();
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor == null) {
            textView.setText(getString(R.string.search_no_results, new Object[] {
                query,
                bible.get(Bible.TYPE.BOOK, frombook),
                bible.get(Bible.TYPE.BOOK, tobook),
                String.valueOf(version).toUpperCase(Locale.US)
            }));
            return false;
        } else {
            int count = cursor.getCount();
            String countString = getResources().getQuantityString(R.plurals.search_results, count, new Object[] {
                count,
                query,
                bible.get(Bible.TYPE.BOOK, frombook),
                bible.get(Bible.TYPE.BOOK, tobook),
                String.valueOf(version).toUpperCase(Locale.US)
            });
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
            R.layout.item, cursor, from, to);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                int verseIndex = cursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE);
                if (columnIndex == verseIndex) {
                    int[] chapterVerse = bible.getChapterVerse(cursor.getString(verseIndex));
                    String string = getString(R.string.search_result_verse,
                        new Object[] {chapterVerse[0], chapterVerse[1]});
                    TextView textView = (TextView) view;
                    textView.setText(string);
                    return true;
                }

                if (columnIndex == cursor.getColumnIndexOrThrow(Provider.COLUMN_UNFORMATTED)) {
                    String context = cursor.getString(columnIndex);
                    context = context.replaceAll("「", "“").replaceAll("」", "”");
                    context = context.replaceAll("『", "‘").replaceAll("』", "’");
                    ((TextView)view).setText(context);
                    return true;
                }
                return false;
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showVerse(String.valueOf(id));
            }
        });
    }

    private boolean showVerse(String id) {
        if (id == null) {
            return false;
        }
        Uri uri = Provider.CONTENT_URI_VERSE.buildUpon().appendEncodedPath(id).fragment(version).build();
        Cursor verseCursor = getContentResolver().query(uri, null, null, null, null);

        String book = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
        String verse = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE));
        int[] chapterVerse = bible.getChapterVerse(verse);
        String osis = book + "." + chapterVerse[0];
        Log.d(TAG, "show osis: " + osis + ", version: " + version);
        verseCursor.close();

        Intent chapterIntent = new Intent(getApplicationContext(), Chapter.class);
        Uri data = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
        chapterIntent.setData(data);
        chapterIntent.putExtra("verse", chapterVerse[1]);
        startActivity(chapterIntent);

        return true;
    }

    private void updateOptions() {
        for (Option option: options) {
            switch (option.text) {
                case R.string.chooseversion:
                    option.subtext = String.valueOf(version).toUpperCase(Locale.US);
                    break;
                case R.string.searchall:
                    option.checked = (searchtype == SEARCH_ALL);
                    break;
                case R.string.searchold:
                    option.checked = (searchtype == SEARCH_OLD);
                    break;
                case R.string.searchnew:
                    option.checked = (searchtype == SEARCH_NEW);
                    break;
                case R.string.searchgospel:
                    option.checked = (searchtype == SEARCH_GOSPEL);
                    break;
                case R.string.searchfrom:
                    option.subtext = bible.get(Bible.TYPE.BOOK, frombook);
                    break;
                case R.string.searchto:
                    option.subtext = bible.get(Bible.TYPE.BOOK, tobook);
                    break;
            }
        }
        optionadapter.notifyDataSetChanged();
    }

    public void showSearch() {
        searching = true;
        if (bible.getCount(Bible.TYPE.BOOK) == 0) {
            setContentView(R.layout.result);
            textView = (TextView) findViewById(R.id.text);
            textView.setText(R.string.noversion);
            return;
        }
        inflater = LayoutInflater.from(this);
        setContentView(R.layout.search);
        searchlist = (ListView) findViewById(R.id.searchlist);
        findViewById(R.id.searchbutton).setOnClickListener(this);
        final EditText edittext = (EditText) findViewById(R.id.searchtext);
        edittext.setText(query);
        edittext.selectAll();
        edittext.setOnEditorActionListener(this);

        spinner = new Spinner(this);
        spinneradapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        spinneradapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinneradapter);
        spinner.setOnItemSelectedListener(this);

        ((ViewGroup) searchlist.getParent()).addView(spinner, 0, 0);

        options.clear();
        options.add(new Option(R.string.chooseversion, String.valueOf(bible.getVersion()).toUpperCase(Locale.US)));
        options.add(new Option(R.string.searchall, (searchtype == SEARCH_ALL)));
        options.add(new Option(R.string.searchold, (searchtype == SEARCH_OLD)));
        options.add(new Option(R.string.searchnew, (searchtype == SEARCH_NEW)));
        options.add(new Option(R.string.searchgospel, (searchtype == SEARCH_GOSPEL)));
        options.add(new Option(R.string.searchfrom, bible.get(Bible.TYPE.BOOK, frombook)));
        options.add(new Option(R.string.searchto, bible.get(Bible.TYPE.BOOK, tobook)));
        optionadapter = new OptionAdapter();
        searchlist.setAdapter(optionadapter);
    }

    private ArrayList<Option> options = new ArrayList<Option>();

    public class Option {
        int text;
        String subtext = null;
        boolean checkable = false;
        boolean checked = false;

        Option(int mtext, String msubtext) {
            text = mtext;
            checkable = false;
            subtext = msubtext;
        }

        Option(int mtext, boolean mchecked) {
            text = mtext;
            checkable = true;
            checked = mchecked;
        }

    }

    private void showSpinner(View v) {
        int selected = 0;

        spinneradapter.clear();
        switch (v.getId()) {
            case R.string.searchfrom:
                selected = frombook;
                for (String string: bible.get(Bible.TYPE.BOOK)) {
                    spinneradapter.add(string);
                }
                break;
            case R.string.searchto:
                selected = tobook;
                for (String string: bible.get(Bible.TYPE.BOOK)) {
                    spinneradapter.add(string);
                }
                break;
            case R.string.chooseversion:
                bible.checkVersions();
                selected = bible.getPosition(Bible.TYPE.VERSION, version);
                for (String string: bible.get(Bible.TYPE.VERSION)) {
                    spinneradapter.add(bible.getVersionResource(string));
                }
                break;
        }

        spinner.setId(v.getId());
        spinner.setPromptId(v.getId());
        spinner.setSelection(selected);
        spinner.performClick();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        switch (spinner.getId()) {
            case R.string.searchfrom:
                frombook = pos;
                searchtype = SEARCH_CUSTOM;
                break;
            case R.string.searchto:
                tobook = pos;
                searchtype = SEARCH_CUSTOM;
                break;
            case R.string.chooseversion:
                version = bible.get(Bible.TYPE.VERSION, pos);
                bible.setVersion(version);
                Log.d(TAG, "choose version: " + version);
                break;
        }
        if (frombook > tobook) {
            int swap = tobook;
            tobook = frombook;
            frombook = swap;
        }
        if (frombook == 0 && tobook == (bible.getCount(Bible.TYPE.BOOK) - 1)) {
            searchtype = SEARCH_ALL;
        } else if (frombook == bible.getPosition(Bible.TYPE.OSIS, "Gen") && tobook == bible.getPosition(Bible.TYPE.OSIS, "Mal")) {
            searchtype = SEARCH_OLD;
        } else if (frombook == bible.getPosition(Bible.TYPE.OSIS, "Matt") && tobook == bible.getPosition(Bible.TYPE.OSIS, "Rev")) {
            searchtype = SEARCH_NEW;
        } else if (frombook == bible.getPosition(Bible.TYPE.OSIS, "Matt") && tobook == bible.getPosition(Bible.TYPE.OSIS, "John")) {
            searchtype = SEARCH_GOSPEL;
        }
        updateOptions();
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onClick(View v) {
        int from;
        int to;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        switch (v.getId()) {
            case R.string.searchall:
                frombook = 0;
                tobook = bible.getCount(Bible.TYPE.OSIS) - 1;
                searchtype = SEARCH_ALL;
                updateOptions();
                break;
            case R.string.searchold:
                // Gen, Mal
                from = bible.getPosition(Bible.TYPE.OSIS, "Gen");
                to = bible.getPosition(Bible.TYPE.OSIS, "Mal");
                if (from != -1 && to != -1) {
                    frombook = from;
                    tobook = to;
                }
                searchtype = SEARCH_OLD;
                updateOptions();
                break;
            case R.string.searchnew:
                // Matt, Rev
                from = bible.getPosition(Bible.TYPE.OSIS, "Matt");
                to = bible.getPosition(Bible.TYPE.OSIS, "Rev");
                if (from != -1 && to != -1) {
                    frombook = from;
                    tobook = to;
                }
                searchtype = SEARCH_NEW;
                updateOptions();
                break;
            case R.string.searchgospel:
                from = bible.getPosition(Bible.TYPE.OSIS, "Matt");
                to = bible.getPosition(Bible.TYPE.OSIS, "John");
                if (from != -1 && to != -1) {
                    frombook = from;
                    tobook = to;
                }
                searchtype = SEARCH_GOSPEL;
                updateOptions();
                break;
            case R.string.chooseversion:
            case R.string.searchfrom:
            case R.string.searchto:
                showSpinner(v);
                break;
            case R.id.searchbutton:
                query = ((EditText) findViewById(R.id.searchtext)).getText().toString();
                // isEmpty since api-9 ?
                if (query.length() > 0) {
                    doSearch(query);
                }
                break;
        }
    }

    public String getQueryBooks() {
        String queryBooks = String.format("'%s'", bible.get(Bible.TYPE.OSIS, frombook));
        for (int i = frombook + 1; i <= tobook; i++) {
            queryBooks += String.format(", '%s'", bible.get(Bible.TYPE.OSIS, i));
        }
        return queryBooks;
    }

    private ListView searchlist;
    private LayoutInflater inflater;
    private OptionAdapter optionadapter = null;
    public class OptionAdapter extends BaseAdapter {

        public int getCount() {
            return options.size();
        }

        public long getItemId(int position) {
            return position;
        }

        public Object getItem(int position) {
            return options.get(position);
        }

        @Override
        public int getViewTypeCount() {
            return options.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder holder;
            Option option = options.get(position);

            if (view == null) {
                holder = new ViewHolder();
                if (option.checkable) {
                    view = inflater.inflate(R.layout.checkable, null);
                    holder.radio = (RadioButton) view.findViewById(R.id.radio);
                } else {
                    view = inflater.inflate(R.layout.selectable, null);
                    holder.subtext = (TextView) view.findViewById(R.id.subtext);
                }
                holder.text = (TextView) view.findViewById(R.id.text);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            view.setId(option.text);
            view.setClickable(true);
            view.setOnClickListener(Search.this);
            holder.text.setText(option.text);
            if (option.checkable) {
                holder.radio.setId(option.text);
                holder.radio.setOnClickListener(Search.this);
                holder.radio.setChecked(option.checked);
            } else {
                holder.subtext.setText(option.subtext);
            }

            return view;
        }

        class ViewHolder {
            TextView text;
            TextView subtext;
            RadioButton radio;
            Option option;
        };

    };

    @Override
    public boolean onSearchRequested() {
        if (!searching) {
            showSearch();
        }
        return false;
    }

    @Override
    public void onPause() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("query", query);
        editor.putInt("searchtype", searchtype);
        editor.putInt("frombook", frombook);
        editor.putInt("tobook", tobook);
        editor.commit();
        Log.d(TAG, "on pause");
        super.onPause();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        View view = findViewById(R.id.searchbutton);
        if (view == null) {
            return false;
        }
        if (actionId == EditorInfo.IME_ACTION_SEARCH ||
            // why some system report IME_ACTION_UNSPECIFIED ???
            (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            view.performClick();
            return true;
        }
        return false;
    }

}
