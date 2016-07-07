package me.piebridge.bible;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.WebViewUtils;

import static me.piebridge.bible.BaseActivity.COLOR_BACKGROUND;
import static me.piebridge.bible.BaseActivity.COLOR_HIGHLIGHT;
import static me.piebridge.bible.BaseActivity.COLOR_HIGHLIGHT_SELECTED;
import static me.piebridge.bible.BaseActivity.COLOR_LINK;
import static me.piebridge.bible.BaseActivity.COLOR_RED;
import static me.piebridge.bible.BaseActivity.COLOR_SELECTED;
import static me.piebridge.bible.BaseActivity.COLOR_TEXT;
import static me.piebridge.bible.BaseActivity.CONTENT;
import static me.piebridge.bible.BaseActivity.CROSS;
import static me.piebridge.bible.BaseActivity.CSS;
import static me.piebridge.bible.BaseActivity.FONT_SIZE;
import static me.piebridge.bible.BaseActivity.HIGHLIGHTED;
import static me.piebridge.bible.BaseActivity.HUMAN;
import static me.piebridge.bible.BaseActivity.NOTES;
import static me.piebridge.bible.BaseActivity.RED;
import static me.piebridge.bible.BaseActivity.SEARCH;
import static me.piebridge.bible.BaseActivity.SHANGTI;
import static me.piebridge.bible.BaseActivity.VERSE_END;
import static me.piebridge.bible.BaseActivity.VERSE_START;
import static me.piebridge.bible.BaseActivity.VERSION;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingFragment extends Fragment {

    private static final String VERSE = "verse";
    private static final String SELECTED_VERSES = "selectedVerses";
    private static final String SELECTED_CONTENT = "selectedContent";
    private static final String HIGHLIGHT_SELECTED = "highlightSelected";

    private static String template;
    private BaseActivity mActivity;
    private WebView webView;
    private ReadingBridge readingBridge;

    private int verse;
    private boolean highlightSelected;
    private String selectedVerses = "";
    private String selectedContent = "";

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (BaseActivity) activity;
        readingBridge = new ReadingBridge(mActivity);
        if (TextUtils.isEmpty(template)) {
            template = retrieveTemplate();
        }
    }

    private String retrieveTemplate() {
        try {
            return FileUtils.readAsString(mActivity.getAssets().open("reader.html"));
        } catch (IOException e) {
            LogUtils.d("cannot get template", e);
            return null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            verse = savedInstanceState.getInt(VERSE);
            selectedVerses = savedInstanceState.getString(SELECTED_VERSES);
            selectedContent = savedInstanceState.getString(SELECTED_CONTENT);
            highlightSelected = savedInstanceState.getBoolean(HIGHLIGHT_SELECTED);
        }
        if (webView != null) {
            reloadData();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pager, container, false);
        webView = (WebView) view.findViewById(R.id.webview);
        webView.setFocusableInTouchMode(false);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setUseWideViewPort(true);
        WebViewUtils.hideDisplayZoomControls(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(readingBridge, "android");
        if (mActivity != null) {
            webView.setBackgroundColor(mActivity.getBackground());
        }
        return view;
    }

    public void reloadData() {
        if (mActivity == null) {
            return;
        }
        Bundle bundle = getArguments();
        mActivity.updateColor(bundle);
        String content = (String) bundle.get(CONTENT);
        if (!TextUtils.isEmpty(content)) {
            String human = (String) bundle.get(HUMAN);
            String body = getBody(human, content);
            webView.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
        }
    }

    private String getBody(String title, String content) {
        Bundle bundle = getArguments();
        String body = fixIfNeeded(bundle, content);
        String[] notes = bundle.getStringArray(NOTES);
        int fontSize = bundle.getInt(FONT_SIZE);
        String css = fixCSS(bundle);
        int verseStart = NumberUtils.parseInt(getString(bundle, VERSE_START));
        int verseEnd = NumberUtils.parseInt(getString(bundle, VERSE_END));
        String search = getString(bundle, SEARCH);
        String highlighted = getString(bundle, HIGHLIGHTED);
        String backgroundColor = getString(bundle, COLOR_BACKGROUND);
        String textColor = getString(bundle, COLOR_TEXT);
        String linkColor = getString(bundle, COLOR_LINK);
        String selectedColor = getString(bundle, COLOR_SELECTED);
        String highlightColor = getString(bundle, COLOR_HIGHLIGHT);
        String highlightSelectedColor = getString(bundle, COLOR_HIGHLIGHT_SELECTED);
        return String.format(template, fontSize, css,
                backgroundColor, textColor, linkColor,
                selectedColor, highlightColor, highlightSelectedColor,
                verse > 0 ? verse : NumberUtils.parseInt(getString(bundle, VERSE), verseStart),
                verseStart, verseEnd, search, selectedVerses, highlighted,
                Arrays.toString(notes), title, body);
    }

    private String getString(Bundle bundle, String key) {
        String value = bundle.getString(key);
        return value == null ? "" : value;
    }

    private String fixCSS(Bundle bundle) {
        StringBuilder css = new StringBuilder();
        if (bundle.containsKey(CSS)) {
            css.append(bundle.getString(CSS));
        }
        String fontPath = BibleUtils.getFontPath(mActivity);
        if (!TextUtils.isEmpty(fontPath)) {
            css.append("@font-face { font-family: 'custom'; src: url('");
            css.append(fontPath);
            css.append("');}");
        }
        if (!bundle.getBoolean(CROSS, false)) {
            css.append("a.x-link, sup.crossreference { display: none }");
        }
        if (bundle.getBoolean(RED, true)) {
            css.append(".wordsofchrist, .woj, .wj { color: ");
            css.append(bundle.get(COLOR_RED));
            css.append("; }");

        }
        return css.toString();
    }

    private String fixIfNeeded(Bundle bundle, String content) {
        String body = content;
        String version = bundle.getString(VERSION);
        if (Locale.getDefault().equals(Locale.SIMPLIFIED_CHINESE)
                || "CCB".equalsIgnoreCase(version)
                || (!TextUtils.isEmpty(version) && version.endsWith("ss"))) {
            body = body.replaceAll("「", "“").replaceAll("」", "”").replaceAll("『", "‘").replaceAll("』", "’");
        }
        if (bundle.getBoolean(SHANGTI, false)) {
            body = body.replace("　神", "上帝");
        } else {
            body = body.replace("上帝", "　神");
        }
        return body;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(VERSE, readingBridge.getVerse(webView));
        outState.putString(SELECTED_VERSES, readingBridge.getSelectedVerses(selectedVerses));
        outState.putString(SELECTED_CONTENT, readingBridge.getSelectedContent(selectedContent));
        outState.putBoolean(HIGHLIGHT_SELECTED, readingBridge.isHighlightSelected(highlightSelected));
        super.onSaveInstanceState(outState);
    }

}

