package me.piebridge.bible;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.collection.SimpleArrayMap;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.HttpUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/25.
 */
public class DownloadComponent extends Handler {

    private static final int CHECK = 0;

    private static final int STATUS_DOWNLOADING = DownloadManager.STATUS_PENDING
            | DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_PAUSED;

    private volatile SimpleArrayMap<String, DownloadInfo> downloads = null;

    private final Object downloadsLock = new Object();

    private static final String VERSIONS_JSON = "versions.json";

    private static final String URL_VERSIONS_JSON = "https://dl.jianyv.com/bd/versions.json";

    private static final String URL_BIBLE_DATA_PREFIX = "https://github.com/liudongmiao/bibledata/raw/master/";

    private Context mContext;

    public DownloadComponent(Context context) {
        super(context.getMainLooper());
        this.mContext = context;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case CHECK:
                checkStatus((Long) msg.obj);
                break;
            default:
                break;
        }
    }

    private void checkDownloading() {
        if (downloads == null) {
            doCheckDownloading();
        }
    }

    private synchronized void doCheckDownloading() {
        if (downloads == null) {
            downloads = new SimpleArrayMap<>();
            DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
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
            LogUtils.d(filename + " status: " + downloadInfo.getStatus());
            return downloadInfo.id;
        }
        File externalCacheDir = mContext.getExternalCacheDir();
        if (externalCacheDir == null) {
            return 0;
        }
        String url = URL_BIBLE_DATA_PREFIX + filename;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(filename);
        File file = new File(externalCacheDir, filename);
        request.setDestinationUri(Uri.fromFile(file));
        DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            if (downloadInfo != null) {
                downloadManager.remove(downloadInfo.id);
            }
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
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

    public void onCompleted(long id) {
        LogUtils.d("will check status for " + id);
        obtainMessage(CHECK, id).sendToTarget();
    }

    private void checkStatus(long id) {
        LogUtils.d("check status for " + id);
        DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            if (downloads == null) {
                checkDownloading();
            }
            String title = checkStatus(downloadManager, id);
            Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id);
            intent.putExtra(Intent.EXTRA_TEXT, title);
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            if (!localBroadcastManager.sendBroadcast(intent) && !TextUtils.isEmpty(title)) {
                addBibleData(new File(mContext.getExternalCacheDir(), title));
            }
        }
    }

    public boolean addBibleData(File file) {
        if (file.exists()) {
            Bible bible = Bible.getInstance(mContext);
            if (bible.checkZipPath(file)) {
                bible.checkVersionsSync(true);
                String version = BibleUtils.removeDemo(bible.getVersion());
                if (file.getName().contains(version)) {
                    bible.setVersion(version, true);
                    Intent intent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                }
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
            DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
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

    private File getVersionsJson() {
        return new File(mContext.getFilesDir(), VERSIONS_JSON);
    }

    public String getLocalVersions() throws IOException {
        File file = getVersionsJson();
        InputStream is;
        if (file.isFile()) {
            is = new FileInputStream(file);
        } else {
            is = mContext.getResources().openRawResource(R.raw.versions);
        }
        return FileUtils.readAsString(is);
    }

    public String getRemoteVersions() throws IOException {
        final String keyEtag = "versions_etag";

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String etag = sharedPreferences.getString(keyEtag, "");
        Map<String, String> headers = new LinkedHashMap<>();
        if (!TextUtils.isEmpty(etag)) {
            headers.put("If-None-Match", etag);
        }
        String json = HttpUtils.retrieveContent(URL_VERSIONS_JSON, headers);
        if (json == null) {
            return null;
        }
        try {
            new JSONObject(json);
        } catch (JSONException e) {
            LogUtils.d("json: " + json);
            return null;
        }
        etag = headers.get(HttpUtils.ETAG);
        if (!TextUtils.isEmpty(etag)) {
            sharedPreferences.edit().putString(keyEtag, etag).apply();
        }

        File file = getVersionsJson();
        File fileTmp = new File(file.getParent(), file.getName() + ".tmp");
        try (
                OutputStream os = new BufferedOutputStream(new FileOutputStream(fileTmp))
        ) {
            os.write(json.getBytes("UTF-8"));
        }
        //noinspection ResultOfMethodCallIgnored
        fileTmp.renameTo(file);

        return json;
    }

    private static class DownloadInfo {
        public long id;
        public int status;
        public String title;

        @Override
        public String toString() {
            return "DownloadInfo{id=" + id + ", title=" + title + ", status=" + getStatus() + "}";
        }

        public String getStatus() {
            switch (status) {
                case DownloadManager.STATUS_PENDING:
                    return "pending";
                case DownloadManager.STATUS_RUNNING:
                    return "running";
                case DownloadManager.STATUS_PAUSED:
                    return "paused";
                case DownloadManager.STATUS_SUCCESSFUL:
                    return "successful";
                case DownloadManager.STATUS_FAILED:
                    return "failed";
                default:
                    return "unknown(" + status + ")";
            }
        }
    }

}
