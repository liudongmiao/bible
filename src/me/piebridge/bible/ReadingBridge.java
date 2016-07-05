package me.piebridge.bible;

import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 15/10/19.
 */
public class ReadingBridge {

    private final WeakReference<Bridge> wr;

    private int verse;

    private final Object verseLock = new Object();

    public ReadingBridge(Bridge bridge) {
        wr = new WeakReference<Bridge>(bridge);
    }

    @JavascriptInterface
    public void setVerse(String verse) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            synchronized (verseLock) {
                if (!TextUtils.isEmpty(verse) && TextUtils.isDigitsOnly(verse)) {
                    this.verse = Integer.parseInt(verse);
                }
                verseLock.notifyAll();
            }
        }
    }

    @JavascriptInterface
    public void setCopyText(String text) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.setCopyText(text);
        }
    }

    @JavascriptInterface
    public void setHighlighted(String highlighted) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.setHighlighted(highlighted);
        }
    }

    @JavascriptInterface
    public void showAnnotation(String link, String annotation) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.showAnnotation(link, annotation);
        }
    }

    @JavascriptInterface
    public void showNote(String verseNum) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.showNote(verseNum);
        }
    }

    public int getVerse(WebView webview) {
        if (webview != null && webview.getScrollY() != 0) {
            synchronized (verseLock) {
                webview.loadUrl("javascript:getFirstVisibleVerse();");
                try {
                    verseLock.wait(3000);
                } catch (InterruptedException e) {
                    LogUtils.d("interrupted", e);
                }
            }
        }
        return verse;
    }

    interface Bridge {

        void setHighlighted(String highlighted);

        void showAnnotation(String link, String annotation);

        void showNote(String verseNum);

        void setCopyText(String text);

    }

}
