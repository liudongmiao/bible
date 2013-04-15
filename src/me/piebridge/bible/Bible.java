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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map.Entry;

import android.app.SearchManager;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.preference.PreferenceManager;

public class Bible
{

    private final static String TAG = "me.piebridge.bible$Bible";

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

    private static Bible bible = null;
    private static float scale;

    private HashMap<String, String> versionNames = new HashMap<String, String>();
    private HashMap<String, String> versionFullnames = new HashMap<String, String>();

    private LinkedHashMap<String, String> osisZHCN = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> osisZHTW = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> humanEN = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> humanZHCN = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> humanZHTW = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> searchfull = new LinkedHashMap<String, String>();;
    private LinkedHashMap<String, String> searchshort = new LinkedHashMap<String, String>();;
    private LinkedHashMap<String, String> human;

    private final int EN = 0;
    private final int ZHCN = 1;
    private final int ZHTW = 2;
    private int[] orders = new int[3];
    private Locale lastLocale;

    private Bible(Context context) {
        Log.d(TAG, "init bible");
        mContext = context;
        checkVersions();
        setDefaultVersion();
    }

    public Locale getLocale() {
        return lastLocale;
    }

    public void checkLocale() {
        Locale locale = Locale.getDefault();
        if (!locale.equals(lastLocale)) {
            lastLocale = locale;
            setOrders();
            setResources();
        }
    }

    public synchronized static Bible getBible(Context context) {
        if (bible == null) {
            scale = context.getResources().getDisplayMetrics().density;
            bible = new Bible(context);
        }
        if (context != null) {
            mContext = context;
            bible.checkLocale();
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
                    String version = name.toLowerCase(Locale.US).replace(".sqlite3", "").replace("niv2011", "niv").replace("niv1984", "niv84");
                    versions.add(version);
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
        version = version.toLowerCase(Locale.US);
        if (version.endsWith("demo")) {
            file = new File(mContext.getFilesDir(), version + ".sqlite3");
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
            try {
                database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                Log.d(TAG, "open database \"" + database.getPath() + "\"");
                setMetadata(database);
                return true;
            } catch (Exception e) {
                try {
                    file.delete();
                } catch (Exception f) {
                }
                checkVersions();
                return setDefaultVersion();
            }
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

                if (!isCJK(human)) {
                    humanEN.put(osis, human);
                } else if (isVersionZHCN(databaseVersion)) {
                    humanZHCN.put(osis, human);
                } else if (isVersionZHTW(databaseVersion)) {
                    humanZHTW.put(osis, human);
                }

                osiss.add(osis);
                if (scale > 1.0f) {
                    books.add(human);
                } else {
                    if (databaseVersion.endsWith("ts")) {
                        books.add(getResourceValue(osisZHTW, osis));
                    } else if (databaseVersion.endsWith("ss") || databaseVersion.equals("ccb")) {
                        books.add(getResourceValue(osisZHCN, osis));
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
        if (pos == -1) {
            pos = arrayList.size() - 1;
        }
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

    public String getVersionFullname(String version) {
        version = version.toLowerCase(Locale.US);
        String fullname = getResourceValue(versionFullnames, version.replace("demo", ""));
        if (version.endsWith("demo")) {
            fullname += "(" + mContext.getString(R.string.demo) + ")";
        }
        return fullname;
    }

    public String getVersionName(String version) {
        version = version.toLowerCase(Locale.US);
        return getResourceValue(versionNames, version.replace("demo", "")).toUpperCase(Locale.US);
    }

    private String getResourceValue(HashMap<String, String> map, String key) {
        return map.containsKey(key) ? map.get(key) : key;
    }

    private void setResourceValues(HashMap<String, String> map, int resId) {
        for (String entry: mContext.getResources().getStringArray(resId)) {
            String[] strings = entry.split("\\|", 2);
            map.put(strings[0], strings[1]);
        }
    }

    private void setResources() {
        Log.d(TAG, "setResources");
        setResourceValues(versionNames, R.array.versionname);
        setResourceValues(versionFullnames, R.array.versionfullname);
        setResourceValues(osisZHCN, R.array.osiszhcn);
        setResourceValues(osisZHTW, R.array.osiszhtw);
        setResourceValues(searchfull, R.array.searchfull);
        setResourceValues(searchshort, R.array.searchshort);
    }

    private void setDemoVersions() {
        int demoVersion = PreferenceManager.getDefaultSharedPreferences(mContext).getInt("demoVersion", 0);
        int versionCode = 0;
        try {
            versionCode = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
        }
        boolean newVersion = (demoVersion != versionCode);
        boolean unpack = unpackRaw(newVersion, R.raw.niv84demo, new File(mContext.getFilesDir(), "niv84demo.sqlite3"));
        if (unpack) {
            unpack = unpackRaw(newVersion, R.raw.cunpssdemo, new File(mContext.getFilesDir(), "cunpssdemo.sqlite3"));
        }
        if (newVersion && unpack) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("demoVersion", versionCode).commit();
        }
        if (!versions.contains("cunpss")) {
            versions.add("cunpssdemo");
        }
        if (!versions.contains("niv84")) {
            versions.add("niv84demo");
        }
    }

    private boolean setDefaultVersion() {
        if (setVersion("niv") || setVersion("niv84") || setVersion("nivdemo")) {
        }
        if (setVersion("ccb") || setVersion("cunpss") || setVersion("rcuvss") || setVersion("cunpssdemo")) {
        }
        if (setVersion("cunpts") || setVersion("rcuvts")) {
        }
        String version = PreferenceManager.getDefaultSharedPreferences(mContext).getString("version", null);
        if (version != null && getPosition(TYPE.VERSION, version) < 0) {
            version = null;
        }
        if (version == null && getCount(TYPE.VERSION) > 0) {
            version = get(TYPE.VERSION, 0);
        }
        if (version != null) {
            return setVersion(version);
        }
        return false;
    }

    private boolean unpackRaw(boolean newVersion, int resId, File file) {
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
            InputStream is = mContext.getResources().openRawResource(resId);
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

    private String getOsis(String book, ArrayList<LinkedHashMap<String, String>> maps) {

        // test osis
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(book)) {
                    return entry.getKey();
                }
            }
            break;
        }

        book = book.toLowerCase(Locale.US).replace(" ", "");
        // prefer fullname
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getValue().toLowerCase(Locale.US).replace(" ", "").equals(book)) {
                    return entry.getKey();
                }
            }
        }

        // prefer startswith
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getValue().toLowerCase(Locale.US).replace(" ", "").startsWith(book)) {
                    return entry.getKey();
                }
            }
        }

        // prefer contains
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getValue().toLowerCase(Locale.US).replace(" ", "").contains(book)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    private ArrayList<LinkedHashMap<String, String>> getMaps(TYPE type) {
        checkLocale();
        ArrayList<LinkedHashMap<String, String>> maps = new ArrayList<LinkedHashMap<String, String>>();
        for (int order: orders) {
            switch (order) {
                case ZHCN:
                    maps.add(type == TYPE.HUMAN ? humanZHCN : osisZHCN);
                    break;
                case ZHTW:
                    maps.add(type == TYPE.HUMAN ? humanZHTW: osisZHTW);
                    break;
                case EN:
                    if (type == TYPE.HUMAN) {
                        maps.add(humanEN);
                    }
                    break;
            }
        }
        if (type == TYPE.HUMAN) {
            maps.add(searchfull);
            maps.add(searchshort);
        }
        return maps;
    }
    /*
     * 根据book获取osis，用于查询
     *
     */
    public String getOsis(String book) {
        String osis;

        if (book == null) {
            return null;
        }

        book = book.replace("约一", "约壹");
        book = book.replace("约二", "约贰");
        book = book.replace("约三", "约叁");
        book = book.toLowerCase(Locale.US);

        boolean human = false;
        if (isCJK(book.substring(0, 1)) && book.length() > 2) {
            human = true;
        } else if (book.length() > 7) {
            human = true;
        }

        Log.d(TAG, "getOsis, book: " + book);

        if (human) {
            osis = getOsis(book, getMaps(TYPE.HUMAN));
            if (osis != null) {
                return osis;
            }

        } else {
            osis = getOsis(book, getMaps(TYPE.OSIS));
            if (osis != null) {
                return osis;
            }

            osis = getOsis(book, getMaps(TYPE.HUMAN));
            if (osis != null) {
                return osis;
            }

        }
        return null;
    }

    private boolean checkStartSuggest(LinkedHashMap<String, String> osiss, String value, String key, String book, int limit) {
        if ("".equals(book) || value.replace(" ", "").toLowerCase(Locale.US).startsWith(book)) {
            if (addSuggest(osiss, value, key, limit)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkContainSuggest(LinkedHashMap<String, String> osiss, String value, String key, String book, int limit) {
        if (value.replace(" ", "").toLowerCase(Locale.US).contains(book)) {
            if (addSuggest(osiss, value, key, limit)) {
                return true;
            }
        }
        return false;
    }

    private boolean addSuggest(LinkedHashMap<String, String> osiss, String value, String osis, int limit) {
        if (!osiss.values().contains(osis)) {
            String text = human.get(osis);
            if (text == null) {
                text = value;
            }
            Log.d(TAG, "add suggest, text=" + text + ", data=" + osis);
            osiss.put(text, osis);
        }
        if (limit != -1 && osiss.size() >= limit) {
            Log.d(TAG, "arrive limit " + limit);
            return true;
        }
        return false;
    }
    /*
     * 根据book获取多个osiss，可能用于查询
     *
     */
    public LinkedHashMap<String, String> getOsiss(String book, int limit) {
        LinkedHashMap<String, String> osiss = new LinkedHashMap<String, String>();

        if (book != null) {
            // fix for zhcn
            book = book.replace("约一", "约壹");
            book = book.replace("约二", "约贰");
            book = book.replace("约三", "约叁");
        }

        Log.d(TAG, "book: " + book);

        human = null;
        ArrayList<Entry<String, String>> maps = new ArrayList<Entry<String, String>>();

        for (Entry<String, String> entry: searchshort.entrySet()) {
            maps.add(entry);
        }

        for (Entry<String, String> entry: searchfull.entrySet()) {
            maps.add(entry);
        }

        for (LinkedHashMap<String, String> map: getMaps(TYPE.HUMAN)) {
            if (human == null && map.size() > 0) {
                human = map;
            }
            for (Entry<String, String> entry: map.entrySet()) {
                maps.add(entry);
            }
        }

        for (LinkedHashMap<String, String> map: getMaps(TYPE.OSIS)) {
            for (Entry<String, String> entry: map.entrySet()) {
                maps.add(entry);
            }
        }

        book = book.replace(SearchManager.SUGGEST_URI_PATH_QUERY, "").replace(" ", "").toLowerCase(Locale.US);
        for (Entry<String, String> entry: maps) {
            if (checkStartSuggest(osiss, entry.getKey(), entry.getKey(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry: maps) {
            if (checkStartSuggest(osiss, entry.getKey(), entry.getKey(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry: maps) {
            if (checkContainSuggest(osiss, entry.getValue(), entry.getKey(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry: maps) {
            if (checkContainSuggest(osiss, entry.getValue(), entry.getKey(), book, limit)) {
                return osiss;
            }
        }

        String osis = "";
        String chapter = "";
        if (osiss.size() == 0) {
            ArrayList<OsisItem> items = OsisItem.parseSearch(book, mContext);
            if (items.size() == 1) {
                OsisItem item = items.get(0);
                osis = item.book;
                chapter = item.chapter;
            }
        } else if (osiss.size() == 1) {
            for (Entry<String, String> entry: osiss.entrySet()) {
                osis = entry.getKey();
                chapter = "0";
            }
        }

        String bookname = human.get(osis);
        if (bookname == null) {
            bookname = osis;
        }
        int chapternum = 0;
        int maxchapter = 0;
        try {
            chapternum = Integer.parseInt(chapter);
        } catch (Exception e) {
        }
        try {
            maxchapter = Integer.parseInt(get(TYPE.CHAPTER, getPosition(TYPE.OSIS, osis)));
        } catch (Exception e) {
            return osiss;
        }
        if (bookname == null || "".equals(bookname)) {
            return osiss;
        }
        if (chapternum != 0) {
            osiss.put(bookname + " " + chapternum, osis + chapternum);
        }
        for (int i = 0 + chapternum * 10; i <= maxchapter && i < 10 * chapternum + 10; i++) {
            if (i != 0) {
                osiss.put(bookname + " " + i, osis + i);
            }
        }

        return osiss;
    }

    private void setOrders() {
        if (lastLocale.equals(Locale.SIMPLIFIED_CHINESE)) {
            orders[0] = ZHCN;
            orders[1] = ZHTW;
            orders[2] = EN;
        } else if (lastLocale.equals(Locale.TRADITIONAL_CHINESE)) {
            orders[0] = ZHTW;
            orders[1] = ZHCN;
            orders[2] = EN;
        } else {
            orders[0] = EN;
            orders[1] = ZHCN;
            orders[2] = ZHTW;
        }
    }

    public static boolean isCJK(String s) {
        for (char c : s.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVersionZHCN(String s) {
        if (s.endsWith("ss") || s.equals("ccb")) {
            return true;
        }
        return false;
    }

    public static boolean isVersionZHTW(String s) {
        if (s.endsWith("ts")) {
            return true;
        }
        return false;
    }

}
