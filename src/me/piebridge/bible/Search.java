/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2013 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map.Entry;

public class Search extends PreferenceActivity implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener, TextView.OnEditorActionListener, View.OnClickListener
{

    private final String TAG = "me.piebridge.bible$Search";

    protected final String VERSION = "version";
    protected final String ALL = "searchall";
    protected final String OLD = "searchold";
    protected final String NEW = "searchnew";
    protected final String GOSPEL = "searchgospel";
    protected final String FROM = "searchfrom";
    protected final String TO = "searchto";

    protected final int SEARCH_CUSTOM = -1;
    protected final int SEARCH_ALL = 0;
    protected final int SEARCH_OLD = 1;
    protected final int SEARCH_NEW = 2;
    protected final int SEARCH_GOSPEL = 3;

    int searchtype;
    String query;
    String osisfrom;
    String osisto;

    Bible bible;
    ListPreference version;
    ListPreference searchfrom;
    ListPreference searchto;
    CheckBoxPreference searchall;
    CheckBoxPreference searchold;
    CheckBoxPreference searchnew;
    CheckBoxPreference searchgospel;
    BibleAutoCompleteTextView edittext;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bible = Bible.getBible(getBaseContext());
        bible.checkVersions();

        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            searchtype = SEARCH_ALL;
        } else {
            query = sp.getString("query", "");
            searchtype = sp.getInt("searchtype", SEARCH_ALL);
            osisfrom = sp.getString("osisfrom", bible.get(Bible.TYPE.OSIS, 0));
            osisto = sp.getString("osisto", bible.get(Bible.TYPE.OSIS, -1));
        }

        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(createPreferenceHierarchy(root));
        setContentView(R.layout.search);
        updateSearch();
        updateVersion();

        edittext = (BibleAutoCompleteTextView) findViewById(R.id.searchtext);
        edittext.setThreshold(1);
        edittext.setOnEditorActionListener(this);
        edittext.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line) {
            private ArrayList<String> mStrings = new ArrayList<String>();
            private BibleFilter mFilter;
            private Bible bible = Bible.getBible(getBaseContext());

            @Override
            public Filter getFilter() {
                if (mFilter == null) {
                    mFilter = new BibleFilter();
                }
                return mFilter;
            }

            public int getCount() {
                return mStrings.size();
            }

            public String getItem(int position) {
                return mStrings.get(position);
            }

            class BibleFilter extends Filter {
                @Override
                protected FilterResults performFiltering(CharSequence prefix) {
                    FilterResults results = new FilterResults();
                    if (prefix == null) {
                        prefix = "";
                    }

                    LinkedHashMap<String, String> suggestions = bible.getOsiss(prefix.toString(), 66);
                    final ArrayList<String> values = new ArrayList<String>();
                    if (!"".equals(query) && "".equals(prefix.toString())) {
                        values.add(query);
                    }
                    for (Entry<String, String> entry: suggestions.entrySet()) {
                        values.add(entry.getKey());
                    }
                    results.values = values;
                    results.count = values.size();
                    return results;
                }

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    mStrings = (ArrayList<String>) results.values;
                    if (results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            }
        });
        edittext.setOnClickListener(this);
        edittext.requestFocus();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            edittext.setText(query);
        }

        findViewById(R.id.searchbutton).setOnClickListener(this);
    }

    private CheckBoxPreference addCheckBox(String key, int title) {
        CheckBoxPreference checkbox = new CheckBoxPreference(this);
        checkbox.setKey(key);
        checkbox.setTitle(title);
        checkbox.setOnPreferenceChangeListener(this);
        switch (title) {
            case R.string.searchall:
                checkbox.setDefaultValue(searchtype == SEARCH_ALL);
                break;
            case R.string.searchold:
                checkbox.setDefaultValue(searchtype == SEARCH_OLD);
                break;
            case R.string.searchnew:
                checkbox.setDefaultValue(searchtype == SEARCH_NEW);
                break;
            case R.string.searchgospel:
                checkbox.setDefaultValue(searchtype == SEARCH_GOSPEL);
                break;
        }
        return checkbox;
    }

    private ListPreference addList(String key, int title, CharSequence summary, CharSequence[] entries, CharSequence[] entryValues) {
        ListPreference list = new ListPreference(this);
        list.setKey(key);
        list.setTitle(title);
        list.setDialogTitle(title);
        list.setEntries(entries);
        list.setEntryValues(entryValues);
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private PreferenceScreen createPreferenceHierarchy(PreferenceScreen root) {
        String[] versions = (String []) bible.get(Bible.TYPE.VERSION).toArray(new String[0]);
        String[] humanversions = new String[versions.length];
        for (int i = 0; i < versions.length; i++) {
            humanversions[i] = bible.getVersionFullname(versions[i]);
        }
        String[] osiss = (String []) bible.get(Bible.TYPE.OSIS).toArray(new String[0]);
        String[] humans = (String []) bible.get(Bible.TYPE.HUMAN).toArray(new String[0]);

        version = addList(VERSION, R.string.chooseversion, String.valueOf(bible.getVersion()).toUpperCase(Locale.US), humanversions, versions);
        version.setOnPreferenceClickListener(this);
        searchall = addCheckBox(ALL, R.string.searchall);
        searchold = addCheckBox(OLD, R.string.searchold);
        searchnew = addCheckBox(NEW, R.string.searchnew);
        searchgospel = addCheckBox(GOSPEL, R.string.searchgospel);
        searchfrom = addList(FROM, R.string.searchfrom, "", humans, osiss);
        searchto = addList(TO, R.string.searchto, "", humans, osiss);

        root.addPreference(version);
        root.addPreference(searchall);
        root.addPreference(searchold);
        root.addPreference(searchnew);
        root.addPreference(searchgospel);
        root.addPreference(searchfrom);
        root.addPreference(searchto);
        return root;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        Log.d(TAG, "click key: " + key);
        if (key.equals(VERSION)) {
            String[] versions = (String []) bible.get(Bible.TYPE.VERSION).toArray(new String[0]);
            String[] humanversions = new String[versions.length];
            for (int i = 0; i < versions.length; i++) {
                humanversions[i] = bible.getVersionFullname(versions[i]);
            }
            version.setEntries(humanversions);
            version.setEntryValues(versions);
        }
        return true;
    }

    protected void updateVersion() {
        int max = bible.getCount(Bible.TYPE.OSIS) - 1;
        int gen = bible.getPosition(Bible.TYPE.OSIS, "Gen");
        int rev = bible.getPosition(Bible.TYPE.OSIS, "Rev");
        int mal = bible.getPosition(Bible.TYPE.OSIS, "Mal");
        int matt = bible.getPosition(Bible.TYPE.OSIS, "Matt");
        int john = bible.getPosition(Bible.TYPE.OSIS, "John");
        String[] osiss = (String []) bible.get(Bible.TYPE.OSIS).toArray(new String[0]);
        String[] humans = (String []) bible.get(Bible.TYPE.HUMAN).toArray(new String[0]);
        searchfrom.setEntries(humans);
        searchfrom.setEntryValues(osiss);
        searchto.setEntries(humans);
        searchto.setEntryValues(osiss);

        version.setSummary(bible.getVersionName(bible.getVersion()).toUpperCase(Locale.US));
        searchall.setSummary(getString(R.string.fromto, new Object[] {bible.get(Bible.TYPE.HUMAN, 0), bible.get(Bible.TYPE.HUMAN, max)}));
        if (gen != -1 && mal != -1) {
            searchold.setEnabled(true);
            searchold.setSummary(getString(R.string.fromto, new Object[] {bible.get(Bible.TYPE.HUMAN, gen), bible.get(Bible.TYPE.HUMAN, mal)}));
        } else {
            searchold.setEnabled(false);
            searchold.setSummary("");
        }
        if (matt != -1 && rev != -1) {
            searchnew.setEnabled(true);
            searchnew.setSummary(getString(R.string.fromto, new Object[] {bible.get(Bible.TYPE.HUMAN, matt), bible.get(Bible.TYPE.HUMAN, rev)}));
        } else {
            searchnew.setEnabled(false);
            searchnew.setSummary("");
        }
        if (matt != -1 && john != -1) {
            searchgospel.setEnabled(true);
            searchgospel.setSummary(getString(R.string.fromto, new Object[] {bible.get(Bible.TYPE.HUMAN, matt), bible.get(Bible.TYPE.HUMAN, john)}));
        } else {
            searchgospel.setEnabled(false);
            searchgospel.setSummary("");
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        searchtype = SEARCH_CUSTOM;
        if (key.equals(VERSION)) {
            bible.setVersion((String)newValue);
            updateVersion();
        } else if (key.equals(ALL)) {
            osisfrom = bible.get(Bible.TYPE.OSIS, 0);
            osisto = bible.get(Bible.TYPE.OSIS, bible.getCount(Bible.TYPE.OSIS) - 1);
        } else if (key.equals(OLD)) {
            osisfrom = "Gen";
            osisto = "Mal";
        } else if (key.equals(NEW)) {
            osisfrom = "Matt";
            osisto = "Rev";
        } else if (key.equals(GOSPEL)) {
            osisfrom = "Matt";
            osisto = "John";
        } else if (key.equals(FROM)) {
            osisfrom = (String)newValue;
        } else if (key.equals(TO)) {
            osisto = (String)newValue;
        }
        Log.d("preference", "key: " + preference.getKey() + ", value=" + newValue);
        return updateSearch();
    }

    private boolean updateSearch() {
        boolean allow = true;
        int frompos = bible.getPosition(Bible.TYPE.OSIS, osisfrom);
        int topos = bible.getPosition(Bible.TYPE.OSIS, osisto);

        Log.d(TAG, "frompos: " + frompos + ", topos: " + topos);
        if (frompos == -1 || topos == -1) {
            allow = false;
            searchtype = SEARCH_CUSTOM;
        } else if (osisfrom.equals("Gen") && osisto.equals("Rev")) {
            searchtype = SEARCH_ALL;
        } else if (osisfrom.equals("Gen") && osisto.equals("Mal")) {
            searchtype = SEARCH_OLD;
        } else if (osisfrom.equals("Matt") && osisto.equals("Rev")) {
            searchtype = SEARCH_NEW;
        } else if (osisfrom.equals("Matt") && osisto.equals("John")) {
            searchtype = SEARCH_GOSPEL;
        }

        searchall.setChecked(searchtype == SEARCH_ALL);
        searchold.setChecked(searchtype == SEARCH_OLD);
        searchnew.setChecked(searchtype == SEARCH_NEW);
        searchgospel.setChecked(searchtype == SEARCH_GOSPEL);

        String from = bible.get(Bible.TYPE.HUMAN, frompos >= 0 ? frompos : 0);
        searchfrom.setValue(osisfrom);
        searchfrom.setSummary(from);

        String to = bible.get(Bible.TYPE.HUMAN, topos >= 0 ? topos : bible.getCount(Bible.TYPE.OSIS) - 1);
        searchto.setValue(osisto);
        searchto.setSummary(to);

        return allow;
    }

    @Override
    public void onPause() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        if (!version.getSummary().equals("DEMO")) {
            editor.putString("version", version.getSummary().toString());
        }
        editor.putString("query", query);
        editor.putInt("searchtype", searchtype);
        editor.putString("osisfrom", osisfrom);
        editor.putString("osisto", osisto);
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

    public void onClick(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        switch (v.getId()) {
            case R.id.searchtext:
                edittext.showDropDown();
                break;
            case R.id.searchbutton:
                query = edittext.getText().toString();
                // isEmpty since api-9 ?
                if (query.length() > 0) {
                    Intent intent = new Intent(getApplicationContext(), Passage.class);
                    intent.setAction(Intent.ACTION_SEARCH);
                    intent.putExtra(SearchManager.QUERY, query);
                    intent.putExtra("osisfrom", osisfrom);
                    intent.putExtra("osisto", osisto);
                    startActivity(intent);
                }
                break;
        }
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

}
