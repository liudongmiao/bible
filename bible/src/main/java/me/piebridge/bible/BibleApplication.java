package me.piebridge.bible;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.collection.ArraySet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.piebridge.bible.component.AnnotationComponent;
import me.piebridge.bible.component.DownloadComponent;
import me.piebridge.bible.component.VersionComponent;
import me.piebridge.bible.component.VersionsComponent;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LocaleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.payment.PaymentApplication;

/**
 * Created by thom on 2018/10/25.
 */
public class BibleApplication extends PaymentApplication {

    private boolean initialized;

    private static final String VERSION_NIV = "niv";
    private static final String VERSION_NIV1984 = "niv1984";
    private static final String VERSION_NIV2011 = "niv2011";

    private DownloadComponent mDownload;

    private AnnotationComponent mAnnotation;

    private VersionsComponent mVersions;

    private VersionComponent mVersion;

    private Collection<String> mSortedVersions = new LinkedHashSet<>();

    public void onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .permitDiskReads()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
        super.onCreate();
        mDownload = new DownloadComponent(this);
        mAnnotation = new AnnotationComponent(this);
        mVersions = new VersionsComponent(this);
        mVersion = new VersionComponent(this);
    }

    public String getDefaultVersion() {
        return mVersions.getDefaultVersion();
    }

    public long download(String filename, boolean force) {
        return mDownload.download(filename, force);
    }

    public void check(String filename) {
        mDownload.check(filename);
    }

    public void cancelDownload(String filename) {
        mDownload.cancel(filename);
    }

    public void onDownloadCompleted(long id) {
        mDownload.onCompleted(id);
    }

    public boolean isDownloading(String filename) {
        return mDownload.isDownloading(filename);
    }

    public boolean addBibleData(File file) {
        return mDownload.addBibleData(file);
    }

    public String getLocalVersions() throws IOException {
        return mDownload.getLocalVersions();
    }

    public String getRemoteVersions() throws IOException {
        return mDownload.getRemoteVersions();
    }

    public Bundle getNoteVerses(String osis) {
        return mAnnotation.getNoteVerses(osis);
    }

    public Bundle getNote(long id) {
        return mAnnotation.getNote(id);
    }

    public void saveNote(long id, String osis, String verse, String verses, String content) {
        mAnnotation.saveNote(id, osis, verse, verses, content);
    }

    public void deleteNote(long id) {
        mAnnotation.deleteNote(id);
    }

    public String getHighlight(String osis) {
        return mAnnotation.getHighlight(osis);
    }

    public void saveHighlight(String osis, String verses) {
        mAnnotation.saveHighlight(osis, verses);
    }

    public Cursor searchHighlight(String book, String sort) {
        return mAnnotation.searchHighlight(book, sort);
    }

    public Cursor searchNotes(String book, String query, String sort) {
        return mAnnotation.searchNotes(book, query, sort);
    }

    public boolean unpackZip(File file, String version) {
        try {
            return mVersions.unpackZip(file, version);
        } catch (IOException e) {
            LogUtils.d("cannot unpack " + file, e);
            return false;
        }
    }

    public Collection<String> getVersions() {
        return mVersions.getVersions();
    }

    public Collection<String> getSortedVersions() {
        Set<String> versions = new ArraySet<>(getVersions());
        if (versions.equals(mSortedVersions)) {
            return mSortedVersions;
        }
        List<String> sortedVersions = new ArrayList<>(versions);
        Collections.sort(sortedVersions, this::compareFullname);
        mSortedVersions.clear();
        mSortedVersions.addAll(sortedVersions);
        return mSortedVersions;
    }

    public String getOsis(String query) {
        return mVersions.getOsis(query.toLowerCase(Locale.US));
    }

    public String getVersion() {
        return mVersion.getVersion();
    }

    public boolean setVersion(String version) {
        Collection<String> versions = mVersions.getVersions();
        if (versions.contains(version)) {
            return setVersion(version, false);
        }
        if (VERSION_NIV.equals(version)) {
            if (versions.contains(VERSION_NIV1984)) {
                return setVersion(VERSION_NIV1984, false);
            } else if (versions.contains(VERSION_NIV2011)) {
                return setVersion(VERSION_NIV2011, false);
            }
        }
        return false;
    }

    public boolean setDefaultVersion() {
        if (!initialized) {
            initialized = mVersion.setVersion(mVersions.getDefaultVersion(), false);
            return false;
        } else {
            return true;
        }
    }

    public boolean setVersion(String version, boolean force) {
        boolean updated = mVersion.setVersion(version, force);
        if (updated) {
            mVersions.setDefaultVersion(version);
            mVersions.updateBooks(mVersion.getBooks());
        }
        return updated;
    }

    public void deleteVersion(String version) {
        mVersion.deleteVersion(version);
        mVersions.deleteVersion(version);
    }

    public SQLiteDatabase acquireDatabase() {
        return mVersion.acquireDatabase();
    }

    public void releaseDatabase(SQLiteDatabase database) {
        mVersion.releaseDatabase(database);
    }

    public String removeIntro(String osis) {
        return mVersion.removeIntro(osis);
    }

    public String getDate(String version) {
        return mVersions.getDate(version);
    }

    public String getName(String version) {
        return mVersions.getName(BibleUtils.removeDemo(version));
    }

    public String getFullname(String version) {
        return mVersions.getFullname(version);
    }

    public String getFullname() {
        return mVersions.getFullname(getVersion());
    }

    public int compareFullname(String version1, String version2) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // use android.icu.text.Collator if available
            return android.icu.text.Collator.getInstance().compare(getFullname(version1), getFullname(version2));
        } else {
            return java.text.Collator.getInstance().compare(getFullname(version1), getFullname(version2));
        }
    }

    public String getFirstBook() {
        return mVersion.getFirstBook();
    }

    public String getLastBook() {
        return mVersion.getLastBook();
    }

    public String getHuman(String book) {
        return mVersion.getHuman(book);
    }

    public Map<String, String> getBooks() {
        return mVersion.getBooks();
    }

    public List<String> getChapters(String book) {
        return mVersion.getChapters(book);
    }

    public List<Integer> getVerses(String book, int chapter) {
        return mVersion.getVerses(book, chapter);
    }

    public boolean checkItems(ArrayList<OsisItem> items) {
        boolean changed = false;
        Iterator<OsisItem> it = items.iterator();
        Map<String, String> books = getBooks();
        while (it.hasNext()) {
            OsisItem item = it.next();
            if (!books.containsKey(item.toBook())) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    public String getAnnotation(String osis, String link) {
        return mVersion.getAnnotation(osis, link);
    }

    public boolean hasChapter(String version, String osis) {
        return mVersion.hasChapter(version, osis);
    }

    public boolean hasVersion(String version) {
        Collection<String> versions = getVersions();
        if (version.equals(VERSION_NIV) && (versions.contains(VERSION_NIV1984) || versions.contains(VERSION_NIV2011))) {
            return true;
        }
        return versions.contains(version);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.updateResources(base));
    }

}
