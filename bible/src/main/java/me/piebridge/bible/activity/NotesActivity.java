package me.piebridge.bible.activity;

import android.database.Cursor;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;

/**
 * Created by thom on 2018/11/6.
 */
public class NotesActivity extends AbstractAnnotationActivity {

    @Override
    protected int getContentLayout() {
        return R.layout.drawer_notes;
    }

    @Override
    protected Cursor search(String book, String query, String order) {
        BibleApplication application = (BibleApplication) getApplication();
        return application.searchNotes(book, query, order);
    }

    @Override
    public void onResume() {
        super.onResume();
        setCheckedItem(R.id.menu_notes);
    }

}
