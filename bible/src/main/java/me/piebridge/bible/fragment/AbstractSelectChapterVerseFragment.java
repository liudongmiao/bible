package me.piebridge.bible.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.piebridge.bible.R;
import me.piebridge.bible.adapter.SelectAdapter;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/6.
 */
public abstract class AbstractSelectChapterVerseFragment extends AbstractSelectFragment {

    private SelectAdapter selectAdapter;
    private RecyclerView recyclerView;
    private int position = -1;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (selectAdapter == null) {
            String font = BibleUtils.getFontPath(context);
            Typeface typeface = !TextUtils.isEmpty(font) ? Typeface.createFromFile(font) : null;
            selectAdapter = new SelectAdapter(this, typeface);
        }
        if (items != null) {
            updateAdapter();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select, container, false);
        recyclerView = view.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 0x5));
        recyclerView.setAdapter(selectAdapter);
        return view;
    }

    protected void updateAdapter() {
        position = setData(selectAdapter, items.keySet());
    }

    protected void updateChecked() {
        selectAdapter.updateChecked(selected);
    }

    @Override
    protected void scroll() {
        scroll(recyclerView, position);
    }

}
