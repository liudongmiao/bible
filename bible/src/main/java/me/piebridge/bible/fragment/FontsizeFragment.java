package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SettingsActivity;
import me.piebridge.bible.bridge.ReadingBridge;
import me.piebridge.bible.utils.FileUtils;

/**
 * Created by thom on 2018/9/28.
 */
public class FontsizeFragment extends AbstractDialogFragment implements DialogInterface.OnClickListener {

    public static final int FONTSIZE_DEFAULT = 18;

    public static final int FONTSIZE_MAX = 72;

    private static final String BODY = "body";

    private static final String FONTSIZE_KEY = "fontsize-key";

    private static final String FONTSIZE_TITLE = "fontsize-title";

    private static final String FONTSIZE_VALUE = "fontsize-value";

    private SeekBar seekbar;

    private ImageButton minus;
    private ImageButton plus;
    private TextView size;

    private WebView webview;

    public FontsizeFragment() {
        setArguments(new Bundle());
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SettingsActivity activity = (SettingsActivity) getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getFontsizeTitle());
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

        minus = dialog.findViewById(R.id.minus);
        plus = dialog.findViewById(R.id.plus);
        size = dialog.findViewById(R.id.size);

        seekbar.setProgress(getFontsizeValue());
        seekbar.setMax(FONTSIZE_MAX);

        size.setText(getString(R.string.count, seekbar.getProgress()));

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                changeSize(progress);
            }
        });

        minus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                int progress = seekbar.getProgress() - 1;
                if (progress >= 1) {
                    seekbar.setProgress(progress);
                    changeSize(progress);
                }
            }

        });

        plus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                int progress = seekbar.getProgress() + 1;
                if (progress <= FONTSIZE_MAX) {
                    seekbar.setProgress(progress);
                    changeSize(progress);
                }
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
        webview.loadDataWithBaseURL("file:///android_asset/", getBody(), "text/html", FileUtils.UTF_8, null);
    }

    public void changeSize(int progress) {
        minus.setEnabled(progress > 1);
        plus.setEnabled(progress < FONTSIZE_MAX);
        size.setText(getString(R.string.count, progress));
        webview.getSettings().setDefaultFontSize(progress);
        webview.getSettings().setDefaultFixedFontSize(progress);
    }

    public void setBody(String webviewData) {
        getArguments().putString(BODY, webviewData);
    }

    public String getBody() {
        return getArguments().getString(BODY);
    }

    public void setFontsize(String key, String title, int value) {
        getArguments().putString(FONTSIZE_KEY, key);
        getArguments().putString(FONTSIZE_TITLE, title);
        getArguments().putInt(FONTSIZE_VALUE, value);
    }

    private String getFontsizeKey() {
        return getArguments().getString(FONTSIZE_KEY);
    }

    private String getFontsizeTitle() {
        return getArguments().getString(FONTSIZE_TITLE);
    }

    private int getFontsizeValue() {
        return getArguments().getInt(FONTSIZE_VALUE, FONTSIZE_DEFAULT);
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
                        .remove(getFontsizeKey())
                        .apply();
                activity.updateFontsize();
                break;
            case DialogInterface.BUTTON_POSITIVE:
                PreferenceManager.getDefaultSharedPreferences(activity.getApplication()).edit()
                        .putInt(getFontsizeKey(), seekbar.getProgress())
                        .apply();
                activity.updateFontsize();
                break;
            default:
                break;
        }
    }

}
