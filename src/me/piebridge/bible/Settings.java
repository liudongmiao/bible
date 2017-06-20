package me.piebridge.bible;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public class Settings extends PreferenceActivity {

    public static final String RED = "red";
    public static final String FLINK = "xlink";
    public static final String XLINK = "flink";
    public static final String FONTSIZE = "fontsize";
    public static final String VERSION = "version";
    public static final String SHANGTI = "shangti";

    public static final int FONTSIZE_MIN = 1;
    public static final int FONTSIZE_MED = 14;
    public static final int FONTSIZE_MAX = 72;

    private Bible bible = null;
    private String versionName = null;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
        }
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(createPreferenceHierarchy(root));
    }

    private PreferenceScreen createPreferenceHierarchy(PreferenceScreen root) {
        root.addPreference(addPreference(FONTSIZE, R.string.fontsize));
        root.addPreference(addBooleanPreference(RED, R.string.red, R.string.wojinred));
        root.addPreference(addBooleanPreference(FLINK, R.string.flink, 0));
        root.addPreference(addBooleanPreference(XLINK, R.string.xlink, 0));
        root.addPreference(addBooleanPreference(SHANGTI, R.string.shangti, R.string.shangti_or_shen));
        root.addPreference(addPreference(VERSION, R.string.version));
        return root;
    }

    private Preference addPreference(String key, int title) {
        Preference preference = new Preference(this);
        preference.setKey(key);
        preference.setTitle(title);
        switch (title) {
            case R.string.fontsize:
                if (bible == null) {
                    bible = Bible.getInstance(getBaseContext());
                }
                preference.setSummary(getString(R.string.fontsummary, bible.getVersionName(bible.getVersion())));
                break;
            case R.string.version:
                preference.setSummary(versionName);
                break;
        }
        return preference;
    }

    private Preference addBooleanPreference(String key, int title, int summary) {
        Preference preference = new SwitchPreference(this);
        preference.setKey(key);
        preference.setTitle(title);
        if (summary != 0) {
            preference.setSummary(summary);
        }
        switch (title) {
        case R.string.log:
        case R.string.nightmode:
        case R.string.xlink:
        case R.string.shangti:
            preference.setDefaultValue(false);
            break;
        case R.string.red:
        case R.string.flink:
        case R.string.pinch:
            preference.setDefaultValue(true);
            break;
        }
        return preference;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, final Preference preference) {
        final String key = preference.getKey();
        if (FONTSIZE.equals(key)) {
            setupFontDialog(preference);
        } else if (VERSION.equals(key)) {
            String content = getString(R.string.about_source, "<a href=\"http://github.com/liudongmiao/bible\">bible</a>") + "<br />"
                    + getString(R.string.about_libraries, "<a href=\"http://github.com/emilsjolander/StickyListHeaders\">StickyListHeaders</a>");
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.app_name)
                    .setMessage(Html.fromHtml(content)).setPositiveButton(android.R.string.ok, null).show();
            ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        }
        return true;
    }

    private void setupFontDialog(final Preference preference) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog, null);

        final SeekBar seekbar = (SeekBar) view.findViewById(R.id.seekbar);

        seekbar.setMax(FONTSIZE_MAX);
        seekbar.setProgress(getInt(FONTSIZE, FONTSIZE_MED));

        new AlertDialog.Builder(this)
            .setTitle(R.string.fontsize)
            .setView(view)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int fontsize = seekbar.getProgress();
                    if (fontsize < FONTSIZE_MIN) {
                        fontsize = FONTSIZE_MIN;
                    }
                    setInt(FONTSIZE, fontsize);
                    preference.setSummary(getString(R.string.fontsummary, bible.getVersionName(bible.getVersion())));
                }
            }).setNegativeButton(android.R.string.no, null).show();
    }

    private int getInt(String key, int defValue) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (FONTSIZE.equals(key)) {
            if (bible == null) {
                bible = Bible.getInstance(getBaseContext());
            }
            String version = bible.getVersion();
            int fontsize = sp.getInt(FONTSIZE + "-" + version, 0);
            if (fontsize == 0) {
                return sp.getInt(FONTSIZE, defValue);
            }
            return fontsize;
        }
        return sp.getInt(key, defValue);
    }

    private void setInt(String key, int value) {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt(key, value);
        if (FONTSIZE.equals(key)) {
            if (bible == null) {
                bible = Bible.getInstance(getBaseContext());
            }
            String version = bible.getVersion();
            editor.putInt(key + "-" + version, value);
        }
        editor.apply();
    }

}
