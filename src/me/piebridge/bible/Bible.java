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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public class Bible
{

    private final static String TAG = "me.piebridge.bible$Bible";

    public enum TYPE {
        VERSION,
        CHAPTER,
        BOOK,
        OSIS,
        HUMAN,
        VERSIONPATH,
    }

    private SQLiteDatabase database = null;
    private String databaseVersion = "";

    private static Context mContext = null;

    private ArrayList<String> books = new ArrayList<String>();
    private ArrayList<String> osiss = new ArrayList<String>();
    private ArrayList<String> chapters = new ArrayList<String>();
    private ArrayList<String> versions = new ArrayList<String>();
    private Object versionsLock = new Object();
    private HashMap<String, String> versionpaths = new HashMap<String, String>();
    private ArrayList<String> humans = new ArrayList<String>();

    private static Bible bible = null;
    private static float scale;

    private HashMap<String, String> versionNames = new HashMap<String, String>();
    private HashMap<String, String> versionFullnames = new HashMap<String, String>();

    private LinkedHashMap<String, String> osisZHCN = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> osisZHTW = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> allhuman = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> allosis = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> searchfull = new LinkedHashMap<String, String>();;
    private LinkedHashMap<String, String> searchshort = new LinkedHashMap<String, String>();;

    private Locale lastLocale;
    private boolean unpacked = false;
    private HashMap<String, Long> mtime = new HashMap<String, Long>();
    private String css;
    public String versionName;

    private Bible(Context context) {
        Log.d(TAG, "init bible");
        mContext = context;
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
        }
        checkLocale();
        setDefaultVersion();
    }

    public Locale getLocale() {
        return lastLocale;
    }

    public void checkLocale() {
        Locale locale = Locale.getDefault();
        if (!locale.equals(lastLocale)) {
            lastLocale = locale;
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
        int value;
        try {
            value = Integer.parseInt(string);
        } catch (Exception e) {
            value = 0;
        }
        int chapter = value / 1000;
        int verse = value - chapter * 1000;
        return new int[] {chapter, verse};
    }

    public boolean checkVersions() {
        synchronized (versionsLock) {
            return checkVersionsSync();
        }
    }

    private boolean checkVersionsSync() {
        File path = getExternalFilesDirWrapper();
        if (path == null) {
            mtime.clear();
            versions.clear();
            checkInternalVersions();
            return false;
        }
        File oldpath = new File(Environment.getExternalStorageDirectory(), ".piebridge");
        Long oldmtime = mtime.get(path.getAbsolutePath());
        if (oldmtime == null) {
            oldmtime = 0L;
        }
        if (versions.size() != 0 && path.lastModified() <= oldmtime
                && (!oldpath.exists() || !oldpath.isDirectory() || oldpath.lastModified() <= oldmtime)) {
            return true;
        }
        versions.clear();
        versionpaths.clear();
        checkVersion(oldpath);
        checkVersion(path);
        Collections.sort(versions);
        if (versions.size() == 0) {
            checkInternalVersions();
        }
        mtime.put(path.getAbsolutePath(), path.lastModified());
        return true;
    }

    private void checkInternalVersions() {
        if (!unpacked) {
            setDemoVersions();
            unpacked = true;
        }
        checkVersion(mContext.getFilesDir());
    }

    public boolean isDemoVersion(String version) {
        File file = getFile(version);
        if (file == null) {
            return false;
        } else {
            return file.getParentFile().equals(mContext.getFilesDir());
        }
    }

    public boolean setVersion(String version) {
        if (version == null) {
            return false;
        }
        File file = getFile(version);
        if (file == null || !file.isFile()) {
            if ("".equals(databaseVersion)) {
                return setDefaultVersion();
            } else {
                return false;
            }
        }
        if (database != null) {
            if (databaseVersion.equals(version)) {
                return true;
            }
            Log.d(TAG, "close database \"" + database.getPath() + "\"");
            database.close();
        }
        databaseVersion = version;
        try {
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            Log.d(TAG, "open database \"" + database.getPath() + "\"");
            setMetadata(database, databaseVersion);
            return true;
        } catch (Exception e) {
            try {
                file.delete();
            } catch (Exception f) {
            }
            return setDefaultVersion();
        }
    }

    private File getFile(String version) {
        if (version == null) {
            return null;
        }
        version = version.toLowerCase(Locale.US);
        String path = versionpaths.get(version);
        if (path != null) {
            return new File(path);
        } else {
            return null;
        }
    }

    private void setMetadata(SQLiteDatabase metadata, String dataversion) {
        Cursor cursor = metadata.query(Provider.TABLE_BOOKS, Provider.COLUMNS_BOOKS, null, null, null, null, null);
        osiss.clear();
        books.clear();
        chapters.clear();
        humans.clear();
        try {
            while (cursor.moveToNext()) {
                String osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
                String book = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
                String chapter = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CHAPTERS));

                // fix for ccb
                if ("出埃及".equals(book)) {
                    book = "出埃及记";
                }

                if (book.endsWith(" 1")) {
                    book = book.substring(0, book.length() - 2);
                }
                allhuman.put(book, osis);
                osiss.add(osis);
                if (scale > 1.0f) {
                    books.add(book);
                } else {
                    if (dataversion.endsWith("ts")) {
                        books.add(getResourceValue(osisZHTW, osis));
                    } else if (dataversion.endsWith("ss") || dataversion.equals("ccb")) {
                        books.add(getResourceValue(osisZHCN, osis));
                    } else {
                        books.add(osis);
                    }
                }
                chapters.add(chapter);
                humans.add(book);
            }
        } finally {
            cursor.close();
        }

        css = "";
        cursor = metadata.query("metadata", new String[] {"value"}, "name = ?",
                new String[] { "css" }, null, null, null, "1");
        while (cursor != null && cursor.moveToNext()) {
            css = cursor.getString(cursor.getColumnIndexOrThrow("value"));
            cursor.close();
            break;
        }
    }

    public String getCSS() {
        return css;
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

    private File getExternalFilesDirWrapper() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) || mContext == null) {
            Log.d(TAG, "not mounted, mContext = " + mContext);
            return null;
        }
        try {
            Method method = Context.class.getMethod("getExternalFilesDir", new Class[] {String.class});
            return (File) method.invoke(mContext, new Object[] {null});
        } catch (Exception e) {
            Log.d(TAG, "internal getExternalFilesDir");
            return getExternalFilesDir();
        }
    }

    public ArrayList<String> get(TYPE type) {
        synchronized (versionsLock) {
            return getSync(type);
        }
    }

    public ArrayList<String> getSync(TYPE type) {
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
            case VERSIONPATH:
                return new ArrayList<String>(versionpaths.keySet());
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

    private String getVersionMetadata(String name, SQLiteDatabase metadata, String defaultValue) {
        String value = defaultValue;
        Cursor cursor = metadata.query("metadata", new String[] { "value" },
                "name = ? or name = ?", new String[] { name, name + "_" + Locale.getDefault().toString() },
                null, null, "name desc", "1");
        while (cursor != null && cursor.moveToNext()) {
            value = cursor.getString(cursor.getColumnIndexOrThrow("value"));
            cursor.close();
            break;
        }
        return value;
    }

    public String getVersionFullname(String version) {
        version = version.toLowerCase(Locale.US);
        String fullname = getResourceValue(versionFullnames, version.replace("demo", "").replace("niv84", "niv1984"));
        if (version.endsWith("demo")) {
            fullname += "(" + mContext.getString(R.string.demo) + ")";
        }
        return fullname;
    }

    public String getVersionName(String version) {
        version = version.toLowerCase(Locale.US);
        return getResourceValue(versionNames, version.replace("demo", "").replace("niv84", "niv1984")).toUpperCase(Locale.US);
    }

    private String getResourceValue(HashMap<String, String> map, String key) {
        return map.containsKey(key) ? map.get(key) : key;
    }

    private void setResourceValues(HashMap<String, String> map, int resId) {
        map.clear();
        for (String entry: mContext.getResources().getStringArray(resId)) {
            String[] strings = entry.split("\\|", 2);
            map.put(strings[0], strings[1]);
        }
    }

    private void setResourceValuesReverse(HashMap<String, String> map, int resId) {
        for (String entry: mContext.getResources().getStringArray(resId)) {
            String[] strings = entry.split("\\|", 2);
            if (strings.length > 1) {
                map.put(strings[1], strings[0]);
            }
        }
    }

    private void setResources() {
        Log.d(TAG, "setResources");
        setResourceValues(versionNames, R.array.versionname);
        setResourceValues(versionFullnames, R.array.versionfullname);
        setResourceValues(osisZHCN, R.array.osiszhcn);
        setResourceValues(osisZHTW, R.array.osiszhtw);
        setResourceValuesReverse(allosis, R.array.osiszhcn);
        setResourceValuesReverse(allosis, R.array.osiszhtw);
        setResourceValuesReverse(searchfull, R.array.searchfullzhcn);
        setResourceValuesReverse(searchshort, R.array.searchshortzhcn);
    }

    private void setDemoVersions() {
        int demoVersion = PreferenceManager.getDefaultSharedPreferences(mContext).getInt("demoVersion", 0);
        int versionCode = 0;
        try {
            versionCode = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
        }
        boolean newVersion = (demoVersion != versionCode);
        boolean unpack = unpackRaw(newVersion, R.raw.niv84demo, new File(mContext.getFilesDir(), "niv84demo.sqlite3"));
        if (unpack) {
            unpack = unpackRaw(newVersion, R.raw.cunpssdemo, new File(mContext.getFilesDir(), "cunpssdemo.sqlite3"));
        }
        if (newVersion && unpack) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("demoVersion", versionCode).commit();
        }
    }

    public boolean setDefaultVersion() {
        checkVersions();
        if (versions.size() == 0) {
            return false;
        }
        String version = PreferenceManager.getDefaultSharedPreferences(mContext).getString("version", get(TYPE.VERSION, 0));
        // check actual file
        File file = getFile(version);
        if (file != null && file.isFile()) {
            return setVersion(version);
        }
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove("version").commit();
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
        if (maps.size() > 0) {
            for (String osis : maps.get(0).values()) {
                if (osis.equalsIgnoreCase(book)) {
                    return osis;
                }
            }
        }

        book = book.toLowerCase(Locale.US).replace(" ", "");
        // prefer fullname
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.US).replace(" ", "").equals(book)) {
                    return entry.getValue();
                }
            }
        }

        // prefer startswith
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.US).replace(" ", "").startsWith(book)) {
                    return entry.getValue();
                }
            }
        }

        // prefer contains
        for (LinkedHashMap<String, String> map: maps) {
            for (Entry<String, String> entry: map.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.US).replace(" ", "").contains(book)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    private ArrayList<LinkedHashMap<String, String>> getMaps(TYPE type) {
        checkLocale();
        ArrayList<LinkedHashMap<String, String>> maps = new ArrayList<LinkedHashMap<String, String>>();
        if (type == TYPE.HUMAN) {
            maps.add(allhuman);
            maps.add(searchfull);
            maps.add(searchshort);
        } else {
            maps.add(allosis);
        }
        return maps;
    }
    /*
     * 根据book获取osis，用于查询
     *
     */
    public String getOsis(String book) {
        String osis;

        if (book == null || "".equals(book)) {
            return null;
        }

        book = book.replace("约一", "约壹");
        book = book.replace("约二", "约贰");
        book = book.replace("约三", "约叁");
        book = book.toLowerCase(Locale.US);
        book = book.replace("psalms", "psalm");

        boolean checkOsis = true;
        if (isCJK(book.substring(0, 1)) && book.length() > 2) {
            checkOsis = false;
        } else if (book.length() > 6) { // max 1Thess, 2Thess
            checkOsis = false;
        }

        Log.d(TAG, "getOsis, book: " + book);

        if (checkOsis) {
            osis = getOsis(book, getMaps(TYPE.OSIS));
            if (osis != null) {
                return osis;
            }
        }

        osis = getOsis(book, getMaps(TYPE.HUMAN));
        if (osis != null) {
            return osis;
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
            String text = get(TYPE.HUMAN, bible.getPosition(TYPE.OSIS, osis));
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
            book = book.replace(SearchManager.SUGGEST_URI_PATH_QUERY, "").replace(" ", "").toLowerCase(Locale.US);
        }

        Log.d(TAG, "book: " + book);

        ArrayList<Entry<String, String>> maps = new ArrayList<Entry<String, String>>();

        for (Entry<String, String> entry: searchshort.entrySet()) {
            maps.add(entry);
        }

        for (Entry<String, String> entry: searchfull.entrySet()) {
            maps.add(entry);
        }

        for (LinkedHashMap<String, String> map: getMaps(TYPE.HUMAN)) {
            for (Entry<String, String> entry: map.entrySet()) {
                maps.add(entry);
            }
        }

        for (LinkedHashMap<String, String> map: getMaps(TYPE.OSIS)) {
            for (Entry<String, String> entry: map.entrySet()) {
                maps.add(entry);
            }
        }

        if (book == null || "".equals(book)) {
            for (int i = 0; i < this.osiss.size() && i < limit; ++i) {
                osiss.put(humans.get(i), this.osiss.get(i));
            }
            return osiss;
        }

        for (Entry<String, String> entry: maps) {
            if (checkStartSuggest(osiss, entry.getValue(), entry.getValue(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry: maps) {
            if (checkContainSuggest(osiss, entry.getValue(), entry.getValue(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry: maps) {
            if (checkStartSuggest(osiss, entry.getKey(), entry.getValue(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry: maps) {
            if (checkContainSuggest(osiss, entry.getKey(), entry.getValue(), book, limit)) {
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
                osis = entry.getValue();
                chapter = "0";
            }
        }

        if ("".equals(osis)) {
            return osiss;
        }

        String bookname = get(TYPE.HUMAN, bible.getPosition(TYPE.OSIS, osis));
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

    public static Object getField(Object object, final Class<?> clazz, final String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            Log.e(TAG, "no such filed " + object.getClass().getName() + "." + fieldName);
        }
        return null;
    }

    public static Object getField(Object object, final String fieldName) {
        if (object == null) {
            return null;
        }
        return getField(object, object.getClass(), fieldName);
    }

    public boolean deleteVersion(String version, Runnable run) {
        boolean returncode = false;
        version = version.toLowerCase(Locale.US);
        File file = getFile(version);
        if (file != null && file.isFile() && file.delete()) {
            checkBibleData(true);
            if (version.equalsIgnoreCase(databaseVersion)) {
                setVersion(get(TYPE.VERSION, 0));
            }
            returncode = true;
        }
        if (run != null) {
            run.run();
        }
        return returncode;
    }

    @SuppressLint("NewApi")
    void checkBibleData() {
        checkApkData();
        checkZipData(Environment.getExternalStorageDirectory());
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR_MR1) {
            checkZipData(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS));
        } else {
            checkZipData(new File(Environment.getExternalStorageDirectory(), "Download"));
        }
        checkVersions();
    }

    public void checkBibleData(boolean block, final Runnable run) {
        if (block) {
            checkBibleData();
            if (run != null) {
                run.run();
            }
        } else {
            new Thread(new Runnable() {
                public void run() {
                    checkBibleData();
                    if (run != null) {
                        run.run();
                    }
                }
            }).start();
        }
    }

    public void checkBibleData(boolean block) {
        checkBibleData(block, null);
    }

    private boolean checkZipData(File path) {
        if (path == null || !path.isDirectory() || path.list() == null) {
            return false;
        }
        Log.d(TAG, "checking zipdata " + path.getAbsolutePath());
        SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(mContext);
        Long oldmtime = mtime.get(path.getAbsolutePath());
        if (oldmtime == null) {
            oldmtime = preference.getLong(path.getAbsolutePath(), 0);
        }
        boolean newer = (path.lastModified() > oldmtime);
        for (String name : path.list()) {
            if (name.startsWith("bibledata-") && name.endsWith("zip")) {
                try {
                    unpackZip(new File(path, name), newer);
                } catch (IOException e) {
                } catch (Exception e) {
                    Log.e(TAG, "unpackZip", e);
                }
            }
        }
        mtime.put(path.getAbsolutePath(), path.lastModified());
        preference.edit().putLong(path.getAbsolutePath(), path.lastModified()).commit();
        return true;
    }

    private boolean unpackZip(File path, boolean newer) throws IOException {
        if (path == null || !path.isFile()) {
            return false;
        }

        File dirpath = getExternalFilesDirWrapper();

        // bibledata-zh-cn-version.zip
        String filename = path.getAbsolutePath();
        int sep = filename.lastIndexOf("-");
        if (sep != -1) {
            filename = filename.substring(sep + 1, filename.length() - 4);
        }
        filename += ".sqlite3";

        if (!newer && new File(dirpath, filename).isFile()) {
            return true;
        }

        // test for zip completeness
        ZipFile zipfile = null;
        try {
            zipfile = new ZipFile(path);
        } catch (Exception e) {
            Log.e(TAG, "illegal zipfile " + path.getAbsolutePath() + ": " + e.getMessage());
            return false;
        } finally {
            if (zipfile != null) {
                zipfile.close();
            }
        }

        InputStream is = new FileInputStream(path);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String zename = ze.getName();
                if (zename == null || !zename.endsWith((".sqlite3"))) {
                    continue;
                }
                sep = zename.lastIndexOf(File.separator);
                if (sep != -1) {
                    zename = zename.substring(sep + 1);
                }
                File file = new File(dirpath, zename);
                if (file.exists() && file.lastModified() > ze.getTime()) {
                    continue;
                }
                Log.d(TAG, "unpacking " + file.getAbsoluteFile());
                int length;
                File tmpfile = new File(dirpath, zename + ".tmp");
                OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpfile));
                byte[] buffer = new byte[8192];
                int zero = 0;
                while ((length = zis.read(buffer)) != -1) {
                    if (length == 0) {
                        ++zero;
                        if (zero > 3) {
                            break;
                        }
                    } else {
                        zero = 0;
                    }
                    os.write(buffer, 0, length);
                }
                os.close();
                if (zero > 3) {
                    // must do this, otherwise block forever
                    is.close();
                    return false;
                } else {
                    tmpfile.renameTo(file);
                    path.delete();
                    return true;
                }
            }
        } finally {
            zis.close();
        }
        return false;
    }

    private synchronized void checkApkData() {
        Log.d(TAG, "checking apkdata");
        try {
            String packageName = mContext.getPackageName();
            PackageManager pm = mContext.getPackageManager();
            SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(mContext);
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            for (String applicationName : pm.getPackagesForUid(ai.uid)) {
                if (packageName.equals(applicationName)) {
                    continue;
                }

                // version
                String version = applicationName.replace(packageName + ".", "");

                // resources
                Resources resources = mContext.createPackageContext(applicationName, Context.CONTEXT_IGNORE_SECURITY).getResources();

                // newVersion
                int versionCode = pm.getPackageInfo(applicationName, 0).versionCode;
                boolean newVersion = (preference.getInt(version, 0) != versionCode);

                // resid
                int resid = resources.getIdentifier("a", "raw", applicationName);
                if (resid == 0) {
                    resid = resources.getIdentifier("xa", "raw", applicationName);
                }
                if (resid == 0) {
                    Log.d(TAG, "package " + applicationName + " has no R.raw.a nor R.raw.xa");
                    continue;
                }

                // file
                File file = new File(getExternalFilesDirWrapper(), version + ".sqlite3");
                if (file.exists() && !file.isFile()) {
                    file.delete();
                }

                boolean unpack = unpackRaw(resources, newVersion, resid, file);
                if (newVersion && unpack) {
                    preference.edit().putInt(version, versionCode).commit();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private boolean unpackRaw(Resources resources, boolean newVersion, int resid, File file) {
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return false;
        }

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
            File tmpfile = new File(file.getAbsolutePath() + ".tmp");
            OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpfile));
            for (int i=0; i<20; i++) {
                InputStream is = resources.openRawResource(resid + i);
                while((length = is.read(buffer)) >= 0) {
                    os.write(buffer, 0, length);
                }
                is.close();
            }
            os.close();
            tmpfile.renameTo(file);
        } catch (Exception e) {
            Log.e(TAG, "unpacked " + file.getAbsolutePath(), e);
            return false;
        }

        return true;
    }

    void checkVersion(File path) {
        if (!path.exists() || !path.isDirectory() || path.list() == null) {
            return;
        }
        String[] names = path.list();
        for (String name : names) {
            File file = new File(path, name);
            if (name.endsWith(".sqlite3") && file.exists() && file.isFile()) {
                Log.d(TAG, "add version " + name);
                String version = name.toLowerCase(Locale.US).replace(".sqlite3", "");
                if (!versions.contains(version)) {
                    checkVersionMeta(file, version);
                }
                versions.add(version);
                versionpaths.put(version.toLowerCase(Locale.US), file.getAbsolutePath());
                if (version.equalsIgnoreCase("niv2011")) {
                    versionpaths.put("niv", file.getAbsolutePath());
                } else if (version.equalsIgnoreCase("niv1984")) {
                    versionpaths.put("niv84", file.getAbsolutePath());
                }
            }
        }
    }

    private void checkVersionMeta(File file, String version) {
        try {
            SQLiteDatabase metadata = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            String dataversion = version.replace("demo", "");
            if (!versionFullnames.containsKey(version)) {
                versionFullnames.put(version, getVersionMetadata("fullname", metadata, dataversion));
            }
            if (!versionNames.containsKey(version)) {
                versionNames.put(version, getVersionMetadata("name", metadata, dataversion));
            }
            setMetadata(metadata, dataversion);
            metadata.close();
        } catch (Exception e) {
            try {
                file.delete();
            } catch (Exception f) {
            }
        }
    }

    public static final String BIBLEDATA_PREFIX = "http://github.com/liudongmiao/bibledata/raw/master/";

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public long download(String filename) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return 0;
        }
        if (getExternalFilesDirWrapper() == null) {
            return 0;
        }
        String url = BIBLEDATA_PREFIX + filename;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(filename);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        DownloadManager dm = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        return dm.enqueue(request);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public void cancel(long id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return;
        }
        DownloadManager dm = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        dm.remove(id);
    }

    public Context getContext() {
        return mContext;
    }

    public static final String JSON = "versions.json";

    String getLocalVersions() throws IOException {
        InputStream is = null;
        File file = new File(mContext.getFilesDir(), JSON);
        if (file.isFile()) {
            is = new FileInputStream(file);
        } else {
            is = mContext.getResources().openRawResource(R.raw.versions);
        }
        String json = getStringFromInputStream(is);
        is.close();
        return json;
    }

    String getRemoteVersions() throws ClientProtocolException, IOException {
        HttpClient client = new DefaultHttpClient();
        SharedPreferences sp = mContext.getSharedPreferences("json", Context.MODE_PRIVATE);
        String etag = sp.getString(JSON + "_etag", null);
        HttpGet get = new HttpGet(Bible.BIBLEDATA_PREFIX + JSON);
        if (etag != null) {
            get.addHeader("If-None-Match", etag);
        }
        HttpResponse response = client.execute(get);
        if (response.getStatusLine().getStatusCode() == 304) {
            Log.d(TAG, JSON + " not modified");
            return null;
        }
        InputStream is = response.getEntity().getContent();
        String json = getStringFromInputStream(is);
        is.close();
        try {
            new JSONObject(json);
        } catch (JSONException e) {
            Log.d(TAG, "json: " + json);
            return null;
        }
        try {
            Header header = response.getFirstHeader("ETag");
            if (header != null) {
                sp.edit().putString(JSON + "_etag", header.getValue()).commit();
            }
            File tmpfile = new File(mContext.getFilesDir(), JSON + ".tmp");
            OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpfile));
            os.write(json.getBytes("UTF-8"));
            os.close();
            tmpfile.renameTo(new File(mContext.getFilesDir(), JSON));
        } catch (IOException e) {
        }
        return json;
    }

    String getStringFromInputStream(InputStream is) throws IOException {
        int length;
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        while ((length = is.read(buffer)) >= 0) {
            bao.write(buffer, 0, length);
        }
        return bao.toString();
    }

    public void email(Context context) {
        email(context, null);
    }

    public void email(Context context, String content) {
        StringBuffer subject = new StringBuffer();
        subject.append(context.getString(R.string.app_name));
        if (versionName != null) {
            subject.append(" ");
            subject.append(versionName);
        }
        subject.append("(Android ");
        subject.append(Locale.getDefault().toString());
        subject.append("-");
        subject.append(Build.VERSION.RELEASE);
        subject.append(")");
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:liudongmiao@gmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject.toString());
        if (content != null) {
            intent.putExtra(Intent.EXTRA_TEXT, content);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
        }
    }
}
