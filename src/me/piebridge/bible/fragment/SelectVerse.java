package me.piebridge.bible.fragment;

import java.util.LinkedHashMap;
import java.util.Map;

import me.piebridge.bible.SelectActivity;

/**
 * Created by thom on 16/7/6.
 */
public class SelectVerse extends SelectChapter {

    private Map<String, Boolean> verses;

    public void setVerses(Map<String, Boolean> verses) {
        this.verses = verses;
        Map<String, String> grids = new LinkedHashMap<String, String>();
        for (String verse : verses.keySet()) {
            grids.put(verse, verse);
        }
        setData(grids, null);
    }

    @Override
    public boolean isGridEnabled(String item) {
        return verses != null && Boolean.TRUE.equals(verses.get(item));
    }

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setVerse(selected);
    }

}
