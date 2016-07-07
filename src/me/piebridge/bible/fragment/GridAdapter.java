package me.piebridge.bible.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ToggleButton;

import java.util.List;

import me.piebridge.bible.R;

/**
 * Created by thom on 16/7/7.
 */
public class GridAdapter extends ArrayAdapter<String> {

    private Typeface typeface;

    private String selected;

    private GridChecker gridChecker;

    public GridAdapter(Context context, GridChecker gridChecker) {
        super(context, R.layout.grid);
        this.gridChecker = gridChecker;
    }

    public GridAdapter(Context context, GridChecker gridChecker, Typeface typeface) {
        this(context, gridChecker);
        this.typeface = typeface;
    }

    public void setSelected(String selected) {
        this.selected = selected;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ToggleButton view = (ToggleButton) super.getView(position, convertView, parent);
        String item = this.getItem(position);
        String human = gridChecker.getGridName(item);
        view.setTextOn(human);
        view.setTextOff(human);
        view.setChecked(gridChecker.isGridChecked(item));
        view.setEnabled(gridChecker.isGridEnabled(item));
        view.setTransformationMethod(null);
        if (typeface != null) {
            view.setTypeface(typeface);
        }
        return view;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        String item = this.getItem(position);
        return gridChecker.isGridEnabled(item);
    }

    public void setData(List<String> objects) {
        setNotifyOnChange(false);
        clear();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            addAll(objects);
        } else {
            for (String object : objects) {
                add(object);
            }
        }
        notifyDataSetChanged();
    }

    interface GridChecker {

        String getGridName(String key);

        boolean isGridEnabled(String key);

        boolean isGridChecked(String key);
    }
}
