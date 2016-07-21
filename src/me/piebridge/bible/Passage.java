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

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Locale;

import me.piebridge.bible.activity.ReadingCrossActivity;
import me.piebridge.bible.activity.ReadingItemsActivity;
import me.piebridge.bible.utils.ChooserUtils;
import me.piebridge.bible.utils.LogUtils;

import static me.piebridge.bible.activity.ReadingItemsActivity.ITEMS;
import static me.piebridge.bible.activity.ReadingItemsActivity.SEARCH;

public class Passage extends Activity {

    private String action = null;
    private String search = null;
    private String osisfrom = null;
    private String osisto = null;
    private String version = null;
    private boolean cross;
    private Uri uri = null;
    private boolean hasVersion = true;

    public static final String CROSS = "cross";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.passage);
        Intent intent = getIntent();
        action = intent.getAction();
        uri = null;
        version = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
            if (uri == null) {
                finish();
                return;
            }
            version = uri.getQueryParameter("version");
            search = uri.getQueryParameter("search");
            if (TextUtils.isEmpty(search)) {
                search = uri.getQueryParameter("q");
            }
            if (TextUtils.isEmpty(search)) {
                search = uri.getPath().replaceAll("^/search/([^/]*).*$", "$1");
            }
            osisfrom = uri.getQueryParameter("from");
            osisto = uri.getQueryParameter("to");
            LogUtils.d("uri: " + uri + ", search: " + search);
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            search = intent.getStringExtra(SearchManager.QUERY);
            cross = intent.getBooleanExtra(CROSS, false);
            osisfrom = intent.getStringExtra("osisfrom");
            osisto = intent.getStringExtra("osisto");
        } else if ("text/plain".equals(intent.getType()) && Intent.ACTION_SEND.equals(intent.getAction())) {
            search = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (TextUtils.isEmpty(search)) {
            finish();
        } else {
            routeInThread();
        }
    }

    private void routeInThread() {
        new Thread(new Runnable() {
            public void run() {
                Bible bible = Bible.getInstance(getBaseContext());
                hasVersion = true;
                if (!TextUtils.isEmpty(version)) {
                    if (!bible.get(Bible.TYPE.VERSION).contains(version.toLowerCase(Locale.US))) {
                        hasVersion = false;
                    } else {
                        bible.setVersion(version);
                    }
                }
                route();
            }
        }).start();
    }

    private void route() {
        Intent intent;
        ArrayList<OsisItem> items = OsisItem.parseSearch(search, getBaseContext());
        if (!items.isEmpty() && !TextUtils.isEmpty(items.get(0).chapter)) {
            intent = new Intent(this, cross ? ReadingCrossActivity.class : ReadingItemsActivity.class);
            intent.putExtra(SEARCH, search);
            intent.putParcelableArrayListExtra(ITEMS, items);
            intent.putExtra(Intent.EXTRA_REFERRER, getIntent().getAction());
            startActivity(intent);
        } else if (!TextUtils.isEmpty(search) && Intent.ACTION_SEND.equals(action)) {
            intent = new Intent(this, Search.class);
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(SearchManager.QUERY, search);
            startActivity(intent);
        } else if (uri != null && !hasVersion) {
            intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            ChooserUtils.startActivityExcludeSelf(this, intent, version);
        } else if (!TextUtils.isEmpty(search)) {
            intent = new Intent(this, Result.class);
            intent.setAction(Intent.ACTION_SEARCH);
            intent.putExtra(SearchManager.QUERY, search);
            intent.putExtra("osisfrom", osisfrom);
            intent.putExtra("osisto", osisto);
            startActivity(intent);
        } else {
            intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            startActivity(intent);
        }
        finish();
    }

}
