package me.piebridge.bible.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SelectActivity;
import me.piebridge.bible.adapter.SelectAdapter;
import me.piebridge.bible.utils.BibleUtils;

/**
 * Created by thom on 16/7/6.
 */
public class SelectBookFragment extends AbstractSelectFragment {

    private SelectAdapter selectAdapterLeft;
    private SelectAdapter selectAdapterRight;

    private RecyclerView recyclerViewLeft;
    private RecyclerView recyclerViewRight;

    private int positionLeft = -1;
    private int positionRight = -1;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (selectAdapterLeft == null) {
            String font = BibleUtils.getFontPath(context);
            Typeface typeface = !TextUtils.isEmpty(font) ? Typeface.createFromFile(font) : null;
            selectAdapterLeft = new SelectAdapter(this, typeface);
            selectAdapterRight = new SelectAdapter(this, typeface);
        }
        if (items != null) {
            updateAdapter();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select_book, container, false);
        recyclerViewLeft = view.findViewById(R.id.left);
        recyclerViewLeft.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewLeft.setAdapter(selectAdapterLeft);

        recyclerViewRight = view.findViewById(R.id.right);
        recyclerViewRight.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewRight.setAdapter(selectAdapterRight);

        return view;
    }

    @Override
    protected void updateAdapter() {
        List<String> keys = new ArrayList<>(items.keySet());
        int matt = keys.indexOf("Matt");
        int gen = keys.indexOf("Gen");
        List<String> oldData;
        List<String> newData;
        if (matt > -1) {
            oldData = keys.subList(0, matt);
            newData = keys.subList(matt, keys.size());
        } else if (gen > -1) {
            oldData = keys;
            newData = Collections.emptyList();
        } else {
            oldData = Collections.emptyList();
            newData = keys;
        }
        positionLeft = setData(selectAdapterLeft, oldData);
        positionRight = setData(selectAdapterRight, newData);
    }

    @Override
    protected void updateChecked() {
        selectAdapterLeft.updateChecked(selected);
        selectAdapterRight.updateChecked(selected);
    }

    @Override
    protected void onSelected(SelectActivity activity, String selected) {
        activity.setBook(selected);
    }

    @Override
    protected void scroll() {
        scroll(recyclerViewLeft, positionLeft);
        scroll(recyclerViewRight, positionRight);
    }

    @Override
    public String toString() {
        return "SelectBook";
    }

}
