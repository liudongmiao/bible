package me.piebridge.bible.utils;

import android.os.Build;
import android.view.View;
import android.webkit.WebView;
import android.widget.ZoomButtonsController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * Created by thom on 15/10/19.
 */
public class WebViewUtils {

    private WebViewUtils() {

    }

    public static void hideDisplayZoomControls(WebView webView) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            webView.getSettings().setDisplayZoomControls(false);
        } else {
            try {
                Method method = WebView.class.getMethod("getZoomButtonsController");
                ((ZoomButtonsController) method.invoke(webView)).getContainer().setVisibility(View.GONE);
            } catch (NoSuchMethodException e) {
                LogUtils.d("cannot find method", e);
            } catch (InvocationTargetException e) {
                LogUtils.d("cannot invoke", e);
            } catch (IllegalAccessException e) {
                LogUtils.d("cannot access", e);
            }
        }
    }

}
