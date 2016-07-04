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
import me.piebridge.bible.utils.WebViewUtils;

import static me.piebridge.bible.BaseReadingActivity.CONTENT;
import static me.piebridge.bible.BaseReadingActivity.CROSS;
import static me.piebridge.bible.BaseReadingActivity.CSS;
import static me.piebridge.bible.BaseReadingActivity.CURR;
import static me.piebridge.bible.BaseReadingActivity.FONT_SIZE;
import static me.piebridge.bible.BaseReadingActivity.HIGHLIGHTED;
import static me.piebridge.bible.BaseReadingActivity.HUMAN;
import static me.piebridge.bible.BaseReadingActivity.NIGHT;
import static me.piebridge.bible.BaseReadingActivity.NOTES;
import static me.piebridge.bible.BaseReadingActivity.OSIS;
import static me.piebridge.bible.BaseReadingActivity.POSITION;
import static me.piebridge.bible.BaseReadingActivity.RED;
import static me.piebridge.bible.BaseReadingActivity.SEARCH;
import static me.piebridge.bible.BaseReadingActivity.SELECTED;
import static me.piebridge.bible.BaseReadingActivity.SHANGTI;
import static me.piebridge.bible.BaseReadingActivity.VERSE_END;
import static me.piebridge.bible.BaseReadingActivity.VERSE_START;
import static me.piebridge.bible.BaseReadingActivity.VERSION;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingFragment extends Fragment {

    private static String template;

    private BaseReadingActivity mActivity;

    private WebView webView;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (BaseReadingActivity) activity;
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
        reloadIfNeeded();
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
        webView.addJavascriptInterface(new ReadingBridge(mActivity), "android");
        reloadData();
        return view;
    }

    public void reloadIfNeeded() {
        Bundle bundle = getArguments();
        String osis = bundle.getString(OSIS);
        if (mActivity != null && shouldUpdate(bundle)) {
            int position = bundle.getInt(POSITION);
            getArguments().putAll(mActivity.retrieveOsis(position, osis));
            mActivity.onOpenedOsis(this);
            if (webView != null) {
                reloadData();
            }
        }
    }

    private boolean shouldUpdate(Bundle bundle) {
        String osis = bundle.getString(OSIS);
        String current = bundle.getString(CURR);
        if (current == null) {
            return !"".equals(osis);
        } else {
            return !current.equals(osis);
        }
    }

    private void reloadData() {
        Bundle bundle = getArguments();
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
        int verseStart = BibleUtils.getNumber(bundle, VERSE_START);
        int verseEnd = BibleUtils.getNumber(bundle, VERSE_END);
        String search = getString(bundle, SEARCH);
        String selected = getString(bundle, SELECTED);
        String highlighted = getString(bundle, HIGHLIGHTED);
        return String.format(template, fontSize, css,
                verseStart, verseEnd, search, selected, highlighted,
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
        String fontPath = getFontPath();
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
            // http://mdbootstrap.com/css/colors/
            if (bundle.getBoolean(NIGHT, false)) {
                css.append("#CC0000");
            } else {
                css.append("#ff4444");
            }
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

    private String getFontPath() {
        File font;
        File dir = mActivity.getExternalFilesDir(null);
        if (dir != null) {
            font = new File(dir, "custom.ttf");
            if (font.isFile()) {
                return font.getAbsolutePath();
            }
        }
        return null;
    }

}

