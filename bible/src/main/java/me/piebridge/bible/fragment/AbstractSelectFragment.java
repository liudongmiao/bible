package me.piebridge.bible.fragment;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SelectActivity;
import me.piebridge.bible.adapter.GridAdapter;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/6.
 */
public abstract class AbstractSelectFragment extends Fragment
        implements AdapterView.OnItemClickListener, GridAdapter.GridChecker {

    private boolean mResumed;

    private boolean mVisible;

    private static final int COLUMN_5 = 5;

    private GridView gridView;

    protected Typeface typeface;
    protected WeakReference<SelectActivity> wr;

    protected String selected;
    protected Map<String, String> items;

    @Override
    @SuppressWarnings("deprecation")
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        String font = BibleUtils.getFontPath(activity);
        if (!TextUtils.isEmpty(font)) {
            typeface = Typeface.createFromFile(font);
        }
        wr = new WeakReference<>((SelectActivity) activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select, container, false);
        gridView = view.findViewById(R.id.gridView);
        gridView.setNumColumns(COLUMN_5);
        SelectActivity selectActivity = wr.get();
        if (selectActivity != null) {
            gridView.setAdapter(new GridAdapter(selectActivity, this, typeface));
        }
        gridView.setOnItemClickListener(this);
        updateAdapter();
        return view;
    }

    protected void updateAdapter() {
        if (gridView != null && items != null) {
            GridAdapter gridAdapter = (GridAdapter) gridView.getAdapter();
            gridAdapter.setData(new ArrayList<>(items.keySet()));
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
        SelectActivity selectActivity = wr.get();
        if (selectActivity != null) {
            GridAdapter adapter = (GridAdapter) parent.getAdapter();
            selected = String.valueOf(adapter.getItem(position));
            onSelected(selectActivity, selected);
            notifyDataSetChanged();
        }
    }

    protected void notifyDataSetChanged() {
        ((GridAdapter) gridView.getAdapter()).notifyDataSetChanged();
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

    @Override
    public void onResume() {
        super.onResume();
        mResumed = true;
        scroll();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        mVisible = true;
        scroll();
    }

    private void scroll() {
        if (mResumed && mVisible && gridView != null && selected != null) {
            GridAdapter gridAdapter = (GridAdapter) gridView.getAdapter();
            int position = gridAdapter.getPosition(selected);
            int last = gridView.getLastVisiblePosition();
            if (last < position) {
                gridView.smoothScrollToPosition(position);
            }
        }
    }

}
