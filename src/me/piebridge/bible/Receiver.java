package me.piebridge.bible;

import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

public class Receiver extends BroadcastReceiver {

    public static String TAG = "me.piebridge.bible$Receiver";

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
                bible.checkBibleData(false);
            }
        } else if ("android.provider.Telephony.SECRET_CODE".equals(action)) {
            context.startActivity(new Intent(context, Chapter.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            final long id = getDownloadId(intent);
            bible.checkBibleData(false, new Runnable() {
                @Override
                public void run() {
                    handler.sendMessage(handler.obtainMessage(0, id));
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    long getDownloadId(Intent intent) {
        return intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
    }

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Versions.refresh((Long) msg.obj);
            return false;
        }
    });

}
