package me.piebridge.bible.component;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.text.TextUtils;

import androidx.collection.ArraySet;
import androidx.collection.SimpleArrayMap;
import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/10/28.
 */
public class VersionsComponent {

    private static final int DATABASE_FLAGS = SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS;

    private static final String PREFERENCE_VERSIONS = "versions";

    private static final String PREFERENCE_BOOKS = "books";

    private static final String KEY_VERSIONS = "versions";

    private static final String KEY_VERSION = "version";

    private static final String DATABASE_SUFFIX = ".sqlite3";

    private Context mContext;

    private SharedPreferences mPreferenceVersions;
    private SharedPreferences mPreferenceBooks;

    private final SimpleArrayMap<String, String> mHuman;

    private final Set<String> mBooks;

    private boolean versionChecked;

    private Locale mLocale;

    private final SimpleArrayMap<String, String> mOverrideNames;
    private final SimpleArrayMap<String, String> mOverrideFullnames;

    public VersionsComponent(Context context) {
        mContext = context;
        mPreferenceVersions = context.getSharedPreferences(PREFERENCE_VERSIONS, Context.MODE_PRIVATE);
        mPreferenceBooks = context.getSharedPreferences(PREFERENCE_BOOKS, Context.MODE_PRIVATE);
        mHuman = new SimpleArrayMap<>();
        mBooks = new ArraySet<>();

        Map<String, ?> books = mPreferenceBooks.getAll();
        for (Map.Entry<String, ?> entry : books.entrySet()) {
            String human = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                String book = (String) value;
                mHuman.put(human, book);
                mBooks.add(book);
            }
        }
        versionChecked = !mBooks.isEmpty();

        // load overrided names, fullnames
        mOverrideNames = new SimpleArrayMap<>();
        mOverrideFullnames = new SimpleArrayMap<>();

        loadOverride();

        setResourceValuesReverse(mHuman, R.array.osiszhcn);
        setResourceValuesReverse(mHuman, R.array.osiszhtw);
        setResourceValuesReverse(mHuman, R.array.searchfullzhcn);
        setResourceValuesReverse(mHuman, R.array.searchshortzhcn);
    }

    private void loadOverride() {
        Locale locale = Locale.getDefault();
        if (!ObjectUtils.equals(mLocale, locale)) {
            mLocale = locale;
            setResourceValues(mOverrideNames, R.array.versionname);
            setResourceValues(mOverrideFullnames, R.array.versionfullname);
        }
    }

    private void setResourceValues(SimpleArrayMap<String, String> map, int resId) {
        for (String entry : mContext.getResources().getStringArray(resId)) {
            int index = entry.indexOf('|');
            String key = entry.substring(0, index);
            String value = entry.substring(index + 1);
            map.put(key, value);
        }
    }

    private void setResourceValuesReverse(SimpleArrayMap<String, String> map, int resId) {
        for (String entry : mContext.getResources().getStringArray(resId)) {
            int index = entry.indexOf('|');
            String key = entry.substring(0, index);
            String value = entry.substring(index + 1);
            map.put(value, key);
        }
    }

    public String getDefaultVersion() {
        String version = mPreferenceVersions.getString(KEY_VERSION, null);
        Collection<String> versions = getVersions();
        if (!TextUtils.isEmpty(version) && versions.contains(version)) {
            return version;
        } else {
            return versions.iterator().next();
        }
    }

    public void setDefaultVersion(String version) {
        mPreferenceVersions.edit().putString(KEY_VERSION, version).apply();
    }

    public Collection<String> getVersions() {
        List<File> dirs = getDirs(mContext);
        if (dirs.isEmpty()) {
            LogUtils.w("no dirs, use demo versions");
            return checkDemoVersions();
        }
        final String key = KEY_VERSIONS + "_mtime";
        long mtime = mPreferenceVersions.getLong(key, 0);
        long lastModified = dirs.get(0).lastModified();
        if (mtime == lastModified) {
            Set<String> versions = mPreferenceVersions.getStringSet(KEY_VERSIONS, null);
            if (versions != null && !versions.isEmpty()) {
                LogUtils.d("[cache] versions: " + versions.size());
                return new ArraySet<>(versions);
            }
        }
        try {
            return checkVersions();
        } finally {
            mPreferenceVersions.edit().putLong(key, lastModified).apply();
        }
    }

    public static String getVersion(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String name = file.getName();
        if (!name.endsWith(DATABASE_SUFFIX)) {
            return null;
        }
        return name.substring(0, name.lastIndexOf(DATABASE_SUFFIX)).toLowerCase(Locale.US);
    }

    public static List<File> getDirs(Context context) {
        List<File> dirs = new ArrayList<>();
        for (File dir : ContextCompat.getExternalFilesDirs(context, null)) {
            if (dir != null && dir.isDirectory() && dir.canRead()) {
                dirs.add(dir);
            }
        }
        File file = new File(Environment.getExternalStorageDirectory(), ".piebridge");
        if (file.isDirectory()) {
            if (file.canRead()) {
                dirs.add(file);
            } else {
                LogUtils.d("ignore directory: " + file);
            }
        }
        Collections.sort(dirs, VersionsComponent::compare);
        return dirs;
    }

    static int compare(File o1, File o2) {
        return Long.compare(o2.lastModified(), o1.lastModified());
    }

    public static File getFile(Context context, String version) {
        if (BibleUtils.isDemoVersion(version)) {
            return new File(context.getFilesDir(), version + DATABASE_SUFFIX);
        } else {
            return getFile(getDirs(context), version);
        }
    }

    public static File getFile(List<File> dirs, String version) {
        List<File> files = new ArrayList<>();
        String name = version + DATABASE_SUFFIX;
        for (File dir : dirs) {
            File file = new File(dir, name);
            if (file.isFile() && file.canRead()) {
                files.add(file);
            }
        }
        if (files.isEmpty()) {
            return null;
        }
        if (files.size() == 1) {
            return files.get(0);
        }
        Collections.sort(files, VersionsComponent::compare);
        for (File file : files) {
            LogUtils.d("version: " + version + ", multi file: " + file);
        }
        return files.get(0);
    }

    private Collection<String> checkVersionFiles(List<File> dirs) {
        Set<String> versions = new ArraySet<>();
        for (File dir : dirs) {
            String[] names = dir.list();
            if (names != null) {
                for (String name : names) {
                    String version = getVersion(new File(dir, name));
                    if (!TextUtils.isEmpty(version)) {
                        versions.add(version);
                    }
                }
            }
        }
        return versions;
    }

    public Collection<String> checkVersions() {
        List<File> dirs = getDirs(mContext);
        LogUtils.d("dirs: " + dirs);
        Collection<String> candidate = checkVersionFiles(dirs);
        LogUtils.d("candidate: " + candidate);

        Set<String> versions = new ArraySet<>();
        for (String version : candidate) {
            File file = getFile(dirs, version);
            if (file != null) {
                String key = version + "_mtime";
                if (versionChecked && mPreferenceVersions.getLong(key, 0) == file.lastModified()) {
                    versions.add(version);
                    LogUtils.d("[cache] " + file + ": " + getFullname(version));
                } else {
                    synchronized (version.intern()) {
                        if (checkVersion(file, version)) {
                            mPreferenceVersions.edit().putLong(key, file.lastModified()).apply();
                            versions.add(version);
                            LogUtils.d("[check] " + file + ": " + getFullname(version));
                        } else {
                            LogUtils.d("[check] " + file + " is not bible data");
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
                        }
                    }
                }
            }
        }
        if (!versionChecked) {
            versionChecked = true;
        }
        mPreferenceVersions.edit().putStringSet(KEY_VERSIONS, versions).apply();
        Set<String> oldVersions = mPreferenceVersions.getStringSet(KEY_VERSIONS, null);
        if (oldVersions != null) {
            Set<String> removedVersions = new ArraySet<>(oldVersions);
            removedVersions.removeAll(versions);
            for (String version : removedVersions) {
                synchronized (version.intern()) {
                    removeMetadata(version);
                }
            }
        }
        LogUtils.d("[check] versions: " + versions.size());
        if (versions.isEmpty()) {
            return checkDemoVersions();
        } else {
            return versions;
        }
    }

    private void removeMetadata(String version) {
        Set<String> keys = new ArraySet<>();
        String prefix = version + "_";
        for (String key : mPreferenceVersions.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        SharedPreferences.Editor editor = mPreferenceVersions.edit();
        for (String key : keys) {
            editor.remove(key);
        }
        editor.apply();
    }

    private Collection<String> checkDemoVersions() {
        final String niv1984demo = "niv1984demo";
        final String cunpssdemo = "cunpssdemo";
        checkDemoVersion(R.raw.niv1984demo, niv1984demo);
        checkDemoVersion(R.raw.cunpssdemo, cunpssdemo);
        switch (Locale.getDefault().getLanguage()) {
            case "zh":
                return Arrays.asList(cunpssdemo, niv1984demo);
            case "en":
            default:
                return Arrays.asList(niv1984demo, cunpssdemo);
        }
    }

    public String getFullname(String version) {
        loadOverride();
        String fullname = mOverrideFullnames.get(version);
        if (!TextUtils.isEmpty(fullname)) {
            return fullname;
        } else {
            return getMetadata(version, "fullname", version);
        }
    }

    public String getName(String version) {
        loadOverride();
        String name = mOverrideNames.get(version);
        if (!TextUtils.isEmpty(name)) {
            return name;
        } else {
            return getMetadata(version, "name", version);
        }
    }

    public String getDate(String version) {
        return mPreferenceVersions.getString(version + "_date", null);
    }

    private String getMetadata(String version, String name, String defaultValue) {
        Locale locale = Locale.getDefault();
        String key = version + "_" + name;
        String keyLanguage = key + "_" + locale.getLanguage();
        String keyCountry = keyLanguage + "_" + locale.getCountry();

        String value;

        value = mPreferenceVersions.getString(keyCountry, null);
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        value = mPreferenceVersions.getString(keyLanguage, null);
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        value = mPreferenceVersions.getString(key, null);
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        return defaultValue;
    }

    private boolean checkVersion(File file, String version) {
        try (
                SQLiteDatabase database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, DATABASE_FLAGS)
        ) {
            if (isDatabaseSupported(database)) {
                checkMetadata(version, database);
                updateBooks(VersionComponent.loadBooks(database, null));
                return true;
            }
        } catch (SQLiteException | IllegalStateException e) {
            LogUtils.w("cannot open " + file, e);
        }
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
        try (
                Cursor cursor = database.query(table, null, null, null, null, null, null, "1")
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                for (String column : columns) {
                    if (cursor.getColumnIndex(column) == -1) {
                        LogUtils.w("table " + table + " has no column " + column);
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    private void checkMetadata(String version, SQLiteDatabase metadata) {
        try (
                Cursor cursor = metadata.query("metadata", null, null, null, null, null, null)
        ) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                int valueIndex = cursor.getColumnIndex("value");
                SharedPreferences.Editor editor = mPreferenceVersions.edit();
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String value = cursor.getString(valueIndex);
                    String key = version + "_" + name;
                    editor.putString(key, value);
                }
                editor.apply();
            }
        }
    }

    private boolean checkDemoVersion(int resId, String version) {
        File file = new File(mContext.getFilesDir(), version + DATABASE_SUFFIX);

        if (file.exists()) {
            if (checkVersion(file, version)) {
                LogUtils.d(file + ": " + getFullname(version));
                return true;
            }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }

        LogUtils.d("unpacking " + file.getAbsolutePath());

        try (
                OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
                InputStream is = new BufferedInputStream(mContext.getResources().openRawResource(resId))
        ) {
            FileUtils.copy(is, os);
            LogUtils.w("unpacked " + file.getAbsolutePath());
        } catch (IOException e) {
            LogUtils.w("cannot unpack " + version, e);
            return false;
        }

        if (checkVersion(file, version)) {
            LogUtils.d(file + ": " + getFullname(version));
            return true;
        }

        return false;
    }

    public boolean unpackZip(File path, String version) throws IOException {
        long fileSize = path.length();
        boolean unpacked = false;
        try (
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(path)))
        ) {
            ZipEntry ze;
            String name = version + DATABASE_SUFFIX;
            while ((ze = zis.getNextEntry()) != null) {
                long zeSize = ze.getCompressedSize();
                if (fileSize >= zeSize && name.equalsIgnoreCase(ze.getName())) {
                    File file = new File(mContext.getExternalFilesDir(null), name);
                    if (!file.exists() || file.lastModified() <= path.lastModified()) {
                        LogUtils.d("unpacking " + file.getAbsoluteFile());
                        unpacked = unpack(zis, file);
                    }
                }
                zis.closeEntry();
            }
        }
        if (unpacked) {
            //noinspection ResultOfMethodCallIgnored
            path.delete();
        }
        return unpacked;
    }

    private boolean unpack(InputStream is, File file) throws IOException {
        File tempFile = File.createTempFile(file.getName(), null, file.getParentFile());
        try (
                OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile))
        ) {
            FileUtils.copy(is, os);
        }
        return tempFile.renameTo(file);
    }


    public void deleteVersion(String version) {
        Set<String> versions = mPreferenceVersions.getStringSet(KEY_VERSIONS, null);
        if (versions != null) {
            Set<String> oldVersions = new ArraySet<>(versions);
            oldVersions.remove(version);
            mPreferenceVersions.edit().putStringSet(KEY_VERSIONS, oldVersions).apply();
        }
        removeMetadata(version);
    }

    public void updateBooks(Map<String, String> books) {
        SharedPreferences.Editor editor = mPreferenceBooks.edit();
        for (Map.Entry<String, String> entry : books.entrySet()) {
            String book = entry.getKey();
            String human = entry.getValue();
            mBooks.add(book);
            mHuman.put(human, book);
            editor.putString(human, book);
        }
        editor.apply();
    }

    public String getOsis(String bookOrHuman) {
        if (mBooks.contains(bookOrHuman)) {
            return bookOrHuman;
        }
        String book = mHuman.get(bookOrHuman);
        if (TextUtils.isEmpty(book)) {
            return null;
        } else {
            return book;
        }
    }

}
