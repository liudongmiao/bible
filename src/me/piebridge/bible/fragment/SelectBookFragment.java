package me.piebridge.bible.fragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.piebridge.bible.activity.SelectActivity;

/**
 * Created by thom on 16/7/6.
 */
public class SelectBookFragment extends AbstractSelectFragment {

    private static final int COLUMN_2 = 2;

    @Override
    protected int getNumColumns() {
        return COLUMN_2;
    }

    @Override
    protected List<String> convertItems(Collection<String> items) {
        List<String> keys = new ArrayList<String>(items);
        int matt = keys.indexOf("Matt");
        int gen = keys.indexOf("Gen");
        List<String> oldData;
        List<String> newData;
        List<String> data = new ArrayList<String>();
        if (gen > -1 && matt > -1) {
            oldData = keys.subList(0, matt);
            newData = keys.subList(matt, keys.size());
        } else if (gen > -1) {
            oldData = keys;
            newData = Collections.emptyList();
        } else if (matt > -1) {
            oldData = Collections.emptyList();
            newData = keys;
        } else {
            // shouldn't happen
            oldData = Collections.emptyList();
            newData = Collections.emptyList();
            data.addAll(keys);
        }
        int max = Math.max(oldData.size(), newData.size());
        for (int i = 0; i < max; ++i) {
            if (i < oldData.size()) {
                data.add(oldData.get(i));
            } else {
                data.add("");
            }
            if (i < newData.size()) {
                data.add(newData.get(i));
            } else {
                data.add("");
            }
        }
        return data;
    }

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setBook(selected);
    }

}
