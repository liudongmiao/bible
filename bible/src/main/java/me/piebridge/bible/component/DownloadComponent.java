package me.piebridge.bible.component;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.HttpUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/25.
 */
public class DownloadComponent extends Handler {

    private static final AtomicLong ID = new AtomicLong(0x640);

    private static final int CHECK = 0;

    private static final int ADDED = 1;

    private static final int STATUS_DOWNLOADING = DownloadManager.STATUS_PENDING
            | DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_PAUSED;

    private volatile SimpleArrayMap<String, DownloadInfo> downloads = null;

    private SimpleArrayMap<String, DownloadTask> downloadTasks = null;

    private final Object downloadsLock = new Object();

    private final Object downloadTasksLock = new Object();

    private static final char[] HTTPS = new char[] {
            'h', 't', 't', 'p', 's', ':', '/', '/',
    };

    private static final char[] URL_PREFIX = new char[] {
            'd', 'l', '.', 'j', 'i', 'a', 'n', 'y', 'v', '.', 'c', 'o', 'm',
            '/', 'b', 'd', '/'
    };

    private static final String TRANSLATIONS_JSON = String.valueOf(new char[] {
            't', 'r', 'a', 'n', 's', 'l', 'a', 't', 'i', 'o', 'n', 's', '.', 'j', 's', 'o', 'n'
    });

    private static final String X_SDK = String.valueOf(new char[] {
            'X', '-', 'S', 'D', 'K'
    });

    private static final String X_VERSION = String.valueOf(new char[] {
            'X', '-', 'V', 'E', 'R', 'S', 'I', 'O', 'N'
    });

    private Context mContext;

    public DownloadComponent(Context context) {
        super(newLooper());
        this.mContext = context;
    }

    private static Looper newLooper() {
        HandlerThread thread = new HandlerThread("Download");
        thread.start();
        return thread.getLooper();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case CHECK:
                checkStatus((Long) msg.obj);
                break;
            case ADDED:
                addBibleData((File) msg.obj);
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
            if (useDownloadTask()) {
                downloadTasks = new SimpleArrayMap<>();
            }
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
                    if (useDownloadTask()) {
                        downloadManager.remove(info.id);
                    } else {
                        synchronized (downloadsLock) {
                            downloads.put(info.title, info);
                        }
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
        DownloadInfo downloadInfo;
        synchronized (downloadsLock) {
            downloadInfo = downloads.get(filename);
        }
        if (downloadInfo != null && !force) {
            LogUtils.d(filename + " status: " + downloadInfo.getStatus());
            return downloadInfo.id;
        }
        File externalCacheDir = mContext.getExternalCacheDir();
        if (externalCacheDir == null) {
            return 0;
        }
        if (useDownloadTask()) {
            return downloadAsyncTask(filename);
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(buildUrl(filename)));
        request.setTitle(filename);
        File file = new File(externalCacheDir, filename);
        request.setDestinationUri(Uri.fromFile(file));
        request.addRequestHeader(X_SDK, Integer.toString(Build.VERSION.SDK_INT));
        request.addRequestHeader(X_VERSION, Integer.toString(BuildConfig.VERSION_CODE));
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

    public void check(String filename) {
        try {
            HttpUtils.retrieveContent(buildUrl(filename), null, null);
        } catch (IOException ignore) {
            // do nothing
        }
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
            sendTitle(id, checkStatus(downloadManager, id));
        }
    }

    private void sendTitle(long id, String title) {
        Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id);
        intent.putExtra(Intent.EXTRA_TEXT, title);
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        if (!localBroadcastManager.sendBroadcast(intent) && !TextUtils.isEmpty(title)) {
            File file = new File(mContext.getExternalCacheDir(), title);
            obtainMessage(ADDED, file).sendToTarget();
        }
    }

    public boolean addBibleData(File file) {
        if (file.isFile() && isBibleData(file.getName())) {
            if (mContext instanceof BibleApplication) {
                BibleApplication application = (BibleApplication) mContext;
                String version = getVersion(file);
                LogUtils.d("addBibleData, file: " + file + ", version: " + version);
                if (!TextUtils.isEmpty(version) && application.unpackZip(file, version)) {
                    Collection<String> versions = application.getVersions();
                    if (versions.contains(version) && version.equals(BibleUtils.removeDemo(application.getVersion()))) {
                        application.setVersion(version, true);
                        Intent intent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isBibleData(String name) {
        return name != null && name.startsWith("bibledata-") && name.endsWith(".zip") && !name.contains("/");
    }

    public static String getVersion(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        final String prefix = "bibledata-";
        final String suffix = ".zip";
        String name = file.getName();
        if (name.startsWith(prefix) && name.endsWith(suffix)) {
            int begin = name.lastIndexOf('-');
            int end = name.lastIndexOf(suffix);
            int index;
            index = name.indexOf(' ');
            if (index > 0 && index < end) {
                end = index;
            }
            index = name.indexOf('(');
            if (index > 0 && index < end) {
                end = index;
            }
            return name.substring(begin + 1, end).toLowerCase(Locale.US);
        }
        return null;
    }

    public void cancel(String filename) {
        if (downloads == null) {
            checkDownloading();
        }
        if (useDownloadTask()) {
            cancelAsyncTask(filename);
            return;
        }
        DownloadInfo downloadInfo;
        synchronized (downloadsLock) {
            downloadInfo = downloads.get(filename);
        }
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
        DownloadInfo downloadInfo;
        synchronized (downloadsLock) {
            downloadInfo = downloads.get(filename);
        }
        return downloadInfo != null && (downloadInfo.status & STATUS_DOWNLOADING) != 0;
    }

    private File getVersionsJson() {
        return new File(mContext.getFilesDir(), TRANSLATIONS_JSON);
    }

    public String getLocalVersions() throws IOException {
        File file = getVersionsJson();
        InputStream is;
        if (file.isFile()) {
            is = new FileInputStream(file);
        } else {
            is = mContext.getResources().openRawResource(R.raw.translations);
        }
        return FileUtils.readAsString(is);
    }

    static String buildUrl(String path) {
        StringBuilder url = new StringBuilder();
        url.append(HTTPS);
        url.append(URL_PREFIX);
        url.append(path);
        return url.toString();
    }

    public String getRemoteVersions() throws IOException {
        final String keyEtag = "versions_etag";

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String etag = sharedPreferences.getString(keyEtag, "");
        Map<String, String> headers = new LinkedHashMap<>();
        if (!TextUtils.isEmpty(etag)) {
            headers.put("If-None-Match", etag);
        }
        headers.put(X_SDK, Integer.toString(Build.VERSION.SDK_INT));
        headers.put(X_VERSION, Integer.toString(BuildConfig.VERSION_CODE));
        String json = HttpUtils.retrieveContent(buildUrl(TRANSLATIONS_JSON), headers);
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
            os.write(json.getBytes(FileUtils.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        fileTmp.renameTo(file);

        return json;
    }

    Context getContext() {
        return mContext;
    }

    private boolean useDownloadTask() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT; // no download manager for 4.X
    }

    void updateStatus(String title, int status) {
        DownloadInfo downloadInfo;
        synchronized (downloadsLock) {
            downloadInfo = downloads.get(title);
        }
        if (downloadInfo != null) {
            downloadInfo.status = status;
            switch (status) {
                case DownloadManager.STATUS_PENDING:
                case DownloadManager.STATUS_RUNNING:
                case DownloadManager.STATUS_PAUSED:
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    sendTitle(downloadInfo.id, title);
                    break;
                case DownloadManager.STATUS_FAILED:
                    sendTitle(downloadInfo.id, null);
                    break;
                default:
                    break;

            }
        }
    }

    private long downloadAsyncTask(String filename) {
        DownloadInfo info = new DownloadInfo();
        info.id = ID.getAndIncrement();
        info.title = filename;
        info.status = DownloadManager.STATUS_PENDING;
        LogUtils.d("info: " + info);
        synchronized (downloadsLock) {
            downloads.put(filename, info);
        }
        DownloadTask downloadTask = new DownloadTask(this, filename);
        synchronized (downloadTasksLock) {
            DownloadTask oldTask = downloadTasks.get(filename);
            if (oldTask != null) {
                oldTask.cancel(true);
            }
            downloadTasks.put(filename, downloadTask);
        }
        downloadTask.execute();
        return info.id;
    }

    void cancelAsyncTask(String filename) {
        DownloadInfo downloadInfo;
        synchronized (downloadsLock) {
            int index = downloads.indexOfKey(filename);
            downloadInfo = downloads.valueAt(index);
            downloads.removeAt(index);
        }
        DownloadTask downloadTask;
        synchronized (downloadTasksLock) {
            int index = downloadTasks.indexOfKey(filename);
            downloadTask = downloadTasks.valueAt(index);
            downloadTasks.removeAt(index);
        }
        if (downloadTask != null) {
            LogUtils.d("cancel " + filename + ", task: " + downloadTask);
            downloadTask.cancel(true);
            sendTitle(downloadInfo != null ? downloadInfo.id : -1, null);
        }
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

    private static class DownloadTask extends AsyncTask<Void, Integer, Boolean> {

        private final WeakReference<DownloadComponent> mReference;

        private final String mTitle;

        public DownloadTask(DownloadComponent downloadComponent, String filename) {
            this.mReference = new WeakReference<>(downloadComponent);
            this.mTitle = filename;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            if (isCancelled()) {
                return false;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(X_SDK, Integer.toString(Build.VERSION.SDK_INT));
            headers.put(X_VERSION, Integer.toString(BuildConfig.VERSION_CODE));

            DownloadComponent downloadComponent = mReference.get();
            if (downloadComponent == null) {
                return false;
            }
            Context context = downloadComponent.getContext();
            if (context == null) {
                return false;
            }
            File externalCacheDir = context.getExternalCacheDir();
            if (externalCacheDir == null) {
                return false;
            }
            File file = new File(externalCacheDir, mTitle);
            File fileTmp = new File(file.getParent(), file.getName() + ".tmp");
            boolean downloaded = false;
            try (
                    FileOutputStream os = new FileOutputStream(fileTmp)
            ) {
                publishProgress(0);
                downloaded = HttpUtils.retrieveContent(buildUrl(mTitle), headers, os);
            } catch (IOException e) {
                LogUtils.w("cannot download " + mTitle, e);
            }
            try {
                return downloaded && fileTmp.renameTo(file);
            } finally {
                if (fileTmp.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    fileTmp.delete();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            DownloadComponent downloadComponent = mReference.get();
            if (downloadComponent != null) {
                downloadComponent.updateStatus(mTitle, DownloadManager.STATUS_RUNNING);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            LogUtils.d("filename: " + mTitle + ", result: " + result);
            if (result == null) {
                return;
            }
            DownloadComponent downloadComponent = mReference.get();
            if (downloadComponent == null) {
                return;
            }
            downloadComponent.updateStatus(mTitle, result ? DownloadManager.STATUS_SUCCESSFUL : DownloadManager.STATUS_FAILED);
            Context context = downloadComponent.getContext();
            if (context == null) {
                return;
            }
        }

    }

}
