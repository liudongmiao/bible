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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

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
import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import me.piebridge.bible.utils.LogUtils;

public class Bible {

    private final static String TAG = "me.piebridge.bible$Bible";

    public static final String INTRO = "int";

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
    private final Object versionsLock = new Object();
    private final Object versionsCheckingLock = new Object();
    private final HashMap<String, String> versionpaths = new HashMap<String, String>();
    private ArrayList<String> humans = new ArrayList<String>();

    private static Bible bible = null;

    private HashMap<String, String> versionNames = new HashMap<String, String>();
    private HashMap<String, String> versionDates = new HashMap<String, String>();
    private HashMap<String, String> versionFullnames = new HashMap<String, String>();

    private String annotationOsis = null;
    private String noteOsis = null;
    private HashMap<String, Note> notes = new HashMap<String, Note>();
    private HashMap<String, String> annotations = new HashMap<String, String>();

    private LinkedHashMap<String, String> allhuman = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> allosis = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> searchfull = new LinkedHashMap<String, String>();
    private LinkedHashMap<String, String> searchshort = new LinkedHashMap<String, String>();

    private Collator collator;
    private Locale lastLocale;
    private boolean unpacked = false;
    private HashMap<String, Long> mtime = new HashMap<String, Long>();
    private String css;
    public String versionName;

    private static String HUMAN_PREFERENCE = "human";

    private Bible(Context context) {
        Log.d(TAG, "init bible");
        mContext = context;
        SharedPreferences preferences = mContext.getSharedPreferences(HUMAN_PREFERENCE, 0);
        for (Entry<String, ?> entry : preferences.getAll().entrySet()) {
            allhuman.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
        }
        updateLocale();
        setDefaultVersion();
    }

    public void updateLocale() {
        Locale locale = Locale.getDefault();
        collator = Collator.getInstance(locale);
        if (!locale.equals(lastLocale)) {
            lastLocale = locale;
            updateResources();
        }
    }

    public synchronized static Bible getInstance(Context context) {
        if (bible == null) {
            bible = new Bible(context);
        }
        if (context != null) {
            mContext = context;
            bible.updateLocale();
        }
        return bible;
    }

    public int[] getChapterVerse(String string) {
        int value = new BigDecimal(string).multiply(new BigDecimal(Provider.THOUSAND)).intValue();
        int chapter = value / Provider.THOUSAND;
        int verse = value % Provider.THOUSAND;
        return new int[]{chapter, verse};
    }

    public List<Integer> getVerses(String book, int chapter) {
        Cursor cursor = null;
        List<Integer> verses = new ArrayList<Integer>();
        try {
            cursor = database.query(Provider.TABLE_VERSE, Provider.COLUMNS_VERSE,
                    "book = ? and verse > ? and verse < ?",
                    new String[] {book, String.valueOf(chapter), String.valueOf(chapter + 1)},
                    null, null, "id");
            BigDecimal thousand = new BigDecimal(Provider.THOUSAND);
            while (cursor.moveToNext()) {
                String verse = cursor.getString(0);
                LogUtils.d("verse: " + verse);
                verses.add(new BigDecimal(verse).multiply(thousand).intValue() % Provider.THOUSAND);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        LogUtils.d(verses.toString());
        return verses;
    }

    private File getPath(File[] dirs) {
        File path = dirs[dirs.length - 1];
        if (path == null) {
            return dirs[0];
        } else {
            return path;
        }
    }

    private int checkVersionsSync(boolean all) {
        List<String> newVersions = new ArrayList<String>();
        Map<String, String> newVersionpaths = new HashMap<String, String>();
        File[] dirs = getExternalFilesDirWrapper();
        if (dirs.length == 0) {
            checkInternalVersions(newVersions, newVersionpaths, all);
            synchronized (versionsLock) {
                mtime.clear();
                versions.clear();
                versionpaths.clear();
                versions.addAll(newVersions);
                versionpaths.putAll(newVersionpaths);
            }
            return versions.size();
        }
        File path = getPath(dirs);
        File oldpath = new File(Environment.getExternalStorageDirectory(), ".piebridge");
        Long oldmtime = mtime.get(path.getAbsolutePath());
        if (oldmtime == null) {
            oldmtime = 0L;
        }
        if (versions.size() != 0 && path.lastModified() <= oldmtime
                && (!oldpath.exists() || !oldpath.isDirectory() || oldpath.lastModified() <= oldmtime)) {
            return versions.size();
        }
        checkVersion(oldpath, newVersions, newVersionpaths, all);
        for (File dir : dirs) {
            if (dir != null) {
                checkVersion(dir, newVersions, newVersionpaths, all);
            }
        }
        Collections.sort(newVersions, new Comparator<String>() {
            @Override
            public int compare(String item1, String item2) {
                return collator.compare(getVersionFullname(item1), getVersionFullname(item2));
            }
        });
        if (newVersions.size() == 0) {
            checkInternalVersions(newVersions, newVersionpaths, all);
        }
        synchronized (versionsLock) {
            mtime.put(path.getAbsolutePath(), path.lastModified());
            versions.clear();
            versionpaths.clear();
            versions.addAll(newVersions);
            versions.retainAll(newVersions);
        }
        return versions.size();
    }

    private void showNewVersion(String version) {
        if (!versionpaths.containsKey(version)) {
            String text = mContext.getString(R.string.new_version, version.toUpperCase(Locale.US));
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }

    private void checkInternalVersions(List<String> versions, Map<String, String> versionpaths, boolean all) {
        if (!unpacked) {
            setDemoVersions();
            unpacked = true;
        }
        checkVersion(mContext.getFilesDir(), versions, versionpaths, all);
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
            int oldsize = allhuman.size();
            setMetadata(database, databaseVersion, true);
            if (allhuman.size() > oldsize) {
                SharedPreferences.Editor editor = mContext.getSharedPreferences(HUMAN_PREFERENCE, 0).edit();
                for (Entry<String, String> entry : allhuman.entrySet()) {
                    editor.putString(entry.getKey(), entry.getValue());
                }
                editor.commit();
            }
            return true;
        } catch (Exception e) {
            try {
                file.delete();
            } catch (Exception f) {
            }
            return setDefaultVersion();
        }
    }

    private File getFile(File dir, String version) {
        Log.d(TAG, "directory: " + dir + ", version: " + version);
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File path = new File(dir, version + ".sqlite3");
        if (!path.isFile()) {
            return null;
        }
        return path;
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
            File file;
            for (File dir : getExternalFilesDirWrapper()) {
                file = getFile(dir, version);
                Log.d(TAG, "version: " + version);
                if (file != null) {
                    versionpaths.put(version, file.getAbsolutePath());
                    return file;
                }
            }
            file = getFile(new File(Environment.getExternalStorageDirectory(), ".piebridge"), version);
            if (file != null) {
                versionpaths.put(version, file.getAbsolutePath());
                return file;
            }
            return getFile(mContext.getFilesDir(), version);
        }
    }

    private void setMetadata(SQLiteDatabase metadata, String dataversion, boolean change) {
        Cursor cursor = metadata.query(Provider.TABLE_BOOKS, Provider.COLUMNS_BOOKS, null, null, null, null, null);
        if (change) {
            osiss.clear();
            books.clear();
            chapters.clear();
            humans.clear();
        }
        try {
            while (cursor.moveToNext()) {
                String osis = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_OSIS));
                String book = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN));
                String chapter = cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CHAPTERS));

                if (book.endsWith(" 1")) {
                    book = book.substring(0, book.length() - 2);
                }
                if (!allhuman.containsKey(book)) {
                    allhuman.put(book, osis);
                }

                Cursor cursor_chapter = null;
                // select group_concat(replace(reference_osis, "Gen.", "")) as osis from chapters where reference_osis like 'Gen.%';
                try {
                    cursor_chapter = metadata.query(Provider.TABLE_CHAPTERS,
                            new String[]{"group_concat(replace(reference_osis, \"" + osis + ".\", \"\")) as osis"},
                            "reference_osis like ?", new String[]{osis + ".%"}, null, null, null);
                    if (cursor_chapter.moveToNext()) {
                        // we have only one column
                        chapter = sortChapter(cursor_chapter.getString(0)).toLowerCase(Locale.US);
                    }
                } catch (Exception e) {
                } finally {
                    if (cursor_chapter != null) {
                        cursor_chapter.close();
                    }
                }
                if (change) {
                    osiss.add(osis);
                    books.add(book);
                    chapters.add(chapter);
                    humans.add(book);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        css = getVersionMetadata("css", metadata, "");
    }

    private String sortChapter(String chapter) {
        String[] chapters = chapter.split(",");
        Arrays.sort(chapters, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int l;
                int r;
                try {
                    l = Integer.parseInt(left);
                } catch (NumberFormatException e) {
                    l = 0;
                }
                try {
                    r = Integer.parseInt(right);
                } catch (NumberFormatException e) {
                    r = 0;
                }
                if (l == 0 && r == 0) {
                    // shouldn't happend
                    return left.compareTo(right);
                } else {
                    return l - r;
                }
            }
        });
        StringBuilder sb = new StringBuilder(chapter.length());
        int length = chapters.length;
        for (int i = 0; i < length; ++i) {
            sb.append(chapters[i]);
            if (i < length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
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
            } catch (IOException ioe) {
            }
        }
        return file;
    }

    private File[] getExternalFilesDirWrapper() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) || mContext == null) {
            Log.d(TAG, "not mounted, mContext = " + mContext);
            return new File[0];
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return mContext.getExternalFilesDirs(null);
        } else {
            return new File[] {mContext.getExternalFilesDir(null)};
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
            default:
                return new ArrayList<String>();
        }
    }

    public String getDate(String version) {
        return versionDates.get(version);
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
        Cursor cursor = metadata.query("metadata", new String[]{"value"},
                "name = ? or name = ?", new String[]{name, name + "_" + Locale.getDefault().toString()},
                null, null, "name desc", "1");
        while (cursor != null && cursor.moveToNext()) {
            value = cursor.getString(cursor.getColumnIndexOrThrow("value"));
            break;
        }
        if (cursor != null) {
            cursor.close();
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
        for (String entry : mContext.getResources().getStringArray(resId)) {
            String[] strings = entry.split("\\|", 2);
            map.put(strings[0], strings[1]);
        }
    }

    private void setResourceValuesReverse(HashMap<String, String> map, int resId) {
        for (String entry : mContext.getResources().getStringArray(resId)) {
            String[] strings = entry.split("\\|", 2);
            if (strings.length > 1) {
                map.put(strings[1], strings[0]);
            }
        }
    }

    private void updateResources() {
        Log.d(TAG, "updateResources");
        setResourceValues(versionNames, R.array.versionname);
        setResourceValues(versionFullnames, R.array.versionfullname);
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
        String defaultVersion = "";
        if (versions.size() > 0) {
            defaultVersion = get(TYPE.VERSION, 0);
        }
        String version = PreferenceManager.getDefaultSharedPreferences(mContext).getString("version", defaultVersion);
        if ((version == null || version.length() == 0) && defaultVersion.length() > 0) {
            version = defaultVersion;
        }
        // check actual file
        File file = getFile(version);
        if (file != null && file.isFile()) {
            return setVersion(version);
        }
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove("version").commit();
        if (TextUtils.isEmpty(version) || version.endsWith("demo")) {
            checkBibleData(false);
            setDefaultVersion();
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
            byte[] buffer = new byte[8192];
            OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
            InputStream is = mContext.getResources().openRawResource(resId);
            while ((length = is.read(buffer)) >= 0) {
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
        for (LinkedHashMap<String, String> map : maps) {
            for (Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.US).replace(" ", "").equals(book)) {
                    return entry.getValue();
                }
            }
        }

        // prefer startswith
        for (LinkedHashMap<String, String> map : maps) {
            for (Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.US).replace(" ", "").startsWith(book)) {
                    return entry.getValue();
                }
            }
        }

        // prefer contains
        for (LinkedHashMap<String, String> map : maps) {
            for (Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.US).replace(" ", "").contains(book)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    private ArrayList<LinkedHashMap<String, String>> getMaps(TYPE type) {
        updateLocale();
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

        for (Entry<String, String> entry : searchshort.entrySet()) {
            maps.add(entry);
        }

        for (Entry<String, String> entry : searchfull.entrySet()) {
            maps.add(entry);
        }

        for (LinkedHashMap<String, String> map : getMaps(TYPE.HUMAN)) {
            for (Entry<String, String> entry : map.entrySet()) {
                maps.add(entry);
            }
        }

        for (LinkedHashMap<String, String> map : getMaps(TYPE.OSIS)) {
            for (Entry<String, String> entry : map.entrySet()) {
                maps.add(entry);
            }
        }

        if (book == null || "".equals(book)) {
            for (int i = 0; i < this.osiss.size() && i < limit; ++i) {
                osiss.put(humans.get(i), this.osiss.get(i));
            }
            return osiss;
        }

        for (Entry<String, String> entry : maps) {
            if (checkStartSuggest(osiss, entry.getValue(), entry.getValue(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry : maps) {
            if (checkContainSuggest(osiss, entry.getValue(), entry.getValue(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry : maps) {
            if (checkStartSuggest(osiss, entry.getKey(), entry.getValue(), book, limit)) {
                return osiss;
            }
        }

        for (Entry<String, String> entry : maps) {
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
            for (Entry<String, String> entry : osiss.entrySet()) {
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
        for (int i = chapternum * 10; i <= maxchapter && i < 10 * chapternum + 10; i++) {
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

    public boolean deleteVersion(String version) {
        boolean returncode = false;
        version = version.toLowerCase(Locale.US);
        File file = getFile(version);
        if (file != null && file.isFile() && file.delete()) {
            returncode = true;
        }
        synchronized (versionsLock) {
            Iterator<String> it = versions.iterator();
            while (it.hasNext()) {
                if (version.equals(it.next())) {
                    it.remove();
                }
            }
            versionpaths.remove(version);
        }
        checkBibleData(false, null);
        if (version.equalsIgnoreCase(databaseVersion)) {
            setVersion(get(TYPE.VERSION, 0));
        }
        return returncode;
    }

    @SuppressLint("NewApi")
    private void checkBibleData(boolean all) {
        synchronized (versionsCheckingLock) {
            if ((!checking || !all) && versions.size() > 0) {
                Log.d(TAG, "cancel checking");
                return;
            }
            if (!all) {
                if (checkVersionsSync(false) > 0) {
                    return;
                }
            }
            checkZipData(Environment.getExternalStorageDirectory());
            checkZipData(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS));
            checkVersionsSync(true);
        }
    }

    private volatile boolean checking = false;
    private volatile boolean checked = false;

    public void checkBibleData(boolean block, final Runnable run) {
        checking = true;
        if (block && !checked) {
            checkBibleData(true);
            if (run != null) {
                run.run();
            }
            checking = false;
            checked = true;
        } else {
            new Thread(new Runnable() {
                public void run() {
                    checkBibleData(true);
                    if (run != null) {
                        run.run();
                    }
                    checking = false;
                    checked = true;
                }
            }).start();
        }
    }

    private boolean checkZipData(File path) {
        if (path == null || !path.isDirectory() || path.list() == null) {
            return false;
        }
        Log.d(TAG, "checking zipdata " + path.getAbsolutePath());
        for (String name : path.list()) {
            if (name.startsWith("bibledata-") && name.endsWith("zip")) {
                checkZipPath(new File(path, name));
            }
        }
        return true;
    }

    public void checkZipPath(File path) {
        try {
            unpackZip(path);
        } catch (IOException e) {
        } catch (Exception e) {
            Log.e(TAG, "unpackZip", e);
        }
    }

    private boolean unpackZip(File path) throws IOException {
        if (path == null || !path.isFile()) {
            return false;
        }

        File[] dirs = getExternalFilesDirWrapper();
        File dirpath = getPath(dirs);

        InputStream is = new FileInputStream(path);
        long fileSize = path.length();
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                long zeSize = ze.getCompressedSize();
                // zip is incomplete
                if (fileSize < zeSize) {
                    break;
                }
                String zename = ze.getName();
                if (zename == null || !zename.endsWith((".sqlite3"))) {
                    continue;
                }
                int sep = zename.lastIndexOf(File.separator);
                if (sep != -1) {
                    zename = zename.substring(sep + 1);
                }
                File file;
                String version = zename.toLowerCase(Locale.US).replace(".sqlite3", "");
                if (versionpaths.containsKey(version)) {
                    file = new File(versionpaths.get(version));
                } else {
                    file = new File(dirpath, zename);
                }
                if (file.exists() && file.lastModified() > ze.getTime() && file.lastModified() > path.lastModified()) {
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
                    return false;
                } else {
                    tmpfile.renameTo(file);
                    path.delete();
                    return true;
                }
            }
        } finally {
            is.close();
            zis.close();
        }
        return false;
    }

    int checkVersion(File path, List<String> versions, Map<String, String> versionpaths, boolean all) {
        if (!path.exists() || !path.isDirectory() || path.list() == null) {
            return versions.size();
        }
        String[] names = path.list();
        for (String name : names) {
            if (!all && versions.size() > 0) {
                break;
            }
            File file = new File(path, name);
            if (name.endsWith(".sqlite3") && file.exists() && file.isFile()) {
                Log.d(TAG, "add version " + name);
                String version = name.toLowerCase(Locale.US).replace(".sqlite3", "");
                if (!checkVersionMeta(file, version)) {
                    continue;
                }
                if (!versions.contains(version)) {
                    versions.add(version);
                }
                versionpaths.put(version, file.getAbsolutePath());
                if (version.equalsIgnoreCase("niv2011")) {
                    versionpaths.put("niv", file.getAbsolutePath());
                } else if (version.equalsIgnoreCase("niv1984")) {
                    versionpaths.put("niv84", file.getAbsolutePath());
                }
            }
        }
        return versions.size();
    }

    @SuppressLint("NewApi")
    private boolean isDatabaseIntegrityOk(SQLiteDatabase database) {
        // assume ok if the api is not available
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return true;
        } else {
            return database.isDatabaseIntegrityOk();
        }
    }

    private boolean checkVersionMeta(File file, String version) {
        SQLiteDatabase metadata = null;
        try {
            metadata = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            String dataversion = version.replace("demo", "");
            if (!versionFullnames.containsKey(version)) {
                versionFullnames.put(version, getVersionMetadata("fullname", metadata, dataversion));
            }
            if (!versionNames.containsKey(version)) {
                versionNames.put(version, getVersionMetadata("name", metadata, dataversion));
            }
            versionDates.put(version, getVersionMetadata("date", metadata, "0"));
            // setMetadata(metadata, dataversion, false);
            return true;
        } catch (Exception e) {
            try {
                file.delete();
            } catch (Exception f) {
            }
            return false;
        } finally {
            if (metadata != null) {
                metadata.close();
            }
        }
    }

    public static final String BIBLEDATA_PREFIX = "http://github.com/liudongmiao/bibledata/raw/master/";

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public DownloadInfo download(String filename) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return null;
        }
        if (getExternalFilesDirWrapper() == null) {
            return null;
        }
        String url = BIBLEDATA_PREFIX + filename;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(filename);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        DownloadManager dm = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        return DownloadInfo.getDownloadInfo(mContext, dm.enqueue(request));
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

    @SuppressWarnings("deprecation")
    String getRemoteVersions() throws IOException {
        org.apache.http.client.HttpClient client = new org.apache.http.impl.client.DefaultHttpClient();

        SharedPreferences sp = mContext.getSharedPreferences("json", 0);
        String etag = sp.getString(JSON + "_etag", null);

        org.apache.http.client.methods.HttpGet get = new org.apache.http.client.methods.HttpGet(Bible.BIBLEDATA_PREFIX + JSON);
        if (etag != null) {
            get.addHeader("If-None-Match", etag);
        }
        org.apache.http.HttpResponse response = client.execute(get);
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

        org.apache.http.Header header = response.getFirstHeader("ETag");
        if (header != null) {
            saveJson(sp, header.getValue(), json);
        }
        return json;
    }

    private void saveJson(SharedPreferences sp, String header, String json) throws IOException {
        if (header != null) {
            sp.edit().putString(JSON + "_etag", header).commit();
        }
        File file = new File(mContext.getFilesDir(), JSON + ".tmp");
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
        os.write(json.getBytes("UTF-8"));
        os.close();
        file.renameTo(new File(mContext.getFilesDir(), JSON));
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

    public void loadAnnotations(String osis, boolean load) {
        if (TextUtils.isEmpty(osis) || osis.equals(annotationOsis)) {
            return;
        }
        annotations.clear();
        if (!load) {
            return;
        }
        annotationOsis = osis;
        Cursor cursor = null;
        try {
            cursor = database.query("annotations", new String[]{"link", "content"}, "osis = ?",
                    new String[]{osis}, null, null, null, null);
            while (cursor.moveToNext()) {
                String link = cursor.getString(0);
                String content = cursor.getString(1);
                annotations.put(link, content);
            }
        } catch (SQLiteException e) {
            // ignore
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getAnnotation(String link) {
        return annotations.get(link);
    }

    /**
     * load notes, called when osis is changed
     *
     * @param osis book.chapter
     */
    public void loadNotes(String osis) {
        if (osis == null) {
            return;
        }
        if (this.noteOsis != null && this.noteOsis.equals(osis)) {
            return;
        }
        this.noteOsis = osis;
        notes.clear();
        if (mOpenHelper == null) {
            mOpenHelper = new AnnotationsDatabaseHelper(mContext);
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return;
        }

        Cursor cursor = null;
        try {
            cursor = db.query(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, null, "osis = ? and type = ?", new String[]{
                    osis, "note"}, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                long id = cursor.getInt(cursor.getColumnIndex(AnnotationsDatabaseHelper.COLUMN_ID));
                String verse = cursor.getString(cursor.getColumnIndex(AnnotationsDatabaseHelper.COLUMN_VERSE));
                String verses = cursor.getString(cursor.getColumnIndex(AnnotationsDatabaseHelper.COLUMN_VERSES));
                String content = cursor.getString(cursor.getColumnIndex(AnnotationsDatabaseHelper.COLUMN_CONTENT));
                Long create = cursor.getLong(cursor.getColumnIndex(AnnotationsDatabaseHelper.COLUMN_CONTENT));
                Long update = cursor.getLong(cursor.getColumnIndex(AnnotationsDatabaseHelper.COLUMN_UPDATETIME));
                notes.put(verse, new Note(id, verse, verses, content, create, update));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    class Note {
        Long id = null;
        String verse;
        String verses;
        String content;
        long createtime;
        long updatetime;
        ContentValues values = null;

        public Note(Long id, String verse, String verses, String content, long create, long update) {
            this.id = id;
            this.verse = verse;
            this.verses = verses;
            this.content = content;
            this.createtime = create;
            this.updatetime = update;
        }

        public Note(String verse, String verses, String content) {
            long time = System.currentTimeMillis() / 1000;
            this.verse = verse;
            this.verses = verses;
            this.content = content;
            this.createtime = time;
            this.updatetime = time;
        }

        public boolean update(String verses, String content) {
            if (this.verses.equals(verses) && this.content.equals(content)) {
                return false;
            }
            long time = System.currentTimeMillis() / 1000;
            this.verses = verses;
            this.content = content;
            this.updatetime = time;
            if (values != null) {
                values.put(AnnotationsDatabaseHelper.COLUMN_VERSES, verses);
                values.put(AnnotationsDatabaseHelper.COLUMN_CONTENT, content);
                values.put(AnnotationsDatabaseHelper.COLUMN_UPDATETIME, time);
            }
            return true;
        }

        public ContentValues getContentValues() {
            if (values == null) {
                values = new ContentValues();
                values.put(AnnotationsDatabaseHelper.COLUMN_TYPE, "note");
                values.put(AnnotationsDatabaseHelper.COLUMN_VERSE, verse);
                values.put(AnnotationsDatabaseHelper.COLUMN_VERSES, verses);
                values.put(AnnotationsDatabaseHelper.COLUMN_CONTENT, content);
                values.put(AnnotationsDatabaseHelper.COLUMN_CREATETIME, createtime);
                values.put(AnnotationsDatabaseHelper.COLUMN_UPDATETIME, updatetime);
            }
            return values;
        }

        public Long getId() {
            return this.id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    /**
     * get a list of verse for osis, called when the reading page refresh.
     *
     * @param osis book.chapter
     * @return
     */
    public String[] getNoteVerses(String osis) {
        if (osis == null) {
            return null;
        }
        if (!osis.equals(this.noteOsis)) {
            loadNotes(osis);
        }
        return notes.keySet().toArray(new String[notes.size()]);
    }

    /**
     * get notes for book.cpater.verse
     *
     * @param osis  book.chapter
     * @param verse the verse is one of the result in {@link #getNoteVerses(String)}
     * @return note
     * @see {@link #getNoteVerses(String)}
     */
    public Note getNote(String osis, String verse) {
        if (osis == null) {
            return null;
        }
        if (!osis.equals(this.noteOsis)) {
            loadNotes(osis);
        }
        return notes.get(verse);
    }

    /**
     * save the note for book.chapter.verse
     *
     * @param osis    book.chapter
     * @param verse   verse, normally number
     * @param content note content
     * @return true if saved, false if not
     */
    public boolean saveNote(String osis, String verse, String verses, String content) {
        if (content == null || content.length() == 0) {
            return false;
        }
        Note note = notes.get(verse);
        if (note == null) {
            note = new Note(verse, verses, content);
        } else if (!note.update(verses, content)) {
            return false;
        }
        notes.put(verse, note);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            File path = mContext.getDatabasePath(AnnotationsDatabaseHelper.DATABASE_NAME);
            if (path != null && path.delete()) {
                return saveNote(osis, verse, verses, content);
            }
            return false;
        }
        ContentValues values = note.getContentValues();
        values.put(AnnotationsDatabaseHelper.COLUMN_OSIS, osis);
        Long id = note.getId();
        if (id == null) {
            id = db.insert(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, null, values);
            note.setId(id);
        } else {
            db.update(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, values,
                    AnnotationsDatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        }
        return true;
    }

    public boolean deleteNote(Note note) {
        if (note == null || note.getId() == null) {
            return false;
        }
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return true;
        }
        db.delete(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, AnnotationsDatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(note.getId())});
        notes.remove(note.verse);
        return true;
    }

    public String getHighlight(String osis) {
        String highlighted = "";
        if (mOpenHelper == null) {
            mOpenHelper = new AnnotationsDatabaseHelper(mContext);
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return highlighted;
        }
        Cursor cursor = null;
        try {
            cursor = db.query(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, new String[]{
                            AnnotationsDatabaseHelper.COLUMN_ID, AnnotationsDatabaseHelper.COLUMN_VERSES},
                    AnnotationsDatabaseHelper.COLUMN_OSIS + " = ? and " + AnnotationsDatabaseHelper.COLUMN_TYPE + " = ?", new String[]{osis, "highlight"}, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                highlighted = cursor.getString(1);
            }
        } catch (SQLiteException e) {
            LogUtils.d("sqlite", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return highlighted;
    }

    public Long getHighlightId(String osis) {
        Long highlightId = null;
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = db.query(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, new String[]{
                            AnnotationsDatabaseHelper.COLUMN_ID, AnnotationsDatabaseHelper.COLUMN_VERSES},
                    AnnotationsDatabaseHelper.COLUMN_OSIS + " = ? and " + AnnotationsDatabaseHelper.COLUMN_TYPE + " = ?", new String[]{osis, "highlight"}, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                highlightId = cursor.getLong(0);
            }
        } catch (SQLiteException e) {
            LogUtils.d("sqlite", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return highlightId;
    }

    public boolean saveHighlight(String osis, String verses) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            File path = mContext.getDatabasePath(AnnotationsDatabaseHelper.DATABASE_NAME);
            return path != null && path.delete() && saveHighlight(osis, verses);
        }
        ContentValues values = new ContentValues();
        values.put(AnnotationsDatabaseHelper.COLUMN_OSIS, osis);
        values.put(AnnotationsDatabaseHelper.COLUMN_TYPE, "highlight");
        values.put(AnnotationsDatabaseHelper.COLUMN_VERSES, verses);
        Long highlightId = getHighlightId(osis);
        if (highlightId == null) {
            db.insert(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, null, values);
        } else {
            db.update(AnnotationsDatabaseHelper.TABLE_ANNOTATIONS, values,
                    AnnotationsDatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(highlightId)});
        }
        return true;
    }

    private SQLiteOpenHelper mOpenHelper = null;

    private class AnnotationsDatabaseHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 2;
        private static final String DATABASE_NAME = "annotations.db";

        AnnotationsDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public static final String TABLE_ANNOTATIONS = "annotations";
        public static final String COLUMN_ID = BaseColumns._ID;
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_OSIS = "osis";
        public static final String COLUMN_VERSE = "verse";
        public static final String COLUMN_VERSES = "verses";
        public static final String COLUMN_CONTENT = "content";
        public static final String COLUMN_CREATETIME = "createtime";
        public static final String COLUMN_UPDATETIME = "updatetime";

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANNOTATIONS);
            db.execSQL("CREATE TABLE " + TABLE_ANNOTATIONS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    COLUMN_OSIS + " VARCHAR NOT NULL," +
                    COLUMN_TYPE + " VARCHAR NOT NULL," +
                    COLUMN_VERSE + " VARCHAR," +
                    COLUMN_VERSES + " TEXT," +
                    COLUMN_CONTENT + " TEXT," +
                    COLUMN_CREATETIME + " DATETIME," +
                    COLUMN_UPDATETIME + " DATETIME" +
                    ");");

            db.execSQL("CREATE INDEX osis_tye ON " + TABLE_ANNOTATIONS + " (" +
                    COLUMN_OSIS + ", " + COLUMN_TYPE +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                onCreate(db);
            }
        }

    }
}
