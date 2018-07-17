package me.piebridge.bible.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.ColorUtils;

/**
 * Created by thom on 16/7/7.
 */
public class GridAdapter extends ArrayAdapter<String> {

    private Typeface typeface;

    private GridChecker gridChecker;

    private ColorStateList textColor;
    private int textColorSelected;

    public GridAdapter(Context context, GridChecker gridChecker) {
        super(context, R.layout.view_cell, R.id.text);
        this.gridChecker = gridChecker;
        this.textColorSelected = ColorUtils.resolveColor(context, R.attr.colorAccent);
    }

    public GridAdapter(Context context, GridChecker gridChecker, Typeface typeface) {
        this(context, gridChecker);
        this.typeface = typeface;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView textView = view.findViewById(R.id.text);
        String item = getItem(position);
        String human = gridChecker.getGridName(item);
        if (textColor == null) {
            textColor = textView.getTextColors();
        }
        if (TextUtils.isEmpty(human)) {
            view.setVisibility(View.INVISIBLE);
        } else {
            textView.setText(human);
            view.setVisibility(View.VISIBLE);
        }
        textView.setEnabled(gridChecker.isGridEnabled(item));
        if (!gridChecker.isGridChecked(item)) {
            textView.setTextColor(textColor);
        } else {
            textView.setTextColor(textColorSelected);
        }
        if (typeface != null) {
            textView.setTypeface(typeface);
        }
        return view;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        String item = getItem(position);
        return gridChecker.isGridEnabled(item);
    }

    public void setData(List<String> objects) {
        setNotifyOnChange(false);
        clear();
        addAll(objects);
        notifyDataSetChanged();
    }

    public interface GridChecker {

        String getGridName(String key);

        boolean isGridEnabled(String key);

        boolean isGridChecked(String key);
    }
}
