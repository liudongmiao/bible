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
import android.app.SearchManager;
import android.os.Bundle;

import android.view.View;

import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;

import android.content.Intent;

import android.net.Uri;
import android.util.Log;
import android.database.Cursor;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.util.ArrayList;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.BaseAdapter;
import android.widget.ArrayAdapter;

public class Search extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener
{
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

    private SimpleCursorAdapter adapter = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVersion();

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        } else {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            query = sp.getString("query", "");
            searchtype = sp.getInt("searchtype", SEARCH_ALL);
            frombook = sp.getInt("frombook", 0);
            tobook = sp.getInt("tobook", Provider.osiss.size() - 1);
            showSearch();
        }

    }

    private boolean doSearch(String query) {
        setContentView(R.layout.result);
        searching = true;
        textView = (TextView) findViewById(R.id.text);
        listView = (ListView) findViewById(R.id.list);
        if (version == null) {
            textView.setText(R.string.noversion);
            return false;
        }

        setBooks();
        Log.d(Provider.TAG, "search \"" + query + "\" in version \"" + version + "\"");

        Uri uri = Provider.CONTENT_URI_SEARCH.buildUpon().appendEncodedPath(query).fragment(version).build();
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor == null) {
            textView.setText(getString(R.string.search_no_results, new Object[] {query, Provider.books.get(frombook), Provider.books.get(tobook), String.valueOf(version).toUpperCase()}));
            return false;
        } else {
            int count = cursor.getCount();
            String countString = getResources().getQuantityString(R.plurals.search_results, count,
                    new Object[] {count, query, Provider.books.get(frombook), Provider.books.get(tobook), String.valueOf(version).toUpperCase()});
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
                    int[] chapterVerse = Provider.getChapterVerse(cursor.getString(verseIndex));
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
        int[] chapterVerse = Provider.getChapterVerse(verse);
        String osis = book + "." + chapterVerse[0];
        Log.d(Provider.TAG, "show osis: " + osis + ", version: " + version);
        verseCursor.close();

        Intent chapterIntent = new Intent(getApplicationContext(), Chapter.class);
        Uri data = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
        chapterIntent.setData(data);
        chapterIntent.putExtra("verse", chapterVerse[1]);
        startActivity(chapterIntent);

        return true;
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
    }

    private void updateOptions() {
        for (Option option: options) {
            switch (option.text) {
                case R.string.chooseversion:
                    option.subtext = String.valueOf(version).toUpperCase();
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
                    option.subtext = Provider.books.get(frombook);
                    break;
                case R.string.searchto:
                    option.subtext = Provider.books.get(tobook);
                    break;
            }
        }
        optionadapter.notifyDataSetChanged();
    }

    public void showSearch() {
        inflater = LayoutInflater.from(this);
        setContentView(R.layout.search);
        searching = false;
        searchlist = (ListView) findViewById(R.id.searchlist);
        findViewById(R.id.searchbutton).setOnClickListener(this);
        ((EditText) findViewById(R.id.searchtext)).setText(query);

        spinner = new Spinner(this);
        spinneradapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        spinneradapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinneradapter);
        spinner.setOnItemSelectedListener(this);

        ((ViewGroup) searchlist.getParent()).addView(spinner, 0, 0);

        options.clear();
        options.add(new Option(R.string.chooseversion, String.valueOf(version).toUpperCase()));
        options.add(new Option(R.string.searchall, (searchtype == SEARCH_ALL)));
        options.add(new Option(R.string.searchold, (searchtype == SEARCH_OLD)));
        options.add(new Option(R.string.searchnew, (searchtype == SEARCH_NEW)));
        options.add(new Option(R.string.searchgospel, (searchtype == SEARCH_GOSPEL)));
        options.add(new Option(R.string.searchfrom, Provider.books.get(frombook)));
        options.add(new Option(R.string.searchto, Provider.books.get(tobook)));
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
        ArrayList<String> strings = null;

        spinneradapter.clear();
        switch (v.getId()) {
            case R.string.searchfrom:
                selected = frombook;
                for (String string: Provider.books) {
                    spinneradapter.add(string);
                }
                break;
            case R.string.searchto:
                selected = tobook;
                for (String string: Provider.books) {
                    spinneradapter.add(string);
                }
                break;
            case R.string.chooseversion:
                Provider.setVersions();
                selected = Provider.versions.indexOf(version);
                for (String string: Provider.versions) {
                    spinneradapter.add(Chapter.getVersion(string));
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
                version = Provider.versions.get(pos);
                Provider.setVersion(version);
                Log.d(Provider.TAG, "choose version: " + version);
                break;
        }
        if (frombook > tobook) {
            int swap = tobook;
            tobook = frombook;
            frombook = swap;
        }
        if (frombook == 0 && tobook == (Provider.books.size() - 1)) {
            searchtype = SEARCH_ALL;
        } else if (frombook == Provider.osiss.indexOf("Gen") && tobook == Provider.osiss.indexOf("Mal")) {
            searchtype = SEARCH_OLD;
        } else if (frombook == Provider.osiss.indexOf("Matt") && tobook == Provider.osiss.indexOf("Rev")) {
            searchtype = SEARCH_NEW;
        } else if (frombook == Provider.osiss.indexOf("Matt") && tobook == Provider.osiss.indexOf("John")) {
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
        switch (v.getId()) {
            case R.string.searchall:
                frombook = 0;
                tobook = Provider.books.size() - 1;
                searchtype = SEARCH_ALL;
                updateOptions();
                break;
            case R.string.searchold:
                // Gen, Mal
                from = Provider.osiss.indexOf("Gen");
                to = Provider.osiss.indexOf("Mal");
                if (from != -1 && to != -1) {
                    frombook = from;
                    tobook = to;
                }
                searchtype = SEARCH_OLD;
                updateOptions();
                break;
            case R.string.searchnew:
                // Matt, Rev
                from = Provider.osiss.indexOf("Matt");
                to = Provider.osiss.indexOf("Rev");
                if (from != -1 && to != -1) {
                    frombook = from;
                    tobook = to;
                }
                searchtype = SEARCH_NEW;
                updateOptions();
                break;
            case R.string.searchgospel:
                from = Provider.osiss.indexOf("Matt");
                to = Provider.osiss.indexOf("John");
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

    public void setBooks() {
        String queryBooks = String.format("'%s'", Provider.osiss.get(frombook));
        for (int i = frombook + 1; i <= tobook; i++) {
            queryBooks += String.format(", '%s'", Provider.osiss.get(i));
        }
        Provider.setQueryBooks(queryBooks);
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
        if (searching) {
            showSearch();
            return false;
        }
        return super.onSearchRequested();
    }

    @Override
    public void onPause() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("query", query);
        editor.putInt("searchtype", searchtype);
        editor.putInt("frombook", frombook);
        editor.putInt("tobook", tobook);
        editor.commit();
        Log.d(Provider.TAG, "on pause");
        super.onPause();
    }
}
