package me.piebridge.bible.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.piebridge.bible.R;

/**
 * Created by thom on 2018/11/6.
 */
public class NoResultAdapter extends RecyclerView.Adapter {

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item, parent, false);
        return new CountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CountViewHolder countViewHolder = (CountViewHolder) holder;
        countViewHolder.typeView.setText(R.string.search_result_none);
        countViewHolder.countView.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

}