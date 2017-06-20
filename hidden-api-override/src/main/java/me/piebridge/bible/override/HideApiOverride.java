package me.piebridge.bible.override;

import android.webkit.WebView;
import android.widget.ZoomButtonsController;

public class HideApiOverride {

    private HideApiOverride() {

    }

    public static ZoomButtonsController getZoomButtonsController(WebView webView) {
        return webView.getZoomButtonsController();
    }

}
