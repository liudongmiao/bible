package me.piebridge.bible;

import android.app.Application;

import java.io.File;
import java.io.IOException;

/**
 * Created by thom on 2018/10/25.
 */
public class BibleApplication extends Application {

    private DownloadComponent mDownload;

    public void onCreate() {
        super.onCreate();
        mDownload = new DownloadComponent(this);
    }

    public long download(String filename, boolean force) {
        return mDownload.download(filename, force);
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

}
