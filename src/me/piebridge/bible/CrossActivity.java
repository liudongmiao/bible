package me.piebridge.bible;

import android.os.Bundle;

import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 16/7/4.
 */
public class CrossActivity extends ResultActivity {

    @Override
    protected void updateTheme() {
        ThemeUtils.setDialogTheme(this);
    }

    @Override
    protected int getContentLayout() {
        return R.layout.cross;
    }

    @Override
    protected void updateTaskDescription(String bookName, String chapterVerse) {
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

}
