package me.piebridge.bible;

import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;

/**
 * Created by thom on 15/10/19.
 */
public class ReadingBridge {

    private final WeakReference<Bridge> wr;

    private final Object verseLock = new Object();

    private int verse;
    private Boolean highlightSelected;
    private String selectedVerses = null;
    private String selectedContent = null;

    public ReadingBridge(Bridge bridge) {
        wr = new WeakReference<Bridge>(bridge);
    }

    @JavascriptInterface
    public void setVerse(String verse) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            synchronized (verseLock) {
                this.verse = NumberUtils.parseInt(verse);
                verseLock.notifyAll();
            }
        }
    }

    @JavascriptInterface
    public void setCopyText(String text) {
        String[] fields = text.split("\n", 0x3);
        // highlight selected's length
        highlightSelected = NumberUtils.parseInt(fields[0]) > 0;
        selectedVerses = fields[0x1];
        selectedContent = fields[0x2];
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

    public boolean isHighlightSelected(boolean defaultHighlightSelected) {
        if (highlightSelected == null) {
            return defaultHighlightSelected;
        } else {
            return highlightSelected;
        }
    }

    public String getSelectedVerses(String defaultSelectedVerses) {
        if (selectedVerses == null) {
            return defaultSelectedVerses;
        } else {
            return selectedVerses;
        }
    }

    public String getSelectedContent(String defaultSelectedContent) {
        if (selectedContent == null) {
            return defaultSelectedContent;
        } else {
            return selectedContent;
        }
    }

    interface Bridge {

        void showAnnotation(String link, String annotation);

        void showNote(String verseNum);

    }

}
