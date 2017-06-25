package me.piebridge.bible.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SelectActivity;
import me.piebridge.bible.adapter.GridAdapter;

/**
 * Created by thom on 16/7/6.
 */
public class SelectBookFragment extends AbstractSelectFragment {

    private GridView left;
    private GridView right;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select_book, container, false);
        left = (GridView) view.findViewById(R.id.left);
        right = (GridView) view.findViewById(R.id.right);
        SelectActivity selectActivity = wr.get();
        if (selectActivity != null) {
            left.setAdapter(new GridAdapter(selectActivity, this, typeface));
            right.setAdapter(new GridAdapter(selectActivity, this, typeface));
        }
        left.setOnItemClickListener(this);
        right.setOnItemClickListener(this);
        updateAdapter();
        return view;
    }

    @Override
    protected void updateAdapter() {
        if (left != null && right != null && items != null) {
            GridAdapter leftAdapter = (GridAdapter) left.getAdapter();
            GridAdapter rightAdapter = (GridAdapter) right.getAdapter();
            @SuppressWarnings("unchecked")
            List<String>[] data = (List<String>[]) convertItems(items.keySet());
            leftAdapter.setData(data[0]);
            rightAdapter.setData(data[1]);
            left.setSelection(leftAdapter.getPosition(selected));
            right.setSelection(rightAdapter.getPosition(selected));
        }
    }

    private List[] convertItems(Collection<String> items) {
        List<String> keys = new ArrayList<>(items);
        int matt = keys.indexOf("Matt");
        int gen = keys.indexOf("Gen");
        List<String> oldData;
        List<String> newData;
        if (gen > -1 && matt > -1) {
            oldData = keys.subList(0, matt);
            newData = keys.subList(matt, keys.size());
        } else if (gen > -1) {
            oldData = keys;
            newData = Collections.emptyList();
        } else {
            oldData = Collections.emptyList();
            newData = keys;
        }
        return new List[] {
            oldData, newData
        };
    }

    @Override
    protected void notifyDataSetChanged() {
        ((GridAdapter) left.getAdapter()).notifyDataSetChanged();
        ((GridAdapter) right.getAdapter()).notifyDataSetChanged();
    }

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setBook(selected);
    }

}
