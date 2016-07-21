package me.piebridge.bible.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by thom on 16/7/21.
 */
public class RecreateUtils {

    private RecreateUtils() {

    }

    private static Field scanField(Class<?> clazz, String... names) {
        for (String name : names) {
            Field field;
            try {
                field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                LogUtils.d("no such field", e);
            }
            try {
                return clazz.getField(name);
            } catch (NoSuchFieldException e) {
                LogUtils.d("no such field", e);
            }
        }
        return null;
    }

    public static void recreate(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            recreateHC(activity);
        } else {
            try {
                recreateGB(activity);
            } catch (InvocationTargetException e) {
                LogUtils.d("invocation target", e);
            } catch (NoSuchMethodException e) {
                LogUtils.d("no such method", e);
            } catch (IllegalAccessException e) {
                LogUtils.d("illegal access", e);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void recreateHC(Activity activity) {
        activity.recreate();
    }

    private static boolean recreateGB(Activity activity) throws IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field mTokenField = scanField(Activity.class, "mToken");
        Field mMainThreadField = scanField(Activity.class, "mMainThread");
        if (mTokenField == null || mMainThreadField == null) {
            return false;
        }
        IBinder mToken = (IBinder) mTokenField.get(activity);
        Object mMainThread = mMainThreadField.get(activity);
        Field mAppThreadField = scanField(mMainThread.getClass(), "mAppThread");
        if (mAppThreadField == null) {
            return false;
        }
        Object mAppThread = mAppThreadField.get(mMainThread);
        Method method = mAppThread.getClass().getMethod("scheduleRelaunchActivity",
                IBinder.class, List.class, List.class, int.class, boolean.class, Configuration.class);
        method.invoke(mAppThread, mToken, null, null, 0, false, null);
        return true;
    }

}
