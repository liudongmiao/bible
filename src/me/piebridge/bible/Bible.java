/*
 * vim: set sw=4 ts=4:
 *
 * Copyright (C) 2013 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Bible License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.preference.PreferenceManager;

public class Bible
{

    private final String TAG = "me.piebridge.bible$Bible";

    public enum TYPE {
        VERSION,
        CHAPTER,
        BOOK,
        OSIS,
    }

    private SQLiteDatabase database = null;
    private String databaseVersion = "";
    private String databasePath = null;

    private Context mContext = null;

    private ArrayList<String> books = new ArrayList<String>();
    private ArrayList<String> osiss = new ArrayList<String>();
    private ArrayList<String> chapters = new ArrayList<String>();
    private ArrayList<String> versions = new ArrayList<String>();

    private ArrayList<String> abbrs = new ArrayList<String>();
    private ArrayList<String> resources = new ArrayList<String>();

    private static Bible bible = null;

    private Bible(Context context) {
        mContext = context;
        checkVersions();
        setResources();
        setDefaultVersion();
    }

    public synchronized static Bible getBible(Context context) {
        if (bible == null) {
            bible = new Bible(context);
        }
        return bible;
    }

    public int[] getChapterVerse(String string) {
        Integer value = Integer.parseInt(string);
        Integer chapter = value / 1000;
        Integer verse = value - chapter * 1000;
        return new int[] {chapter, verse};
    }

    public boolean checkVersions() {
        versions.clear();
        if (!setDatabasePath()) {
            return false;
        }
        File path = new File(databasePath);
        File oldpath = new File(Environment.getExternalStorageDirectory(), ".piebridge");
        if (oldpath.exists() && oldpath.isDirectory()) {
            String[] names = oldpath.list();
            for (String name: names) {
                if (!name.endsWith(".sqlite3")) {
                    continue;
                }
                File oldfile = new File(oldpath, name);
                File newfile = new File(path, name);
                if (oldfile.exists() && oldfile.isFile() && !newfile.exists()) {
                    oldfile.renameTo(newfile);
                }
            }
        }
        if (path.exists() && path.isDirectory()) {
            String[] names = path.list();
            for (String name: names) {
                File file = new File(path, name);
                if (name.endsWith(".sqlite3") && file.exists() && file.isFile()) {
                    if (name.equals("niv.sqlite3")) {
                        versions.add(0, name.replace(".sqlite3", ""));
                    } else {
                        versions.add(name.replace(".sqlite3", ""));
                    }
                }
            }
        }
        return true;
    }

    public boolean setVersion(String version) {
        if (database != null) {
            if (databaseVersion.equals(version)) {
                return true;
            }
            Log.d(TAG, "close database \"" + database.getPath() + "\"");
            database.close();
        }
        /*
        if (!setDatabasePath()) {
            Log.e(TAG, "cannot setDatabasePath");
            return false;
        }
        */
        File file = new File(databasePath, version + ".sqlite3");
        if (file.exists() && file.isFile()) {
            databaseVersion = version;
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            Log.d(TAG, "open database \"" + database.getPath() + "\"");
            setMetadata(database);
            return true;
        } else {
            Log.e(TAG, "cannot get database \"" + file.getAbsolutePath() + "\"");
            databaseVersion = "";
            database = null;
            return false;
        }
    }

    private void setMetadata(SQLiteDatabase database) {
        osiss.clear();
        books.clear();
        chapters.clear();
        Cursor cursor = database.query(Provider.TABLE_BOOKS, Provider.COLUMNS_BOOKS, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                osiss.add(cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS)));
                books.add(cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN)));
                chapters.add(cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CHAPTERS)));
            }
        } finally {
            cursor.close();
        }
    }

    private File getExternalFilesDir() {
        // /mnt/sdcard/Android/data/me.piebridge.bible/files
        File file = new File(new File(new File(new File(Environment.getExternalStorageDirectory(),
                "Android"), "data"), mContext.getPackageName()), "files");
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Log.w(TAG, "cannot create directory: " + file);
                return null;
            }
            try {
                (new File(file, ".nomedia")).createNewFile();
            } catch (java.io.IOException ioe) {
            }
        }
        return file;
    }

    private boolean setDatabasePath() {
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            if (mContext == null) {
                Log.e(TAG, "mContext is null");
                return false;
            }
            File file = null;
            try {
                Method method = Context.class.getMethod("getExternalFilesDir", new Class[] {String.class});
                file = (File) method.invoke(mContext, new Object[] {null});
            } catch (Exception e) {
                Log.d(TAG, "internal getExternalFilesDir");
                file = getExternalFilesDir();
            }
            if (file == null) {
                Log.e(TAG, "file: " + file);
                return false;
            }
            databasePath = file.getAbsolutePath();
            Log.d(TAG, "set database path: " + databasePath);
            return true;
        }
        return false;
    }

    public ArrayList<String> get(TYPE type) {
        switch (type) {
            case VERSION:
                return versions;
            case CHAPTER:
                return chapters;
            case BOOK:
                return books;
            case OSIS:
                return osiss;
        }
        return new ArrayList<String>();
    }

    public int getPosition(TYPE type, String string) {
        return get(type).indexOf(string);
    }

    public String get(TYPE type, int pos) {
        ArrayList<String> arrayList = get(type);
        if (pos > -1 && pos < arrayList.size()) {
            return arrayList.get(pos);
        } else {
            return null;
        }
    }

    public int getCount(TYPE type) {
        return get(type).size();
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public String getVersion() {
        return databaseVersion;
    }

    public String getVersionResource(String string) {
        int index = abbrs.indexOf(string);
        if (index == -1) {
            return string;
        }
        return resources.get(index);
    }

    private void setResources() {
        Resources resource = mContext.getResources();
        TypedArray abbr = resource.obtainTypedArray(R.array.abbr);
        TypedArray version = resource.obtainTypedArray(R.array.version);
        for (int i = 0; i < abbr.length(); i++) {
            abbrs.add(abbr.getString(i));
            resources.add(version.getString(i));
        }
        abbr.recycle();
        version.recycle();
    }

    private void setDefaultVersion() {
        String version = PreferenceManager.getDefaultSharedPreferences(mContext).getString("version", null);
        if (version != null && getPosition(TYPE.VERSION, version) < 0) {
            version = null;
        }
        if (version == null && getCount(TYPE.VERSION) > 0) {
            version = get(TYPE.VERSION, 0);
        }
        setVersion(version);
    }

}
