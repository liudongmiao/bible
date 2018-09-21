package me.piebridge.bible.bridge;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;

/**
 * Created by thom on 15/10/19.
 */
public class ReadingBridge {

    private final WeakReference<Bridge> wr;
    private final ReadingFragment fragment;

    private static final int FIELDS_COPY_TEXT = 3;
    private static final int FIELD_HIGHLIGHT = 0;
    private static final int FIELD_SELECTED = 1;
    private static final int FIELD_CONTENT = 2;

    private static final long SECONDS_3 = 3000;

    private final Object verseLock = new Object();

    private boolean loaded;
    private int verse;
    private Boolean highlightSelected;
    private String selectedVerses = null;
    private String selectedContent = null;

    public ReadingBridge(Bridge bridge, ReadingFragment fragment) {
        wr = new WeakReference<>(bridge);
        this.fragment = fragment;
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
        String[] fields = text.split("\n", FIELDS_COPY_TEXT);
        // highlight selected's length
        if (fields.length == FIELDS_COPY_TEXT) {
            highlightSelected = NumberUtils.parseInt(fields[FIELD_HIGHLIGHT]) > 0;
            selectedVerses = fields[FIELD_SELECTED];
            selectedContent = fields[FIELD_CONTENT];
        } else {
            highlightSelected = false;
            selectedVerses = "";
            selectedContent = "";
        }
        Bridge bridge = wr.get();
        if (bridge != null) {
            fragment.onSelected(highlightSelected, selectedVerses, selectedContent);
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

    @JavascriptInterface
    public void setTop(final String top) {
        if (fragment != null) {
            fragment.scrollTo(NumberUtils.parseInt(top));
        }
    }

    @JavascriptInterface
    public boolean onLoaded() {
        loaded = true;
        return true;
    }

    public int getVerse(WebView webview, int top) {
        if (!loaded) {
            return verse;
        }
        if (top == 0) {
            return 0;
        }
        synchronized (verseLock) {
            webview.loadUrl("javascript:getFirstVisibleVerse(" + top + ");");
            try {
                verseLock.wait(SECONDS_3);
            } catch (InterruptedException e) {
                LogUtils.d("interrupted", e);
            }
        }
        return verse;
    }

    public void setHighlight(WebView webView, String verses, boolean added) {
        String url = "javascript:setHighlightVerses(\"" + verses + "\", " + added + ");";
        LogUtils.d("url: " + url);
        webView.loadUrl(url);
    }

    public void setNote(WebView webView, String verse, boolean added) {
        String url = "javascript:addNote(\"" + verse + "\", " + added + ");";
        LogUtils.d("url: " + url);
        webView.loadUrl(url);
    }

    public void selectVerses(WebView webView, String verses, boolean added) {
        String url = "javascript:selectVerses(\"" + verses + "\", " + added + ");";
        LogUtils.d("url: " + url);
        webView.loadUrl(url);
    }

    @JavascriptInterface
    public void setHighlight(String verses) {
        LogUtils.d("setHighlight: " + verses);
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.saveHighlight(verses);
        }
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

    public void updateBundle(Bundle bundle) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.updateBundle(bundle);
        }
    }

    public interface Bridge {

        Bundle retrieveOsis(int position, String osis);

        void showAnnotation(String link, String annotation);

        void showNote(String verseNum);

        void updateBundle(Bundle bundle);

        void saveHighlight(String verses);

    }

}
