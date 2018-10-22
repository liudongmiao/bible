package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.SeekBar;

import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SettingsActivity;
import me.piebridge.bible.bridge.ReadingBridge;

/**
 * Created by thom on 2018/9/28.
 */
public class FontsizeFragment extends AbstractDialogFragment implements DialogInterface.OnClickListener {

    public static final int FONTSIZE_DEFAULT = 18;

    public static final int FONTSIZE_MAX = 72;

    private static final String BODY = "body";

    private static final String FONTSIZE = "font-size";

    private SeekBar seekbar;

    private WebView webview;

    public FontsizeFragment() {
        setArguments(new Bundle());
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SettingsActivity activity = (SettingsActivity) getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getFontsizeTitle());
        builder.setView(R.layout.fragment_dialog);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, this);
        builder.setNeutralButton(R.string.reset, this);
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();

        Dialog dialog = getDialog();
        seekbar = dialog.findViewById(R.id.seekbar);
        webview = dialog.findViewById(R.id.webview);

        seekbar.setProgress(getFontsize());
        seekbar.setMax(FONTSIZE_MAX);

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                webview.getSettings().setDefaultFontSize(progress);
                webview.getSettings().setDefaultFixedFontSize(progress);
            }
        });


        webview.setFocusableInTouchMode(false);
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setUseWideViewPort(true);
        webview.getSettings().setDisplayZoomControls(false);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.addJavascriptInterface(new ReadingBridge(null, null), "android");

        webview.getSettings().setDefaultFontSize(seekbar.getProgress());
        webview.getSettings().setDefaultFixedFontSize(seekbar.getProgress());
        webview.loadDataWithBaseURL("file:///android_asset/", getBody(), "text/html", "utf-8", null);
    }

    public void setBody(String webviewData) {
        getArguments().putString(BODY, webviewData);
    }

    public String getBody() {
        return getArguments().getString(BODY);
    }

    public void setFontsize(int fontsizeValue) {
        getArguments().putInt(FONTSIZE, fontsizeValue);
    }

    public int getFontsize() {
        return getArguments().getInt(FONTSIZE, FONTSIZE_DEFAULT);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        SettingsActivity activity = (SettingsActivity) getActivity();
        if (activity == null) {
            return;
        }
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                PreferenceManager.getDefaultSharedPreferences(activity.getApplication()).edit()
                        .putInt(activity.getFontsizeKey(), FONTSIZE_DEFAULT)
                        .apply();
                activity.updateFontsize();
                break;
            case DialogInterface.BUTTON_POSITIVE:
                PreferenceManager.getDefaultSharedPreferences(activity.getApplication()).edit()
                        .putInt(activity.getFontsizeKey(), seekbar.getProgress())
                        .apply();
                activity.updateFontsize();
                break;
            default:
                break;
        }

    }

}
