package me.piebridge.bible.fragment;

import me.piebridge.bible.activity.SelectActivity;

/**
 * Created by thom on 16/7/6.
 */
public class SelectChapterFragment extends AbstractSelectFragment {

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setChapter(selected);
    }

}
