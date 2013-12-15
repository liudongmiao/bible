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
            if (applicationName != null && applicationName.startsWith(context.getPackageName())) {
                Log.d(TAG,  "action: " + action + ", packageName: " + context.getPackageName() + ", applicationName: " + applicationName);
                bible.checkBibleData(false);
            }
        } else if ("android.provider.Telephony.SECRET_CODE".equals(action)) {
            context.startActivity(new Intent(context, Chapter.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            bible.checkBibleData(false);
        }
    }

}