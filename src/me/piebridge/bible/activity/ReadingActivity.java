package me.piebridge.bible.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.content.SharedPreferencesCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import me.piebridge.bible.Provider;
import me.piebridge.bible.R;
import me.piebridge.bible.Search;
import me.piebridge.bible.Settings;
import me.piebridge.bible.Versions;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingActivity extends AbstractReadingActivity {

    private static final int SIZE = 1189;

    private static final int MENU_SEARCH = 2;
    private static final int MENU_SETTINGS = 3;
    private static final int MENU_VERSIONS = 4;

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
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit().putString(OSIS, getCurrentOsis());
            SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, android.R.string.search_go, MENU_SEARCH, android.R.string.search_go).setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, R.string.settings, MENU_SETTINGS, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(Menu.NONE, R.string.manageversion, MENU_VERSIONS, R.string.manageversion).setIcon(android.R.drawable.ic_menu_more);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getOrder()) {
            case MENU_SEARCH:
                startActivity(new Intent(this, Search.class));
                return true;
            case MENU_SETTINGS:
                startActivity(new Intent(this, Settings.class));
                return true;
            case MENU_VERSIONS:
                startActivity(new Intent(this, Versions.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
