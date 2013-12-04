package me.piebridge.bible;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

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
            Log.d(TAG,  "action: " + action + ", packageName: " + context.getPackageName() + ", applicationName: " + applicationName);
            if (applicationName.startsWith(context.getPackageName())) {
                 bible.checkBibleData(false);
            }
        } else {
            bible.checkBibleData(false);
        }
    }

}