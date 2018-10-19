package me.piebridge.bible.utils;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startActivityExcludeSelfN(context, intent, title);
        } else {
            startActivityExcludeSelfDeprecated(context, intent, title);
        }
    }

    private static void startActivityExcludeSelfDeprecated(Context context, Intent intent, String title) {
        List<ResolveInfo> intentActivities = context.getPackageManager().queryIntentActivities(intent, 0);
        if (!intentActivities.isEmpty()) {
            List<Intent> intents = new ArrayList<>();
            for (ResolveInfo resolveInfo : intentActivities) {
                if (accept(intent, resolveInfo)) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    intents.add(new Intent(intent).setPackage(packageName));
                }
            }
            Intent chooser = Intent.createChooser(intents.remove(0), title);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Parcelable[0]));
            context.startActivity(chooser);
        } else {
            context.startActivity(Intent.createChooser(intent, title));
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static void startActivityExcludeSelfN(Context context, Intent intent, String title) {
        Intent chooser = Intent.createChooser(intent, title);
        List<ResolveInfo> intentActivities = context.getPackageManager().queryIntentActivities(intent, 0);
        if (!intentActivities.isEmpty()) {
            List<ComponentName> componentNames = new ArrayList<>();
            for (ResolveInfo resolveInfo : intentActivities) {
                if (!accept(intent, resolveInfo)) {
                    ActivityInfo activityInfo = resolveInfo.activityInfo;
                    componentNames.add(new ComponentName(activityInfo.packageName, activityInfo.name));
                }
            }
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, componentNames.toArray(new Parcelable[0]));
        }
        context.startActivity(chooser);
    }

    private static boolean accept(Intent intent, ResolveInfo resolveInfo) {
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        String packageName = activityInfo.packageName;
        if (BuildConfig.APPLICATION_ID.equals(packageName)) {
            LogUtils.d("exclude " + packageName + "/" + activityInfo.name);
            return false;
        }
        if (intent.hasCategory(Intent.CATEGORY_BROWSABLE) && resolveInfo.priority != 0) {
            LogUtils.d("exclude " + packageName + "/" + activityInfo.name + ", priority: " + resolveInfo.priority);
            return false;
        }
        return true;
    }

}
