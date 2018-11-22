package me.piebridge.bible.fragment;

import java.util.LinkedHashMap;
import java.util.Map;

import me.piebridge.bible.activity.SelectActivity;

/**
 * Created by thom on 16/7/6.
 */
public class SelectVerseFragment extends AbstractSelectChapterVerseFragment {

    private Map<String, Boolean> verses;

    public void selectItems(Map<String, Boolean> verses) {
        this.verses = verses;
        Map<String, String> grids = new LinkedHashMap<>();
        for (String verse : verses.keySet()) {
            grids.put(verse, verse);
        }
        super.setItems(grids, null);
    }

    @Override
    public boolean isGridEnabled(String item) {
        return verses != null && Boolean.TRUE.equals(verses.get(item));
    }

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setVerse(selected);
    }

    @Override
    public String toString() {
        return "SelectVerse";
    }

}
