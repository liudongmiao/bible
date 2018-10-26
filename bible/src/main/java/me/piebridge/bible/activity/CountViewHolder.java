package me.piebridge.bible.activity;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.piebridge.bible.R;

/**
 * Created by thom on 2018/10/25.
 */
public class CountViewHolder extends RecyclerView.ViewHolder {

    final TextView typeView;

    final TextView countView;

    public CountViewHolder(View view) {
        super(view);
        this.typeView = view.findViewById(R.id.type);
        this.countView = view.findViewById(R.id.count);
    }

}
