package me.piebridge.bible.fragment;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SelectActivity;
import me.piebridge.bible.adapter.GridAdapter;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/6.
 */
public abstract class AbstractSelectFragment extends Fragment implements AdapterView.OnItemClickListener, GridAdapter.GridChecker {

    private static final int COLUMN_5 = 5;

    private GridView gridView;
    private Typeface typeface;
    private SelectActivity mActivity;

    private String selected;
    private Map<String, String> items;

    @Override
    @SuppressWarnings("deprecation")
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        String font = BibleUtils.getFontPath(activity);
        if (!TextUtils.isEmpty(font)) {
            typeface = Typeface.createFromFile(font);
        }
        mActivity = (SelectActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select, container, false);
        gridView = (GridView) view.findViewById(R.id.gridView);
        gridView.setNumColumns(getNumColumns());
        gridView.setAdapter(new GridAdapter(mActivity, this, typeface));
        gridView.setOnItemClickListener(this);
        updateAdapter();
        return view;
    }

    protected int getNumColumns() {
        return COLUMN_5;
    }

    protected List<String> convertItems(Collection<String> items) {
        return new ArrayList<String>(items);
    }

    private void updateAdapter() {
        if (gridView != null && items != null) {
            GridAdapter gridAdapter = (GridAdapter) gridView.getAdapter();
            gridAdapter.setData(convertItems(items.keySet()));
            gridView.setSelection(gridAdapter.getPosition(selected));
        }
    }

    public void setItems(Map<String, String> items, String selected) {
        this.items = items;
        this.selected = selected;
        updateAdapter();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        GridAdapter adapter = (GridAdapter) parent.getAdapter();
        selected = String.valueOf(adapter.getItem(position));
        onSelected(mActivity, selected);
        adapter.notifyDataSetChanged();
    }

    protected abstract void onSelected(SelectActivity activity, String selected);

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

}
