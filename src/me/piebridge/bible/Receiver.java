package me.piebridge.bible;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class Receiver extends BroadcastReceiver {

    public static String TAG = "me.piebridge.bible$Receiver";

    @SuppressLint("InlinedApi")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, action);
        Bible bible = Bible.getBible(context);
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            Uri uri = intent.getData();
            String applicationName = (uri != null) ? uri.getSchemeSpecificPart() : null;
            if (applicationName != null && applicationName.startsWith(context.getPackageName())) {
                Log.d(TAG,  "action: " + action + ", packageName: " + context.getPackageName() + ", applicationName: " + applicationName);
                bible.checkBibleData(false, null);
            }
        } else if ("android.provider.Telephony.SECRET_CODE".equals(action)) {
            context.startActivity(new Intent(context, Chapter.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            final DownloadInfo info = DownloadInfo.getDownloadInfo(context, id);
            if (info == null) {
            } else if (info.status != DownloadManager.STATUS_SUCCESSFUL) {
                Versions.onDownloadComplete(info);
            } else {
                bible.checkBibleData(false, new Runnable() {
                    @Override
                    public void run() {
                        Versions.onDownloadComplete(info);
                    }
                });
            }
        } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
            viewDownloads(context);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void viewDownloads(Context context) {
        context.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

}
