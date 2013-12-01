package me.piebridge.bible;

import com.android.mms.ui.NumberPickerDialog;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

public class Settings extends PreferenceActivity implements OnPreferenceChangeListener {

	private static Bible bible = null;
	private static final String TAG = "me.piebridge.bible$Settings";

	public static String RED = "red";
	public static String FONTSIZE = "fontsize";
	public static String NIGHTMODE = "nightmode";

	@SuppressWarnings("deprecation")
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
		setPreferenceScreen(createPreferenceHierarchy(root));
	}

	private PreferenceScreen createPreferenceHierarchy(PreferenceScreen root) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			root.addPreference(addPreference(FONTSIZE, R.string.fontsize));
		}
		root.addPreference(addBooleanPreference(RED, R.string.red, R.string.wojinred));
		root.addPreference(addBooleanPreference(NIGHTMODE, R.string.nightmode, 0));
		return root;
	}

	private Preference addPreference(String key, int title) {
		Preference preference = new Preference(this);
		preference.setKey(key);
		preference.setTitle(title);
		if (FONTSIZE.equals(key)) {
			if (bible == null) {
				bible = Bible.getBible(getBaseContext());
			}
			preference.setSummary(getString(R.string.fontsummary,
					getInt(FONTSIZE, Chapter.FONTSIZE_MED),
					bible.getVersionName(bible.getVersion())));
		}
		return preference;
	}

	private Preference addBooleanPreference(String key, int title, int summary) {
		Preference preference;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			preference = getBooleanPreference();
		} else {
			preference = getBooleanPreferenceGB();
		}
		preference.setKey(key);
		preference.setTitle(title);
		if (summary != 0) {
			preference.setSummary(summary);
		}
		preference.setOnPreferenceChangeListener(this);
		switch (title) {
		case R.string.red:
			preference.setDefaultValue(getBoolean(key, true));
			break;
		case R.string.nightmode:
			preference.setDefaultValue(getBoolean(key, false));
			break;
		}
		return preference;
	}

	Preference getBooleanPreferenceGB() {
		return new CheckBoxPreference(this);
	}

	@SuppressLint("NewApi")
	Preference getBooleanPreference() {
		return new SwitchPreference(this);
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		String key = preference.getKey();
		if (newValue instanceof Boolean) {
			setBoolean(key, (Boolean) newValue);
		}
		return true;
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		final String key = preference.getKey();
		if (FONTSIZE.equals(key) && Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			new NumberPickerDialog(this, new NumberPickerDialog.OnNumberSetListener() {
				@Override
				public void onNumberSet(int number) {
					setInt(key, number);
					Log.d(TAG, "set number to " + number);
				}
			},
			getInt(FONTSIZE, Chapter.FONTSIZE_MED),
			Chapter.FONTSIZE_MIN, Chapter.FONTSIZE_MAX, R.string.fontsize).show();
		}
		return true;
	}

	private int getInt(String key, int defValue) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		if (FONTSIZE.equals(key)) {
			if (bible == null) {
				bible = Bible.getBible(getBaseContext());
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

	private boolean getBoolean(String key, boolean defValue) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		return sp.getBoolean(key, defValue);
	}

	private void setInt(String key, int value) {
		final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putInt(key, value);
		if (FONTSIZE.equals(key)) {
			if (bible == null) {
				bible = Bible.getBible(getBaseContext());
			}
			String version = bible.getVersion();
			editor.putInt(key + "-" + version, value);
			editor.putInt(key, value);
		}
		editor.commit();
	}

	private void setBoolean(String key, boolean defValue) {
		final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putBoolean(key, defValue);
		editor.commit();
	}
}
