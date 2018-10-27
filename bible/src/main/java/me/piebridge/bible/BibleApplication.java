package me.piebridge.bible;

import android.app.Application;
import android.os.Bundle;

import java.io.File;
import java.io.IOException;

/**
 * Created by thom on 2018/10/25.
 */
public class BibleApplication extends Application {

    private DownloadComponent mDownload;

    private AnnotationComponent mAnnotation;

    public void onCreate() {
        super.onCreate();
        mDownload = new DownloadComponent(this);
        mAnnotation = new AnnotationComponent(this);
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

}
