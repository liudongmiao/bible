package me.piebridge.bible.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

import me.piebridge.bible.BuildConfig;

/**
 * Created by thom on 16/3/2.
 */
public class ChooserUtils {

    private ChooserUtils() {

    }

    public static void startActivityExcludeSelf(Context context, Intent intent, String title) {
        List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(intent, 0);
        if (!activities.isEmpty()) {
            List<Intent> intents = new ArrayList<>();
            for (ResolveInfo activity : activities) {
                String packageName = activity.activityInfo.packageName;
                if (BuildConfig.APPLICATION_ID.equals(packageName) || activity.priority != 0) {
                    continue;
                }
                intents.add(new Intent(intent).setPackage(packageName));
            }
            Intent chooser = Intent.createChooser(intents.remove(0), title);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Parcelable[intents.size()]));
            context.startActivity(chooser);
        } else {
            context.startActivity(Intent.createChooser(intent, title));
        }
    }

}
