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

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

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

    private SQLiteDatabase mDatabase = null;
    private SQLiteDatabase oDatabase = null;
    private String databaseVersion = "";
    private final Object databaseLock = new Object();

    // FIXME
    private static Context mContext = null;

    private ArrayList<String> books = new ArrayList<>();
    private ArrayList<String> osiss = new ArrayList<>();
    private ArrayList<String> chapters = new ArrayList<>();
    private ArrayList<String> versions = new ArrayList<>();
    private final Object versionsLock = new Object();
    private final Object versionsCheckingLock = new Object();
    private final HashMap<String, String> versionpaths = new HashMap<>();
    private ArrayList<String> humans = new ArrayList<>();

    // FIXME
    private static Bible bible = null;

    private HashMap<String, String> versionNames = new HashMap<>();
    private HashMap<String, String> versionDates = new HashMap<>();
    private HashMap<String, String> versionFullnames = new HashMap<>();

    private LinkedHashMap<String, String> allhuman = new LinkedHashMap<>();
    private LinkedHashMap<String, String> allosis = new LinkedHashMap<>();
    private LinkedHashMap<String, String> searchfull = new LinkedHashMap<>();
    private LinkedHashMap<String, String> searchshort = new LinkedHashMap<>();

    private Collator collator;
    private Locale lastLocale;
    private boolean unpacked = false;
    private HashMap<String, Long> mtime = new HashMap<>();
    private String css;
    public String versionName;

    private static String HUMAN_PREFERENCE = "human";

    private Bible(Context context) {
        LogUtils.d("init bible");
        mContext = context;
        SharedPreferences preferences = mContext.getSharedPreferences(HUMAN_PREFERENCE, 0);
        for (Entry<String, ?> entry : preferences.getAll().entrySet()) {
            allhuman.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    0).versionName;
        } catch (NameNotFoundException e) {
        }
        updateLocale();
        checkVersionsSync(true);
        setDefaultVersion();
    }

    public void updateLocale() {
        Locale locale = Locale.getDefault();
        if (collator == null || !locale.equals(lastLocale)) {
            collator = Collator.getInstance(locale);
            lastLocale = locale;
            updateResources();
        }
    }

    public synchronized static Bible getInstance(Context context) {
        if (bible == null) {
            bible = new Bible(context);
        }
        bible.updateLocale();
        return bible;
    }

    public int[] getChapterVerse(String string) {
        int value = new BigDecimal(string).multiply(new BigDecimal(Provider.THOUSAND)).intValue();
        int chapter = value / Provider.THOUSAND;
        int verse = value % Provider.THOUSAND;
        return new int[] {chapter, verse};
    }

    public List<Integer> getVerses(String book, int chapter) {
        List<Integer> verses = new ArrayList<>();
        SQLiteDatabase database = acquireDatabase();
        try (
                Cursor cursor = database.query(Provider.TABLE_VERSE, Provider.COLUMNS_VERSE,
                        "book = ? and verse > ? and verse < ?",
                        new String[] {book, String.valueOf(chapter), String.valueOf(chapter + 1)},
                        null, null, "id")
        ) {
            BigDecimal thousand = new BigDecimal(Provider.THOUSAND);
            while (cursor != null && cursor.moveToNext()) {
                String verse = cursor.getString(0);
                verses.add(new BigDecimal(verse).multiply(thousand).intValue() % Provider.THOUSAND);
            }
        } finally {
            releaseDatabase(database);
        }
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

    public int checkVersionsSync(boolean all) {
        List<String> newVersions = new ArrayList<>();
        Map<String, String> newVersionpaths = new HashMap<>();
        File[] dirs = getExternalFilesDirs();
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
                &&
                (!oldpath.exists() || !oldpath.isDirectory() || oldpath.lastModified() <= oldmtime)) {
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

    private void checkInternalVersions(List<String> versions, Map<String, String> versionpaths,
                                       boolean all) {
        if (!unpacked) {
            setDemoVersions(false);
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
        return setVersion(version, false);
    }

    public boolean setVersion(String version, boolean refresh) {
        if (version == null) {
            return false;
        }
        File file = getFile(version);
        if (file == null || !file.isFile()) {
            return "".equals(databaseVersion) && setDefaultVersion();
        }
        if (mDatabase != null && databaseVersion.equals(version) && !refresh) {
            return true;
        }
        databaseVersion = version;
        synchronized (databaseLock) {
            oDatabase = mDatabase;
            mDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        }
        LogUtils.d("open database \"" + mDatabase.getPath() + "\"");
        int oldsize = allhuman.size();
        setMetadata(mDatabase, databaseVersion, true);
        if (allhuman.size() > oldsize) {
            SharedPreferences.Editor editor = mContext.getSharedPreferences(HUMAN_PREFERENCE, 0).edit();
            for (Entry<String, String> entry : allhuman.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            editor.apply();
        }
        return true;
    }

    private File getFile(File dir, String version) {
        LogUtils.d("directory: " + dir + ", version: " + version);
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
            for (File dir : getExternalFilesDirs()) {
                file = getFile(dir, version);
                LogUtils.d("version: " + version);
                if (file != null) {
                    versionpaths.put(version, file.getAbsolutePath());
                    return file;
                }
            }
            file = getFile(new File(Environment.getExternalStorageDirectory(), ".piebridge"),
                    version);
            if (file != null) {
                versionpaths.put(version, file.getAbsolutePath());
                return file;
            }
            return getFile(mContext.getFilesDir(), version);
        }
    }

    private void setMetadata(SQLiteDatabase metadata, String dataversion, boolean change) {
        Cursor cursor =
                metadata.query(Provider.TABLE_BOOKS, Provider.COLUMNS_BOOKS, null, null, null, null,
                        null);
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
                String chapter =
                        cursor.getString(cursor.getColumnIndexOrThrow(Provider.COLUMN_CHAPTERS));

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
                            new String[] {"group_concat(replace(reference_osis, \"" + osis +
                                    ".\", \"\")) as osis"},
                            "reference_osis like ?", new String[] {osis + ".%"}, null, null, null);
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
                LogUtils.w("cannot create directory: " + file);
                return null;
            }
            try {
                (new File(file, ".nomedia")).createNewFile();
            } catch (IOException ioe) {
            }
        }
        return file;
    }

    private File[] getExternalFilesDirs() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                mContext == null) {
            LogUtils.d("not mounted, mContext = " + mContext);
            return new File[0];
        } else {
            return ContextCompat.getExternalFilesDirs(mContext, null);
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
                return new ArrayList<>(versionpaths.keySet());
            default:
                return new ArrayList<>();
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

    public SQLiteDatabase acquireDatabase() {
        synchronized (databaseLock) {
            return mDatabase;
        }
    }

    public void releaseDatabase(SQLiteDatabase database) {
        if (oDatabase != null && oDatabase != database) {
            LogUtils.d("close database \"" + oDatabase.getPath() + "\"");
            oDatabase.close();
            oDatabase = null;
        }
    }

    public String getVersion() {
        if (databaseVersion.equals("")) {
            setDefaultVersion();
        }
        return databaseVersion;
    }

    private String getVersionMetadata(String name, SQLiteDatabase metadata, String defaultValue) {
        try (
                Cursor cursor = metadata.query("metadata", new String[] {"value"},
                        "name = ? or name = ?",
                        new String[] {name, localeName(name)},
                        null, null, "name desc", "1")
        ) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getString(0);
            } else {
                return defaultValue;
            }
        }
    }

    private String localeName(String name) {
        Locale locale = Locale.getDefault();
        return name + "_" + locale.getLanguage() + "_" + locale.getCountry();
    }

    public String getVersionFullname(String version) {
        version = version.toLowerCase(Locale.US);
        String fullname = getResourceValue(versionFullnames,
                version.replace("demo", "").replace("niv84", "niv1984"));
        if (version.endsWith("demo")) {
            fullname += "(" + mContext.getString(R.string.demo) + ")";
        }
        return fullname;
    }

    public String getVersionName(String version) {
        version = version.toLowerCase(Locale.US);
        return getResourceValue(versionNames,
                version.replace("demo", "").replace("niv84", "niv1984")).toUpperCase(Locale.US);
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
        LogUtils.d("updateResources");
        setResourceValues(versionNames, R.array.versionname);
        setResourceValues(versionFullnames, R.array.versionfullname);
        setResourceValuesReverse(allosis, R.array.osiszhcn);
        setResourceValuesReverse(allosis, R.array.osiszhtw);
        setResourceValuesReverse(searchfull, R.array.searchfullzhcn);
        setResourceValuesReverse(searchshort, R.array.searchshortzhcn);
    }

    private void setDemoVersions(boolean force) {
        final int demoVersion = PreferenceManager.getDefaultSharedPreferences(mContext).getInt("demoVersion", 0);
        final int versionCode = BuildConfig.VERSION_CODE;
        boolean newVersion = demoVersion != versionCode || force;
        boolean unpack = unpackRaw(newVersion, R.raw.niv84demo, new File(mContext.getFilesDir(), "niv84demo.sqlite3"));
        if (unpack) {
            unpack = unpackRaw(newVersion, R.raw.cunpssdemo, new File(mContext.getFilesDir(), "cunpssdemo.sqlite3"));
        }
        if (newVersion && unpack) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("demoVersion", versionCode).apply();
        }
    }

    public boolean setDefaultVersion() {
        if (versions.isEmpty()) {
            checkBibleData();
            if (versions.isEmpty()) {
                setDemoVersions(true);
                checkVersionsSync(true);
            }
        }
        String version = PreferenceManager.getDefaultSharedPreferences(mContext).getString("version", null);
        if (TextUtils.isEmpty(version) || !versions.contains(version)) {
            version = get(TYPE.VERSION, 0);
        }
        File file = getFile(version);
        if (file != null && file.isFile()) {
            return setVersion(version, true);
        } else {
            LogUtils.w("cannot find version " + version);
            return false;
        }
    }

    private boolean unpackRaw(boolean newVersion, int resId, File file) {
        if (file.exists()) {
            if (!newVersion) {
                return true;
            }
            file.delete();
        }

        LogUtils.d("unpacking " + file.getAbsolutePath());

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
        } catch (IOException e) {
            LogUtils.w("unpacked " + file.getAbsolutePath(), e);
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
        ArrayList<LinkedHashMap<String, String>> maps =
                new ArrayList<>();
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

        LogUtils.d("getOsis, book: " + book);

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

    private boolean checkStartSuggest(LinkedHashMap<String, String> osiss, String value, String key,
                                      String book, int limit) {
        if ("".equals(book) || value.replace(" ", "").toLowerCase(Locale.US).startsWith(book)) {
            if (addSuggest(osiss, value, key, limit)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkContainSuggest(LinkedHashMap<String, String> osiss, String value, String key,
                                        String book, int limit) {
        if (value.replace(" ", "").toLowerCase(Locale.US).contains(book)) {
            if (addSuggest(osiss, value, key, limit)) {
                return true;
            }
        }
        return false;
    }

    private boolean addSuggest(LinkedHashMap<String, String> osiss, String value, String osis,
                               int limit) {
        if (!osiss.values().contains(osis)) {
            String text = get(TYPE.HUMAN, bible.getPosition(TYPE.OSIS, osis));
            LogUtils.d("add suggest, text=" + text + ", data=" + osis);
            osiss.put(text, osis);
        }
        if (limit != -1 && osiss.size() >= limit) {
            LogUtils.d("arrive limit " + limit);
            return true;
        }
        return false;
    }

    /*
     * 根据book获取多个osiss，可能用于查询
     *
     */
    public LinkedHashMap<String, String> getOsiss(String book, int limit) {
        LinkedHashMap<String, String> osiss = new LinkedHashMap<>();

        if (book != null) {
            // fix for zhcn
            book = book.replace("约一", "约壹");
            book = book.replace("约二", "约贰");
            book = book.replace("约三", "约叁");
            book = book.replace(SearchManager.SUGGEST_URI_PATH_QUERY, "").replace(" ",
                    "").toLowerCase(Locale.US);
        }

        LogUtils.d("book: " + book);

        ArrayList<Entry<String, String>> maps = new ArrayList<>();

        maps.addAll(searchshort.entrySet());

        maps.addAll(searchfull.entrySet());

        for (LinkedHashMap<String, String> map : getMaps(TYPE.HUMAN)) {
            maps.addAll(map.entrySet());
        }

        for (LinkedHashMap<String, String> map : getMaps(TYPE.OSIS)) {
            maps.addAll(map.entrySet());
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

    public boolean deleteVersion(String version) {
        version = version.toLowerCase(Locale.US);
        File file = getFile(version);
        LogUtils.d("version: " + version + ", file: " + file);
        if (file != null && file.isFile() && file.delete()) {
            versions.remove(version);
            versionDates.remove(version);
            if (versions.isEmpty() || ObjectUtils.equals(version, databaseVersion)) {
                setDefaultVersion();
            }
            return true;
        } else {
            return false;
        }
    }

    public void checkBibleData() {
        synchronized (versionsCheckingLock) {
            if (versions.isEmpty() && checkVersionsSync(false) == 0) {
                checkZipData(Environment.getExternalStorageDirectory());
                checkZipData(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS));
                checkZipData(mContext.getExternalCacheDir());
                checkVersionsSync(true);
            }
        }
    }

    private boolean checkZipData(File path) {
        if (path == null || !path.isDirectory() || path.list() == null) {
            return false;
        }
        LogUtils.d("checking zipdata " + path.getAbsolutePath());
        for (String name : path.list()) {
            if (name.startsWith("bibledata-") && name.endsWith("zip")) {
                checkZipPath(new File(path, name));
            }
        }
        return true;
    }

    public boolean checkZipPath(File path) {
        try {
            return unpackZip(path);
        } catch (IOException e) {
            LogUtils.w("unpackZip", e);
            return false;
        }
    }

    private boolean unpackZip(File path) throws IOException {
        if (path == null || !path.isFile()) {
            return false;
        }

        long fileSize = path.length();
        try (
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(path)))
        ) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                long zeSize = ze.getCompressedSize();
                // zip is incomplete
                if (fileSize < zeSize) {
                    break;
                }
                String zeName = ze.getName();
                if (zeName == null || !zeName.endsWith((".sqlite3"))) {
                    continue;
                }
                int sep = zeName.lastIndexOf(File.separator);
                if (sep != -1) {
                    zeName = zeName.substring(sep + 1);
                }
                File file;
                String version = zeName.toLowerCase(Locale.US).replace(".sqlite3", "");
                String versionPath = versionpaths.get(version);
                if (versionPath != null) {
                    file = new File(versionPath);
                } else {
                    file = new File(getPath(getExternalFilesDirs()), zeName);
                }
                if (file.exists() && file.lastModified() > ze.getTime() && file.lastModified() > path.lastModified()) {
                    continue;
                }
                LogUtils.d("unpacking " + file.getAbsoluteFile());
                if (unpack(zis, file)) {
                    //noinspection ResultOfMethodCallIgnored
                    path.delete();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean unpack(InputStream is, File file) throws IOException {
        File tmpFile = new File(file.getParent(), file.getName() + ".tmp");
        try (
                OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpFile))
        ) {
            int length;
            byte[] buffer = new byte[0x2000];
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
            return tmpFile.renameTo(file);
        }
    }

    int checkVersion(File path, List<String> versions, Map<String, String> versionpaths,
                     boolean all) {
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
                LogUtils.d("add version " + name);
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

    private boolean checkVersionMeta(File file, String version) {
        try (
                SQLiteDatabase metadata = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        ) {
            String dataversion = version.replace("demo", "");
            if (isDatabaseSupported(metadata)) {
                if (!versionFullnames.containsKey(version)) {
                    versionFullnames.put(version, getVersionMetadata("fullname", metadata, dataversion));
                }
                if (!versionNames.containsKey(version)) {
                    versionNames.put(version, getVersionMetadata("name", metadata, dataversion));
                }
                versionDates.put(version, getVersionMetadata("date", metadata, "0"));
                return true;
            }
        } catch (SQLiteException e) {
            LogUtils.w("cannot open " + file, e);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return false;
    }

    private boolean isDatabaseSupported(SQLiteDatabase database) {
        return hasColumns(database, "verses", "id", "book", "verse", "unformatted")
                && hasColumns(database, "books", "number", "osis", "human", "chapters")
                && hasColumns(database, "chapters", "id", "reference_osis", "reference_human", "content",
                "previous_reference_osis", "next_reference_osis")
                && hasColumns(database, "metadata", "name", "value");
    }

    private boolean hasColumns(SQLiteDatabase database, String table, String... columns) {
        try (Cursor cursor = database.query(table, null, null, null, null, null, null, "1")) {
            if (cursor != null && cursor.moveToFirst()) {
                for (String column : columns) {
                    if (cursor.getColumnIndex(column) == -1) {
                        LogUtils.w("table " + table + " has no column " + column);
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean hasChapter(String version, String osis) {
        File file = getFile(version);
        if (file == null || !file.isFile()) {
            return false;
        }
        try (
                SQLiteDatabase metadata = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                Cursor cursor = metadata.query(Provider.TABLE_CHAPTERS, new String[] {"id"},
                        "reference_osis = ? or reference_osis = ?",
                        new String[] {osis, osis.replace(Bible.INTRO, "1")},
                        null, null, "id", "1")
        ) {
            return cursor != null && cursor.moveToFirst();
        } catch (SQLiteException e) {
            LogUtils.w("cannot open " + file, e);
        }
        return false;
    }

    public Context getContext() {
        return mContext;
    }

    public void email(Context context) {
        email(context, null);
    }

    public void email(Context context, String content) {
        StringBuilder subject = new StringBuilder();
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

    public String getAnnotation(String osis, String link) {
        if (TextUtils.isEmpty(osis) || TextUtils.isEmpty(link)) {
            return null;
        }
        SQLiteDatabase database = acquireDatabase();
        try (
                Cursor cursor = database.query("annotations", new String[] {"content"}, "osis = ? and link = ?",
                        new String[] {osis, link}, null, null, null, "1")
        ) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getString(0);
            }
        } finally {
            releaseDatabase(database);
        }
        return null;
    }

    public boolean checkItems(ArrayList<OsisItem> items) {
        boolean changed = false;
        Iterator<OsisItem> it = items.iterator();
        while (it.hasNext()) {
            OsisItem item = it.next();
            String osis = item.toOsis();
            String book = BibleUtils.getBook(osis);
            if (getPosition(Bible.TYPE.OSIS, book) < 0) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }


}
