package me.piebridge.bible.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.bridge.ReadingBridge;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ObjectUtils;

import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_BACKGROUND;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_BACKGROUND_HIGHLIGHT;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_BACKGROUND_HIGHLIGHT_SELECTION;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_BACKGROUND_SELECTION;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_LINK;
import static me.piebridge.bible.activity.AbstractReadingActivity.COLOR_RED;
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
        if (TextUtils.isEmpty(template)) {
            try {
                template = FileUtils.readAsString(activity.getAssets().open("reader.html"));
            } catch (IOException e) {
                LogUtils.d("cannot get template", e);
            }
        }
        if (readingBridge == null) {
            readingBridge = new ReadingBridge((ReadingBridge.Bridge) activity, this);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reading, container, false);
        nestedView = view.findViewById(R.id.nested);
        webView = view.findViewById(R.id.webview);
        webView.setFocusableInTouchMode(false);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(readingBridge, "android");
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void setForceVerse(int verse) {
        this.forceVerse = verse;
    }

    public boolean reloadData() {
        if (webView == null) {
            LogUtils.w("webView is null");
            return false;
        }
        Bundle bundle = getArguments();
        if (!TextUtils.isEmpty(osis) && osis.equals(bundle.getString(CURR))) {
            saveState();
        }
        String body = getBody();
        if (!TextUtils.isEmpty(body)) {
            int fontSize = bundle.getInt(FONT_SIZE);
            webView.getSettings().setDefaultFontSize(fontSize);
            webView.getSettings().setDefaultFixedFontSize(fontSize);
            webView.loadDataWithBaseURL("file:///android_asset/", body, "text/html", "utf-8", null);
            return true;
        } else {
            LogUtils.w("body is empty!");
            return false;
        }
    }

    public String getBody() {
        Bundle bundle = getArguments();
        String content = bundle.getString(CONTENT);
        if (TextUtils.isEmpty(content)) {
            return null;
        }
        return getBody(getTitle(), content);
    }

    public String getTitle() {
        Bundle bundle = getArguments();
        if (bundle != null) {
            return BibleUtils.getBookChapterVerse(bundle.getString(HUMAN),
                    BibleUtils.getChapter(bundle.getString(CURR)));
        } else {
            return null;
        }
    }

    private String getBody(String title, String content) {
        Bundle bundle = getArguments();
        osis = bundle.getString(CURR);
        String body = fixIfNeeded(bundle, content);
        String[] notes = keys(bundle.getBundle(NOTES));
        String css = fixCSS(bundle);
        int verseStart = NumberUtils.parseInt(getString(bundle, VERSE_START));
        int verseEnd = NumberUtils.parseInt(getString(bundle, VERSE_END));
        int verseBegin = getVerseBegin(bundle);
        LogUtils.d("title: " + title + ", forceVerse: " + forceVerse + ", verse: " + verse
                + ", verseStart: " + verseStart + ", VERSE: " + getString(bundle, VERSE)
                + ", notes: " + Arrays.toString(notes));
        String search = getString(bundle, SEARCH);
        String highlighted = getString(bundle, HIGHLIGHTED);
        String backgroundColor = getString(bundle, COLOR_BACKGROUND);
        String textColor = getString(bundle, COLOR_TEXT);
        String linkColor = getString(bundle, COLOR_LINK);
        String backgroundSelection = getString(bundle, COLOR_BACKGROUND_SELECTION);
        String backgroundHighlight = getString(bundle, COLOR_BACKGROUND_HIGHLIGHT);
        String backgroundHighlightSelection = getString(bundle, COLOR_BACKGROUND_HIGHLIGHT_SELECTION);
        return String.format(template, css,
                backgroundColor, textColor, linkColor,
                backgroundSelection, backgroundHighlight, backgroundHighlightSelection,
                verseBegin, verseStart, verseEnd,
                search, selectedVerses, highlighted,
                Arrays.toString(notes), title, body);
    }

    private String[] keys(Bundle bundle) {
        if (bundle == null) {
            return new String[0];
        }
        int size = bundle.size();
        return bundle.keySet().toArray(new String[size]);
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
            body = body.replaceAll("「", "“").replaceAll("」", "”").replaceAll("『", "‘").replaceAll("』",
                    "’");
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
        Context context = getActivity();
        if (context != null) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            LogUtils.d("title: " + getTitle() + ", scroll to " + top + ", density: " +
                    metrics.density);
            final int scrollY = (int) (top * metrics.density);
            nestedView.post(new Runnable() {
                @Override
                public void run() {
                    nestedView.smoothScrollTo(0, scrollY);
                }
            });
        }
    }

    private int currentPos() {
        Context context = getActivity();
        if (context != null) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return (int) (nestedView.getScrollY() / metrics.density);
        } else {
            return 0;
        }
    }

    public WebView getWebView() {
        return webView;
    }

    public void setHighlight(String verses, boolean added) {
        readingBridge.setHighlight(webView, verses, added);
    }

    public void setNote(String verse, boolean added) {
        readingBridge.setNote(webView, verse, added);
    }

    public long getNote(String verse) {
        Bundle bundle = getArguments().getBundle(NOTES);
        if (bundle == null) {
            return 0;
        } else {
            return bundle.getLong(verse);
        }
    }

    public void selectVerses(String verses, boolean added) {
        readingBridge.selectVerses(webView, verses, added);
    }

    public void onSelected(boolean highlight, String verses, String content) {
        highlightSelected = highlight;
        selectedVerses = verses;
        selectedContent = content;
        AbstractReadingActivity activity = (AbstractReadingActivity) getActivity();
        if (ObjectUtils.equals(activity.getCurrentOsis(), osis)) {
            activity.onSelected(highlight, verses, content);
        }
    }

    public void onSelected() {
        AbstractReadingActivity activity = (AbstractReadingActivity) getActivity();
        if (isCurrent()) {
            activity.onSelected(highlightSelected, selectedVerses, selectedContent);
        }
    }

    private boolean isCurrent() {
        AbstractReadingActivity activity = (AbstractReadingActivity) getActivity();
        return activity != null && ObjectUtils.equals(activity.getCurrentOsis(), osis);
    }

    public void updateFontSize(int fontSize) {
        getArguments().putInt(FONT_SIZE, fontSize);
        webView.getSettings().setDefaultFontSize(fontSize);
        webView.getSettings().setDefaultFixedFontSize(fontSize);
    }

}
