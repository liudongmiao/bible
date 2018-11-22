package me.piebridge.bible.fragment;

import me.piebridge.bible.activity.SelectActivity;

/**
 * Created by thom on 16/7/6.
 */
public class SelectChapterFragment extends AbstractSelectChapterVerseFragment {

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setChapter(selected);
    }

    @Override
    public String toString() {
        return "SelectChapter";
    }

}
