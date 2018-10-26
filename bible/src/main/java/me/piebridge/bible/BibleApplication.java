package me.piebridge.bible;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import androidx.collection.SimpleArrayMap;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;

import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/25.
 */
public class BibleApplication extends Application {

    private static final int STATUS_DOWNLOADING = DownloadManager.STATUS_PENDING
            | DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_PAUSED;

    private volatile SimpleArrayMap<String, DownloadInfo> downloads = null;

    private final Object downloadsLock = new Object();

    public static final String BIBLEDATA_PREFIX = "https://github.com/liudongmiao/bibledata/raw/master/";

    public void checkDownloading() {
        if (downloads == null) {
            doCheckDownloading();
        }
    }

    private synchronized void doCheckDownloading() {
        if (downloads == null) {
            downloads = new SimpleArrayMap<>();
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                DownloadManager.Query query = new DownloadManager.Query().setFilterByStatus(STATUS_DOWNLOADING);
                fetchDownloads(downloadManager, query);
            }
        }
    }

    private String fetchDownloads(DownloadManager downloadManager, DownloadManager.Query query) {
        String title = null;
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                do {
                    DownloadInfo info = new DownloadInfo();
                    info.id = cursor.getLong(idIndex);
                    info.title = cursor.getString(titleIndex);
                    info.status = cursor.getInt(statusIndex);
                    LogUtils.d("info: " + info);
                    synchronized (downloadsLock) {
                        downloads.put(info.title, info);
                    }
                    title = info.title;
                } while (cursor.moveToNext());
            }
        }
        return title;
    }

    public long download(String filename, boolean force) {
        if (downloads == null) {
            checkDownloading();
        }
        DownloadInfo downloadInfo = downloads.get(filename);
        if (downloadInfo != null && !force) {
            return downloadInfo.id;
        }
        File externalCacheDir = getExternalCacheDir();
        if (externalCacheDir == null) {
            return 0;
        }
        String url = BIBLEDATA_PREFIX + filename;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(filename);
        File file = new File(externalCacheDir, filename);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        request.setDestinationUri(Uri.fromFile(file));
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            if (downloadInfo != null) {
                downloadManager.remove(downloadInfo.id);
            }
            long id = downloadManager.enqueue(request);
            if (checkStatus(downloadManager, id) != null) {
                return id;
            }
        }
        return 0;
    }

    private String checkStatus(DownloadManager downloadManager, long id) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        String title = fetchDownloads(downloadManager, query);
        if (title == null && !downloads.isEmpty()) {
            LogUtils.w("cannot find " + id);
            synchronized (downloadsLock) {
                int size = downloads.size();
                for (int i = 0; i < size; ++i) {
                    DownloadInfo downloadInfo = downloads.valueAt(i);
                    if (downloadInfo.id == id) {
                        downloads.removeAt(i);
                        break;
                    }
                }
            }
        }
        return title;
    }

    public void checkStatus(final long id) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                checkStatusAsync(id);
            }
        }).start();
    }

    void checkStatusAsync(long id) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        String title = checkStatus(downloadManager, id);
        Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id);
        intent.putExtra(Intent.EXTRA_TEXT, title);
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        if (!localBroadcastManager.sendBroadcast(intent)) {
            addBibleData(new File(getExternalCacheDir(), title));
        }
    }

    public boolean addBibleData(File file) {
        if (file.exists()) {
            Bible bible = Bible.getInstance(this);
            if (bible.checkZipPath(file)) {
                bible.checkVersionsSync(true);
                return true;
            }
        }
        return false;
    }

    public void cancel(String filename) {
        if (downloads == null) {
            checkDownloading();
        }
        DownloadInfo downloadInfo = downloads.get(filename);
        if (downloadInfo != null) {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.remove(downloadInfo.id);
                synchronized (downloadsLock) {
                    downloads.remove(filename);
                }
            }
        }
    }

    public boolean isDownloading(String filename) {
        if (downloads == null) {
            checkDownloading();
        }
        DownloadInfo downloadInfo = downloads.get(filename);
        return downloadInfo != null && (downloadInfo.status & STATUS_DOWNLOADING) != 0;
    }

    static class DownloadInfo {
        public long id;
        public int status;
        public String title;

        @Override
        public String toString() {
            return "DownloadInfo{id=" + id + ", title=" + title + ", status=" + status + "}";
        }
    }

}
