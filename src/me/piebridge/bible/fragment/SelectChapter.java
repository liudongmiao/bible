package me.piebridge.bible.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.R;
import me.piebridge.bible.SelectActivity;

/**
 * Created by thom on 16/7/6.
 */
public class SelectChapter extends Fragment implements AdapterView.OnItemClickListener, GridAdapter.GridChecker {

    private GridView gridView;
    private SelectActivity mActivity;

    private String selected;
    private Map<String, String> items;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.select_chapter_verse, container, false);
        gridView = (GridView) view.findViewById(R.id.gridView);
        gridView.setAdapter(new GridAdapter(mActivity, this));
        gridView.setOnItemClickListener(this);
        updateIfNeeded();
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (SelectActivity) activity;
    }

    private void updateIfNeeded() {
        if (gridView != null && items != null) {
            List<String> keys = new ArrayList<String>(items.keySet());
            ((GridAdapter) gridView.getAdapter()).setData(keys);
            gridView.smoothScrollToPosition(keys.indexOf(selected));
        }
    }

    public void setData(Map<String, String> items, String selected) {
        this.items = items;
        this.selected = selected;
        updateIfNeeded();
    }

    @Override
    public String getGridName(String key) {
        if (items != null) {
            return items.get(key);
        } else {
            return key;
        }
    }

    @Override
    public boolean isGridEnabled(String key) {
        return true;
    }

    @Override
    public boolean isGridChecked(String key) {
        return key.equals(selected);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        selected = String.valueOf(parent.getAdapter().getItem(position));
        onSelected(mActivity, selected);
        ((GridAdapter) gridView.getAdapter()).notifyDataSetChanged();
    }

    protected void onSelected(SelectActivity activity, String selected) {
        activity.setChapter(selected);
    }

}
