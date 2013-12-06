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

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Intent;

import java.util.ArrayList;

public class Passage extends Activity {

    private final String TAG = "me.piebridge.bible$Passage";

    String action = null;
    String search = null;
    String osisfrom = null;
    String osisto = null;
    String version = null;
    ProgressDialog dialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.passage);
        Intent intent = getIntent();
        action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri == null) {
                finish();
                return;
            }
            version = uri.getQueryParameter("version");
            search = uri.getQueryParameter("search");
            if (search == null || search.equals("")) {
                search = uri.getQueryParameter("q");
            }
            Log.d(TAG, "search: " + search);
            if (search == null || search.equals("")) {
                search = uri.getPath().replaceAll("^/search/([^/]*).*$", "$1");
                Log.d(TAG, "search: " + search + ", path: " + uri.getPath());
            }
            osisfrom = uri.getQueryParameter("from");
            osisto = uri.getQueryParameter("to");
            Log.d(TAG, "uri: " + uri);
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            search = intent.getStringExtra(SearchManager.QUERY);
            osisfrom = intent.getStringExtra("osisfrom");
            osisto = intent.getStringExtra("osisto");
        } else if ("text/plain".equals(intent.getType()) && Intent.ACTION_SEND.equals(intent.getAction())) {
            search = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (search == null) {
            finish();
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                Bible bible = Bible.getBible(getBaseContext());
                if (version != null && bible != null) {
                    bible.setVersion(version);
                }
                route();
            }
        }).start();
    }

    private void route() {
        Intent intent;
        ArrayList<OsisItem> items = OsisItem.parseSearch(search, getBaseContext());
        if (items.size() > 0 && !"".equals(items.get(0).chapter)) {
            intent = new Intent(getApplicationContext(), Chapter.class);
            intent.putExtra("search", search);
            intent.putParcelableArrayListExtra("osiss", items);
            startActivity(intent);
        } else if (search != null && Intent.ACTION_SEND.equals(action)) {
            intent = new Intent(getApplicationContext(), Search.class);
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(SearchManager.QUERY, search);
            startActivity(intent);
        } else if (search != null) {
            intent = new Intent(getApplicationContext(), Result.class);
            intent.setAction(Intent.ACTION_SEARCH);
            intent.putExtra(SearchManager.QUERY, search);
            intent.putExtra("osisfrom", osisfrom);
            intent.putExtra("osisto", osisto);
            startActivity(intent);
        } else {
            intent = new Intent(getApplicationContext(), Chapter.class);
            startActivity(intent);
        }
        finish();
    }

}
