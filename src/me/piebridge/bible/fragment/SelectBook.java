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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.piebridge.bible.R;
import me.piebridge.bible.SelectActivity;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/6.
 */
public class SelectBook extends Fragment implements AdapterView.OnItemClickListener, GridAdapter.GridChecker {

    private GridView left;
    private GridView right;

    private String selected;
    private Map<String, String> books;

    private SelectActivity mActivity;
    private Typeface typeface;

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
        View view = inflater.inflate(R.layout.select_book, container, false);
        left = (GridView) view.findViewById(R.id.left);
        left.setOnItemClickListener(this);
        left.setAdapter(new GridAdapter(mActivity, this, typeface));

        right = (GridView) view.findViewById(R.id.right);
        right.setOnItemClickListener(this);
        right.setAdapter(new GridAdapter(mActivity, this, typeface));
        updateIfNeeded();
        return view;
    }

    public void setBooks(Map<String, String> books, String selected) {
        this.books = books;
        this.selected = selected;
        updateIfNeeded();
    }

    private void updateIfNeeded() {
        if (left != null && right != null && books != null) {
            List<String> keys = new ArrayList<String>(books.keySet());
            int matt = keys.indexOf("Matt");
            int gen = keys.indexOf("Gen");
            GridAdapter leftAdapter = (GridAdapter) left.getAdapter();
            GridAdapter rightAdapter = (GridAdapter) right.getAdapter();
            int position = keys.indexOf(selected);
            if (gen > -1 && matt > -1) {
                List<String> leftData = keys.subList(0, matt);
                List<String> rightData = keys.subList(matt, keys.size());
                leftAdapter.setData(leftData);
                rightAdapter.setData(rightData);
                if (position < matt) {
                    left.smoothScrollToPosition(position);
                } else {
                    right.smoothScrollToPosition(position - matt);
                }
            } else if (gen > -1) {
                leftAdapter.setData(keys);
                rightAdapter.setData(Collections.<String>emptyList());
                left.smoothScrollToPosition(position);
            } else if (matt > -1) {
                leftAdapter.setData(Collections.<String>emptyList());
                rightAdapter.setData(keys);
                right.smoothScrollToPosition(position);
            }
        }
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        selected = String.valueOf(parent.getAdapter().getItem(position));
        if (mActivity != null) {
            mActivity.setBook(selected);
        }
        ((GridAdapter) left.getAdapter()).notifyDataSetChanged();
        ((GridAdapter) right.getAdapter()).notifyDataSetChanged();

    }

    @Override
    public String getGridName(String key) {
        if (books != null) {
            return books.get(key);
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
