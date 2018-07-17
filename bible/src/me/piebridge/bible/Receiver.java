package me.piebridge.bible;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.piebridge.bible.activity.VersionsActivity;

public class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            onCompleteDownloads(context, intent);
        } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
            onClickDownloads(context);
        } else if ("android.provider.Telephony.SECRET_CODE".equals(action)) {
            launchApplication(context);
        }
    }


    private void onClickDownloads(Context context) {
        context.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void onCompleteDownloads(Context context, Intent intent) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
        final DownloadInfo info = DownloadInfo.getDownloadInfo(context, id);
        if (info == null) {
            return;
        }
        if (info.status != DownloadManager.STATUS_SUCCESSFUL) {
            VersionsActivity.onDownloadComplete(info);
        } else {
            Bible.getInstance(context).checkBibleData(false, new Runnable() {
                @Override
                public void run() {
                    VersionsActivity.onDownloadComplete(info);
                }
            });
        }
    }

    private void launchApplication(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        context.startActivity(intent);
    }

}
