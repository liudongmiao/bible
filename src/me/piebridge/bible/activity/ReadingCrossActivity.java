package me.piebridge.bible.activity;

import android.os.Bundle;
import android.view.View;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 16/7/4.
 */
public class ReadingCrossActivity extends ReadingItemsActivity {

    @Override
    protected void updateTheme() {
        ThemeUtils.setDialogTheme(this);
    }

    @Override
    protected int getContentLayout() {
        return R.layout.activity_reading_cross;
    }

    @Override
    protected void updateTaskDescription(String label) {
        // for cross, don't update task description
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

}
