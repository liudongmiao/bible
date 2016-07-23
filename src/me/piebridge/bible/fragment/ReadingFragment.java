package me.piebridge.bible.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.NestedScrollView;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import me.piebridge.bible.R;
import me.piebridge.bible.bridge.ReadingBridge;
import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.WebViewUtils;

import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_BACKGROUND;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_HIGHLIGHT;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_HIGHLIGHT_SELECTED;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_LINK;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_RED;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_SELECTED;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_TEXT;
import static me.piebridge.bible.activity.AbstractReadingActivity.CONTENT;
import static me.piebridge.bible.activity.AbstractReadingActivity.CROSS;
import static me.piebridge.bible.activity.AbstractReadingActivity.CSS;
import static me.piebridge.bible.activity.AbstractReadingActivity.CURR;
import static me.piebridge.bible.activity.AbstractReadingActivity.FONT_PATH;
import static me.piebridge.bible.activity.AbstractReadingActivity.FONT_SIZE;
import static me.piebridge.bible.activity.AbstractReadingActivity.HIGHLIGHTED;
import static me.piebridge.bible.activity.AbstractReadingActivity.HUMAN;
import static me.piebridge.bible.activity.AbstractReadingActivity.NOTES;
import static me.piebridge.bible.activity.AbstractReadingActivity.RED;
import static me.piebridge.bible.activity.AbstractReadingActivity.SEARCH;
import static me.piebridge.bible.activity.AbstractReadingActivity.SHANGTI;
import static me.piebridge.bible.activity.AbstractReadingActivity.VERSE_END;
import static me.piebridge.bible.activity.AbstractReadingActivity.VERSE_START;
import static me.piebridge.bible.activity.AbstractReadingActivity.VERSION;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingFragment extends Fragment {

    private static final String VERSE = "verse";
    private static final String SELECTED_VERSES = "selectedVerses";
    private static final String SELECTED_CONTENT = "selectedContent";
    private static final String HIGHLIGHT_SELECTED = "highlightSelected";

    private static String template;
    private WebView webView;
    private NestedScrollView nestedView;
    private ReadingBridge readingBridge;

    private String osis;
    private int verse;
    private int forceVerse;
    private boolean highlightSelected;
    private String selectedVerses = "";
    private String selectedContent = "";

    @Override
    @SuppressWarnings("deprecation")
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        readingBridge = new ReadingBridge((AbstractReadingActivity) activity, this);
        if (TextUtils.isEmpty(template)) {
            try {
                template = FileUtils.readAsString(activity.getAssets().open("reader.html"));
            } catch (IOException e) {
                LogUtils.d("cannot get template", e);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle bundle = getArguments();
        if (savedInstanceState != null) {
            verse = savedInstanceState.getInt(VERSE);
            selectedVerses = savedInstanceState.getString(SELECTED_VERSES);
            selectedContent = savedInstanceState.getString(SELECTED_CONTENT);
            highlightSelected = savedInstanceState.getBoolean(HIGHLIGHT_SELECTED);
            if (readingBridge != null && bundle != null) {
                readingBridge.updateBundle(bundle);
            }
        }
        if (webView != null && bundle != null) {
            reloadData();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reading, container, false);
        nestedView = (NestedScrollView) view.findViewById(R.id.nested);
        webView = (WebView) view.findViewById(R.id.webview);
        webView.setFocusableInTouchMode(false);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setUseWideViewPort(true);
        WebViewUtils.hideDisplayZoomControls(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(readingBridge, "android");
        return view;
    }

    public void setForceVerse(int verse) {
        this.forceVerse = verse;
    }

    public void reloadData() {
        Bundle bundle = getArguments();
        if (!TextUtils.isEmpty(osis) && osis.equals(bundle.getString(CURR))) {
            saveState();
        }
        String content = bundle.getString(CONTENT);
        if (!TextUtils.isEmpty(content)) {
            String title = getTitle(bundle);
            String body = getBody(title, content);
            webView.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
        }
    }

    private String getTitle(Bundle bundle) {
        if (bundle != null) {
            return BibleUtils.getBookChapterVerse(bundle.getString(HUMAN), BibleUtils.getChapter(bundle.getString(CURR)));
        } else {
            return null;
        }
    }

    private String getBody(String title, String content) {
        Bundle bundle = getArguments();
        osis = bundle.getString(CURR);
        String body = fixIfNeeded(bundle, content);
        String[] notes = bundle.getStringArray(NOTES);
        int fontSize = bundle.getInt(FONT_SIZE);
        String css = fixCSS(bundle);
        int verseStart = NumberUtils.parseInt(getString(bundle, VERSE_START));
        int verseEnd = NumberUtils.parseInt(getString(bundle, VERSE_END));
        int verseBegin = getVerseBegin(bundle);
        LogUtils.d("title: " + title + ", forceVerse: " + forceVerse + ", verse: " + verse
                + ", verseStart: " + verseStart + ", VERSE: " + getString(bundle, VERSE));
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
                verseBegin, verseStart, verseEnd,
                search, selectedVerses, highlighted,
                Arrays.toString(notes), title, body);
    }

    private int getVerseBegin(Bundle bundle) {
        if (forceVerse > 0) {
            return forceVerse;
        } else if (verse > 0) {
            return verse;
        } else {
            int verseStart = NumberUtils.parseInt(getString(bundle, VERSE_START));
            return NumberUtils.parseInt(getString(bundle, VERSE), verseStart);
        }
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
        if (bundle.containsKey(FONT_PATH)) {
            css.append("@font-face { font-family: 'custom'; src: url('");
            css.append(bundle.getString(FONT_PATH));
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
        outState.putInt(VERSE, readingBridge.getVerse(webView, currentPos()));
        outState.putString(SELECTED_VERSES, readingBridge.getSelectedVerses(selectedVerses));
        outState.putString(SELECTED_CONTENT, readingBridge.getSelectedContent(selectedContent));
        outState.putBoolean(HIGHLIGHT_SELECTED, readingBridge.isHighlightSelected(highlightSelected));
        super.onSaveInstanceState(outState);
    }

    private void saveState() {
        verse = readingBridge.getVerse(webView, currentPos());
        selectedVerses = readingBridge.getSelectedVerses(selectedVerses);
        selectedContent = readingBridge.getSelectedContent(selectedContent);
        highlightSelected = readingBridge.isHighlightSelected(highlightSelected);
    }

    public void scrollTo(int top) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        LogUtils.d("title: " + getTitle(getArguments()) + ", scroll to " + top + ", density: " + metrics.density);
        final int scrollY = (int) (top * metrics.density);
        nestedView.post(new Runnable() {
            @Override
            public void run() {
                nestedView.smoothScrollTo(0, scrollY);
            }
        });
    }

    private int currentPos() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return (int) (nestedView.getScrollY() / metrics.density);
    }

}
