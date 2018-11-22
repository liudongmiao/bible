package me.piebridge.bible.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.activity.SelectActivity;
import me.piebridge.bible.adapter.SelectAdapter;

/**
 * Created by thom on 16/7/6.
 */
public abstract class AbstractSelectFragment extends Fragment implements SelectAdapter.OnSelectedListener {

    private WeakReference<SelectActivity> wr;

    protected String selected;

    protected Map<String, String> items;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (wr == null) {
            SelectActivity activity = (SelectActivity) getActivity();
            if (activity != null) {
                wr = new WeakReference<>(activity);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        scroll();
        super.onViewCreated(view, savedInstanceState);
    }

    protected int setData(SelectAdapter adapter, Collection<String> keys) {
        List<SelectAdapter.SelectItem> selectItemList = new ArrayList<>();
        int position = -1;
        for (String key : keys) {
            String value = getGridName(key);
            boolean checked = isGridChecked(key);
            boolean enabled = isGridEnabled(key);
            if (checked) {
                position = selectItemList.size();
            }
            selectItemList.add(new SelectAdapter.SelectItem(key, value, checked, enabled));
        }
        adapter.setData(selectItemList);
        return position;
    }

    public void setItems(Map<String, String> items, String selected) {
        this.items = items;
        this.selected = selected;
        if (wr != null) {
            updateAdapter();
            scroll();
        }
    }

    @Override
    public void onSelected(String key) {
        if (items.containsKey(key)) {
            selected = key;
            updateChecked();
            SelectActivity selectActivity = wr.get();
            if (selectActivity != null) {
                onSelected(selectActivity, selected);
            }
        }
    }

    protected abstract void updateAdapter();

    protected abstract void scroll();

    protected abstract void updateChecked();

    protected abstract void onSelected(SelectActivity activity, String selected);

    protected String getGridName(String key) {
        if (items != null) {
            return items.get(key);
        } else {
            return key;
        }
    }

    protected boolean isGridEnabled(String key) {
        return true;
    }

    protected boolean isGridChecked(String key) {
        return key.equals(selected);
    }

    protected void scroll(RecyclerView recyclerView, int position) {
        if (recyclerView != null && position >= 0) {
            recyclerView.scrollToPosition(position);
        }
    }

}
