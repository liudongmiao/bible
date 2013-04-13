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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map.Entry;

import android.app.SearchManager;
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

    private static Context mContext = null;

    private ArrayList<String> books = new ArrayList<String>();
    private ArrayList<String> osiss = new ArrayList<String>();
    private ArrayList<String> chapters = new ArrayList<String>();
    private ArrayList<String> versions = new ArrayList<String>();
    private ArrayList<String> humans = new ArrayList<String>();

    private static ArrayList<String> abbrs = new ArrayList<String>();
    private static ArrayList<String> names = new ArrayList<String>();
    private static ArrayList<String> resources = new ArrayList<String>();

    private static Bible bible = null;
    private static float scale;

    private Bible(Context context) {
        mContext = context;
        checkVersions();
        setDefaultVersion();
    }

    public synchronized static Bible getBible(Context context) {
        if (bible == null) {
            scale = context.getResources().getDisplayMetrics().density;
            bible = new Bible(context);
        }
        if (context != null) {
            mContext = context;
            setResources();
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
                    if (osisMap.size() == 0) {
                        zhOsisMap(osisMap);
                    }
                    String zhcn = osisMap.get(osis);
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
            return string.toUpperCase(Locale.US);
        }
        return resources.get(index);
    }

    public String getVersionName(String string) {
        int index = abbrs.indexOf(string);
        if (index == -1) {
            return string.toUpperCase(Locale.US);
        }
        return names.get(index);
    }

    private static void setResources() {
        Resources resource = mContext.getResources();
        TypedArray abbr = resource.obtainTypedArray(R.array.abbr);
        TypedArray name = resource.obtainTypedArray(R.array.name);
        TypedArray version = resource.obtainTypedArray(R.array.version);
        abbrs.clear();
        names.clear();
        resources.clear();
        for (int i = 0; i < abbr.length(); i++) {
            abbrs.add(abbr.getString(i));
            names.add(name.getString(i));
            resources.add(version.getString(i));
        }
        abbr.recycle();
        name.recycle();
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

    public final LinkedHashMap<String, String> osisMap = new LinkedHashMap<String, String>();

    protected void zhOsisMap(LinkedHashMap<String, String> map) {
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

    public final LinkedHashMap<String, String> humanMap = new LinkedHashMap<String, String>();

    protected void enHumanMap(LinkedHashMap<String, String> map) {
        map.put("Genesis", "Gen");
        map.put("Exodus", "Exod");
        map.put("Leviticus", "Lev");
        map.put("Numbers", "Num");
        map.put("Deuteronomy", "Deut");
        map.put("Joshua", "Josh");
        map.put("Judges", "Judg");
        map.put("Ruth", "Ruth");
        map.put("1 Samuel", "1Sam");
        map.put("2 Samuel", "2Sam");
        map.put("1 Kings", "1Kgs");
        map.put("2 Kings", "2Kgs");
        map.put("1 Chronicles", "1Chr");
        map.put("2 Chronicles", "2Chr");
        map.put("Ezra", "Ezra");
        map.put("Nehemiah", "Neh");
        map.put("Esther", "Esth");
        map.put("Job", "Job");
        map.put("Psalm", "Ps");
        map.put("Proverbs", "Prov");
        map.put("Ecclesiastes", "Eccl");
        map.put("Song of Songs", "Song");
        map.put("Isaiah", "Isa");
        map.put("Jeremiah", "Jer");
        map.put("Lamentations", "Lam");
        map.put("Ezekiel", "Ezek");
        map.put("Daniel", "Dan");
        map.put("Hosea", "Hos");
        map.put("Joel", "Joel");
        map.put("Amos", "Amos");
        map.put("Obadiah", "Obad");
        map.put("Jonah", "Jonah");
        map.put("Micah", "Mic");
        map.put("Nahum", "Nah");
        map.put("Habakkuk", "Hab");
        map.put("Zephaniah", "Zeph");
        map.put("Haggai", "Hag");
        map.put("Zechariah", "Zech");
        map.put("Malachi", "Mal");
        map.put("Matthew", "Matt");
        map.put("Mark", "Mark");
        map.put("Luke", "Luke");
        map.put("John", "John");
        map.put("Acts", "Acts");
        map.put("Romans", "Rom");
        map.put("1 Corinthians", "1Cor");
        map.put("2 Corinthians", "2Cor");
        map.put("Galatians", "Gal");
        map.put("Ephesians", "Eph");
        map.put("Philippians", "Phil");
        map.put("Colossians", "Col");
        map.put("1 Thessalonians", "1Thess");
        map.put("2 Thessalonians", "2Thess");
        map.put("1 Timothy", "1Tim");
        map.put("2 Timothy", "2Tim");
        map.put("Titus", "Titus");
        map.put("Philemon", "Phlm");
        map.put("Hebrews", "Heb");
        map.put("James", "Jas");
        map.put("1 Peter", "1Pet");
        map.put("2 Peter", "2Pet");
        map.put("1 John", "1John");
        map.put("2 John", "2John");
        map.put("3 John", "3John");
        map.put("Jude", "Jude");
        map.put("Revelation", "Rev");
    }

    protected void enHumanOsisMap(LinkedHashMap<String, String> map) {
        map.put("Gen", "Gen");
        map.put("Exod", "Exod");
        map.put("Lev", "Lev");
        map.put("Num", "Num");
        map.put("Deut", "Deut");
        map.put("Josh", "Josh");
        map.put("Judg", "Judg");
        map.put("Ruth", "Ruth");
        map.put("1Sam", "1Sam");
        map.put("2Sam", "2Sam");
        map.put("1Kgs", "1Kgs");
        map.put("2Kgs", "2Kgs");
        map.put("1Chr", "1Chr");
        map.put("2Chr", "2Chr");
        map.put("Ezra", "Ezra");
        map.put("Neh", "Neh");
        map.put("Esth", "Esth");
        map.put("Job", "Job");
        map.put("Ps", "Ps");
        map.put("Prov", "Prov");
        map.put("Eccl", "Eccl");
        map.put("Song", "Song");
        map.put("Isa", "Isa");
        map.put("Jer", "Jer");
        map.put("Lam", "Lam");
        map.put("Ezek", "Ezek");
        map.put("Dan", "Dan");
        map.put("Hos", "Hos");
        map.put("Joel", "Joel");
        map.put("Amos", "Amos");
        map.put("Obad", "Obad");
        map.put("Jonah", "Jonah");
        map.put("Mic", "Mic");
        map.put("Nah", "Nah");
        map.put("Hab", "Hab");
        map.put("Zeph", "Zeph");
        map.put("Hag", "Hag");
        map.put("Zech", "Zech");
        map.put("Mal", "Mal");
        map.put("Matt", "Matt");
        map.put("Mark", "Mark");
        map.put("Luke", "Luke");
        map.put("John", "John");
        map.put("Acts", "Acts");
        map.put("Rom", "Rom");
        map.put("1Cor", "1Cor");
        map.put("2Cor", "2Cor");
        map.put("Gal", "Gal");
        map.put("Eph", "Eph");
        map.put("Phil", "Phil");
        map.put("Col", "Col");
        map.put("1Thess", "1Thess");
        map.put("2Thess", "2Thess");
        map.put("1Tim", "1Tim");
        map.put("2Tim", "2Tim");
        map.put("Titus", "Titus");
        map.put("Phlm", "Phlm");
        map.put("Heb", "Heb");
        map.put("Jas", "Jas");
        map.put("1Pet", "1Pet");
        map.put("2Pet", "2Pet");
        map.put("1John", "1John");
        map.put("2John", "2John");
        map.put("3John", "3John");
        map.put("Jude", "Jude");
        map.put("Rev", "Rev");

    }

    protected void zhHumanMap(LinkedHashMap<String, String> map) {
        map.put("创世记", "Gen");
        map.put("出埃及记", "Exod");
        map.put("利未记", "Lev");
        map.put("民数记", "Num");
        map.put("申命记", "Deut");
        map.put("约书亚记", "Josh");
        map.put("士师记", "Judg");
        map.put("路得记", "Ruth");
        map.put("撒母耳记上", "1Sam");
        map.put("撒母耳记下", "2Sam");
        map.put("列王纪上", "1Kgs");
        map.put("列王纪下", "2Kgs");
        map.put("历代志上", "1Chr");
        map.put("历代志下", "2Chr");
        map.put("以斯拉记", "Ezra");
        map.put("尼希米记", "Neh");
        map.put("以斯帖记", "Esth");
        map.put("约伯记", "Job");
        map.put("诗篇", "Ps");
        map.put("箴言", "Prov");
        map.put("传道书", "Eccl");
        map.put("雅歌", "Song");
        map.put("以赛亚书", "Isa");
        map.put("耶利米书", "Jer");
        map.put("耶利米哀歌", "Lam");
        map.put("以西结书", "Ezek");
        map.put("但以理书", "Dan");
        map.put("何西阿书", "Hos");
        map.put("约珥书", "Joel");
        map.put("阿摩司书", "Amos");
        map.put("俄巴底亚书", "Obad");
        map.put("约拿书", "Jonah");
        map.put("弥迦书", "Mic");
        map.put("那鸿书", "Nah");
        map.put("哈巴谷书", "Hab");
        map.put("西番雅书", "Zeph");
        map.put("哈该书", "Hag");
        map.put("撒迦利亚书", "Zech");
        map.put("玛拉基书", "Mal");
        map.put("马太福音", "Matt");
        map.put("马可福音", "Mark");
        map.put("路加福音", "Luke");
        map.put("约翰福音", "John");
        map.put("使徒行传", "Acts");
        map.put("罗马书", "Rom");
        map.put("哥林多前书", "1Cor");
        map.put("哥林多后书", "2Cor");
        map.put("加拉太书", "Gal");
        map.put("以弗所书", "Eph");
        map.put("腓立比书", "Phil");
        map.put("歌罗西书", "Col");
        map.put("帖撒罗尼迦前书", "1Thess");
        map.put("帖撒罗尼迦后书", "2Thess");
        map.put("提摩太前书", "1Tim");
        map.put("提摩太后书", "2Tim");
        map.put("提多书", "Titus");
        map.put("腓利门书", "Phlm");
        map.put("希伯来书", "Heb");
        map.put("雅各书", "Jas");
        map.put("彼得前书", "1Pet");
        map.put("彼得后书", "2Pet");
        map.put("约翰一书", "1John");
        map.put("约翰二书", "2John");
        map.put("约翰三书", "3John");
        map.put("犹大书", "Jude");
        map.put("启示录", "Rev");
    }

    protected void zhHumanOsisMap(LinkedHashMap<String, String> map) {
        map.put("创", "Gen");
        map.put("出", "Exod");
        map.put("利", "Lev");
        map.put("民", "Num");
        map.put("申", "Deut");
        map.put("书", "Josh");
        map.put("士", "Judg");
        map.put("得", "Ruth");
        map.put("撒上", "1Sam");
        map.put("撒下", "2Sam");
        map.put("王上", "1Kgs");
        map.put("王下", "2Kgs");
        map.put("代上", "1Chr");
        map.put("代下", "2Chr");
        map.put("拉", "Ezra");
        map.put("尼", "Neh");
        map.put("斯", "Esth");
        map.put("伯", "Job");
        map.put("诗", "Ps");
        map.put("箴", "Prov");
        map.put("传", "Eccl");
        map.put("歌", "Song");
        map.put("赛", "Isa");
        map.put("耶", "Jer");
        map.put("哀", "Lam");
        map.put("结", "Ezek");
        map.put("但", "Dan");
        map.put("何", "Hos");
        map.put("珥", "Joel");
        map.put("摩", "Amos");
        map.put("俄", "Obad");
        map.put("拿", "Jonah");
        map.put("弥", "Mic");
        map.put("鸿", "Nah");
        map.put("哈", "Hab");
        map.put("番", "Zeph");
        map.put("该", "Hag");
        map.put("亚", "Zech");
        map.put("玛", "Mal");
        map.put("太", "Matt");
        map.put("可", "Mark");
        map.put("路", "Luke");
        map.put("约", "John");
        map.put("徒", "Acts");
        map.put("罗", "Rom");
        map.put("林前", "1Cor");
        map.put("林后", "2Cor");
        map.put("加", "Gal");
        map.put("弗", "Eph");
        map.put("腓", "Phil");
        map.put("西", "Col");
        map.put("帖前", "1Thess");
        map.put("帖后", "2Thess");
        map.put("提前", "1Tim");
        map.put("提后", "2Tim");
        map.put("多", "Titus");
        map.put("门", "Phlm");
        map.put("来", "Heb");
        map.put("雅", "Jas");
        map.put("彼前", "1Pet");
        map.put("彼后", "2Pet");
        map.put("约壹", "1John");
        map.put("约贰", "2John");
        map.put("约叁", "3John");
        map.put("犹", "Jude");
        map.put("启", "Rev");
    }

    public String getOsis(String book) {
        if (humanMap.size() == 0) {
            enHumanMap(humanMap);
            zhHumanMap(humanMap);
            enHumanOsisMap(humanMap);
            zhHumanOsisMap(humanMap);
        }

        book = book.replace("约一", "约壹");
        book = book.replace("约二", "约贰");
        book = book.replace("约三", "约叁");
        String osis = humanMap.get(book);
        if (osis != null) {
            return osis;
        }

        book = book.replace(" ", "");
        book = book.toLowerCase(Locale.US);
        for (Entry<String, String> entry: humanMap.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.US);
            if (key.replace(" ", "").equals(book)) {
                return entry.getValue();
            }
        }

        for (Entry<String, String> entry: humanMap.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.US);
            if (key.startsWith(book)) {
                return entry.getValue();
            }
        }

        for (Entry<String, String> entry: humanMap.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.US);
            if (key.contains(book)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /*
    public boolean isCJK(String s) {
        for (char c : s.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
    */

    public LinkedHashMap<String, String> getOsiss(String book) {
        LinkedHashMap<String, String> osiss = new LinkedHashMap<String, String>();
        if (humanMap.size() == 0) {
            enHumanMap(humanMap);
            zhHumanMap(humanMap);
            enHumanOsisMap(humanMap);
            zhHumanOsisMap(humanMap);
        }
        if (book != null) {
            book = book.replace("约一", "约壹");
            book = book.replace("约二", "约贰");
            book = book.replace("约三", "约叁");
            book = book.toLowerCase(Locale.US);
        }
        Log.d(TAG, "book: " + book);
        boolean start = false;
        for (Entry<String, String> entry: humanMap.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.US);
            if (book == null || book.equals(SearchManager.SUGGEST_URI_PATH_QUERY)) {
                if (entry.getKey().equalsIgnoreCase(mContext.getString(R.string.genesis))) {
                    start = true;
                }
                if (!start) {
                    continue;
                }
                if (!osiss.values().contains(entry.getValue())) {
                    Log.d(TAG, "key: " + entry.getKey() + ", value: " + entry.getValue());
                    osiss.put(entry.getKey(), entry.getValue());
                }
            } else if (key.replace(" ", "").contains(book.replace(" ", ""))) {
                if (!osiss.values().contains(entry.getValue())) {
                    Log.d(TAG, "key: " + entry.getKey() + ", value: " + entry.getValue());
                    osiss.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return osiss;
    }

}
