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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
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
        HUMAN,
    }

    private SQLiteDatabase database = null;
    private String databaseVersion = "";
    private String databasePath = null;

    private Context mContext = null;

    private ArrayList<String> books = new ArrayList<String>();
    private ArrayList<String> osiss = new ArrayList<String>();
    private ArrayList<String> chapters = new ArrayList<String>();
    private ArrayList<String> versions = new ArrayList<String>();
    private ArrayList<String> humans = new ArrayList<String>();

    private ArrayList<String> abbrs = new ArrayList<String>();
    private ArrayList<String> resources = new ArrayList<String>();

    private static Bible bible = null;
    private static float scale;

    private Bible(Context context) {
        mContext = context;
        scale = context.getResources().getDisplayMetrics().density;
        if (scale <= 1.0) {
            initMap();
        }
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
        Log.d(TAG, "path=" + path + ", oldpath=" + oldpath);
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
                    Log.d(TAG, "add version " + name);
                    versions.add(name.replace(".sqlite3", "").replace("niv2011", "niv").replace("niv1984", "niv84"));
                }
            }
        }
        if (versions.size() == 0) {
            setDemoVersions();
        }
        return true;
    }

    public boolean setVersion(String version) {
        if (version == null) {
            return false;
        }
        if (databasePath == null) {
            return false;
        }
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
        File file = null;
        if (version.equals("demo")) {
            file = new File(mContext.getFilesDir(), mContext.getString(R.string.demopath));
        } else if (version.equals("niv")) {
            file = new File(databasePath, "niv2011.sqlite3");
        } else if (version.equals("niv84")) {
            file = new File(databasePath, "niv1984.sqlite3");
        }
        if (file == null || !file.exists() || !file.isFile()) {
            file = new File(databasePath, version + ".sqlite3");
        }
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
        humans.clear();
        Cursor cursor = database.query(Provider.TABLE_BOOKS, Provider.COLUMNS_BOOKS, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
                String human = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
                String chapter = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CHAPTERS));
                // fix for ccb
                if ("出埃及".equals(human)) {
                    human = "出埃及记";
                }
                osiss.add(osis);
                if (scale > 1.0f) {
                    books.add(human);
                } else {
                    String zhcn = map.get(osis);
                    if (zhcn != null && !zhcn.equals("") && human.indexOf(zhcn.substring(0, 1)) != -1) {
                        books.add(zhcn);
                    } else {
                        books.add(osis);
                    }
                }
                chapters.add(chapter);
                humans.add(human);
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
        } else {
            Log.d(TAG, "not mounted");
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
            case HUMAN:
                return humans;
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
        if (databaseVersion.equals("")) {
            setDefaultVersion();
        }
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

    private void setDemoVersions() {
        int demoVersion = PreferenceManager.getDefaultSharedPreferences(mContext).getInt("demoVersion", 0);
        int versionCode = 0;
        try {
            versionCode = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
        }
        boolean newVersion = (demoVersion != versionCode);
        boolean unpack = unpackRaw(newVersion, R.raw.zh, new File(mContext.getFilesDir(), "zh.sqlite3"));
        if (unpack) {
            unpack = unpackRaw(newVersion, R.raw.en, new File(mContext.getFilesDir(), "en.sqlite3"));
        }
        if (newVersion && unpack) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("demoVersion", versionCode).commit();
        }
        versions.add("demo");
    }

    private void setDefaultVersion() {
        String version = PreferenceManager.getDefaultSharedPreferences(mContext).getString("version", null);
        if (version != null && getPosition(TYPE.VERSION, version) < 0) {
            version = null;
        }
        if (version == null && getCount(TYPE.VERSION) > 0) {
            version = get(TYPE.VERSION, 0);
        }
        if (version != null) {
            setVersion(version);
        }
    }

    private boolean unpackRaw(boolean newVersion, int resid, File file) {
        if (file.exists()) {
            if (!newVersion) {
                return true;
            }
            file.delete();
        }

        Log.d(TAG, "unpacking " + file.getAbsolutePath());

        try {
            int length;
            byte [] buffer = new byte[8192];
            OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
            InputStream is = mContext.getResources().openRawResource(resid);
            while((length = is.read(buffer)) >= 0) {
                os.write(buffer, 0, length);
            }
            is.close();
            os.close();
        } catch (Exception e) {
            Log.e(TAG, "unpacked " + file.getAbsolutePath(), e);
            return false;
        }

        return true;
    }

    public final static HashMap<String, String> map = new HashMap<String, String>();

    protected void initMap() {
        map.put("Gen", "创");
        map.put("Exod", "出");
        map.put("Lev", "利");
        map.put("Num", "民");
        map.put("Deut", "申");
        map.put("Josh", "书");
        map.put("Judg", "士");
        map.put("Ruth", "得");
        map.put("1Sam", "撒上");
        map.put("2Sam", "撒下");
        map.put("1Kgs", "王上");
        map.put("2Kgs", "王下");
        map.put("1Chr", "代上");
        map.put("2Chr", "代下");
        map.put("Ezra", "拉");
        map.put("Neh", "尼");
        map.put("Esth", "斯");
        map.put("Job", "伯");
        map.put("Ps", "诗");
        map.put("Prov", "箴");
        map.put("Eccl", "传");
        map.put("Song", "歌");
        map.put("Isa", "赛");
        map.put("Jer", "耶");
        map.put("Lam", "哀");
        map.put("Ezek", "结");
        map.put("Dan", "但");
        map.put("Hos", "何");
        map.put("Joel", "珥");
        map.put("Amos", "摩");
        map.put("Obad", "俄");
        map.put("Jonah", "拿");
        map.put("Mic", "弥");
        map.put("Nah", "鸿");
        map.put("Hab", "哈");
        map.put("Zeph", "番");
        map.put("Hag", "该");
        map.put("Zech", "亚");
        map.put("Mal", "玛");
        map.put("Matt", "太");
        map.put("Mark", "可");
        map.put("Luke", "路");
        map.put("John", "约");
        map.put("Acts", "徒");
        map.put("Rom", "罗");
        map.put("1Cor", "林前");
        map.put("2Cor", "林后");
        map.put("Gal", "加");
        map.put("Eph", "弗");
        map.put("Phil", "腓");
        map.put("Col", "西");
        map.put("1Thess", "帖前");
        map.put("2Thess", "帖后");
        map.put("1Tim", "提前");
        map.put("2Tim", "提后");
        map.put("Titus", "多");
        map.put("Phlm", "门");
        map.put("Heb", "来");
        map.put("Jas", "雅");
        map.put("1Pet", "彼前");
        map.put("2Pet", "彼后");
        map.put("1John", "约壹");
        map.put("2John", "约贰");
        map.put("3John", "约叁");
        map.put("Jude", "犹");
        map.put("Rev", "启");
    }

}
