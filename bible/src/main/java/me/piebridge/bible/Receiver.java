package me.piebridge.bible;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.piebridge.bible.utils.LogUtils;

public class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            onCompleteDownloads(context, intent);
        } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
            onClickDownloads(context);
        }
    }

    private void onClickDownloads(Context context) {
        context.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void onCompleteDownloads(Context context, Intent intent) {
        BibleApplication application = (BibleApplication) context.getApplicationContext();
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
        LogUtils.d("intent: " + intent + ", extras: " + intent.getExtras());
        application.checkStatus(id);
    }

}
