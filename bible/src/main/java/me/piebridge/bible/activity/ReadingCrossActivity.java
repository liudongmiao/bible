package me.piebridge.bible.activity;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 16/7/4.
 */
public class ReadingCrossActivity extends ReadingItemsActivity {

    @Override
    protected void setTheme() {
        ThemeUtils.setDialogTheme(this);
    }

    @Override
    protected void setToolbar(Toolbar toolbar) {
        // do nothing
    }

    @Override
    protected int getContentLayout() {
        return R.layout.activity_reading_cross;
    }

    @Override
    public Bundle retrieveOsis(int position, String osis) {
        Bundle bundle = super.retrieveOsis(position, osis);
        // for cross, decrease font size
        bundle.putInt(FONT_SIZE, bundle.getInt(FONT_SIZE) - 1);
        // don't allow cross in cross
        bundle.putBoolean(CROSS, false);
        return bundle;
    }

    @Override
    protected View findHeader() {
        return findViewById(R.id.header);
    }

    @Override
    protected void updateTaskDescription(String label) {
        // no need to update
    }

    @Override
    protected void switchToVersion(String version) {
        // do nothing
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void onSelected(boolean highlight, String verses, String content) {
        // do nothing
    }

}
