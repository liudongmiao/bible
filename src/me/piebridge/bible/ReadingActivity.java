package me.piebridge.bible;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.RecreateUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingActivity extends BaseActivity {

    private static final int SIZE = 0x4a5;

    @Override
    protected int retrieveOsisCount() {
        Uri uri = Provider.CONTENT_URI_CHAPTERS.buildUpon().build();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                return cursor.getInt(cursor.getColumnIndex(BaseColumns._COUNT));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot get chapter size", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return SIZE;
    }

    protected String getInitialOsis() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(OSIS, null);
    }

    protected int getInitialPosition() {
        return POSITION_UNKNOWN;
    }

    @Override
    protected boolean switchTheme() {
        saveOsis();
        return super.switchTheme();
    }

    @Override
    protected void onPause() {
        saveOsis();
        super.onPause();
    }

    private void saveOsis() {
        String osis = getCurrentOsis();
        if (!TextUtils.isEmpty(osis)) {
            final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putString(OSIS, getCurrentOsis());
            editor.commit();
        }
    }

}
