package me.piebridge.bible;

import android.webkit.JavascriptInterface;

import java.lang.ref.WeakReference;

/**
 * Created by thom on 15/10/19.
 */
public class ReadingBridge {

    private final WeakReference<Bridge> wr;

    public ReadingBridge(Bridge bridge) {
        wr = new WeakReference<Bridge>(bridge);
    }

    @JavascriptInterface
    public void setVerse(String verse) {
        Bridge bridge = wr.get();
        if (bridge != null) {
            bridge.setVerse(verse);
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

    interface Bridge {

        void setVerse(String verse);

        void setHighlighted(String highlighted);

        void showAnnotation(String link, String annotation);

        void showNote(String verseNum);

        void setCopyText(String text);

    }

}
