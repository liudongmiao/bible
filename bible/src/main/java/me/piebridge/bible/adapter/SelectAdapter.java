package me.piebridge.bible.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.ColorUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/11/22.
 */
public class SelectAdapter extends RecyclerView.Adapter implements View.OnClickListener {

    private static final int VIEW_OPTION = 0;

    private static final int VIEW_CHECKED = 1;

    private static final int VIEW_DISABLED = 2;

    private final Typeface mTypeface;

    private final List<SelectItem> mOptions;

    private final OnSelectedListener mListener;

    public SelectAdapter(OnSelectedListener listener, Typeface typeface) {
        this.mListener = listener;
        this.mTypeface = typeface;
        this.mOptions = new ArrayList<>();
    }

    public void setData(List<SelectItem> options) {
        mOptions.clear();
        mOptions.addAll(options);
        notifyDataSetChanged();
        LogUtils.d("set data for " + mListener);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.view_cell, parent, false);
        TextView textView = view.findViewById(R.id.text);
        if (mTypeface != null) {
            textView.setTypeface(mTypeface);
        }
        if (viewType == VIEW_DISABLED) {
            textView.setEnabled(false);
        } else {
            textView.setOnClickListener(this);
            if (viewType == VIEW_CHECKED) {
                textView.setTextColor(ColorUtils.resolve(parent.getContext(), R.attr.colorAccent));
                textView.setBackgroundResource(R.color.yellow_alpha);
            }
        }
        return new SelectViewHolder(view, textView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TextView textView = ((SelectViewHolder) holder).textView;
        SelectItem selectItem = mOptions.get(position);
        textView.setTag(selectItem.key);
        textView.setText(selectItem.value);
    }

    @Override
    public int getItemCount() {
        return mOptions.size();
    }

    @Override
    public int getItemViewType(int position) {
        SelectItem selectItem = mOptions.get(position);
        if (selectItem.checked) {
            return VIEW_CHECKED;
        } else if (selectItem.enabled) {
            return VIEW_OPTION;
        } else {
            return VIEW_DISABLED;
        }
    }

    public void updateChecked(String selected) {
        boolean changed = false;
        for (SelectItem item : mOptions) {
            boolean checked = ObjectUtils.equals(item.key, selected);
            if (checked != item.checked) {
                item.checked = checked;
                item.changed = true;
                changed = true;
            }
        }
        if (changed) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffCallback(mOptions, mOptions));
            result.dispatchUpdatesTo(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onSelected((String) v.getTag());
        }
    }

    public interface OnSelectedListener {

        void onSelected(String key);

    }

    public static class SelectItem {
        final String key;
        final String value;
        boolean checked;
        final boolean enabled;
        boolean changed;

        public SelectItem(String key, String value) {
            this(key, value, false, true);
        }

        public SelectItem(String key, String value, boolean checked, boolean enabled) {
            this.key = key;
            this.value = value;
            this.checked = checked;
            this.enabled = enabled;
        }

        private boolean equals(SelectItem other) {
            return ObjectUtils.equals(key, other.key);
        }

        public boolean isSame(SelectItem other) {
            return ObjectUtils.equals(value, other.value)
                    && ObjectUtils.equals(checked, other.checked)
                    && ObjectUtils.equals(enabled, other.enabled);
        }

    }

    private static class SelectViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        public SelectViewHolder(View view, TextView textView) {
            super(view);
            this.textView = textView;
        }

    }

    private static class DiffCallback extends DiffUtil.Callback {

        private final List<SelectItem> mOldList;

        private final List<SelectItem> mNewList;

        DiffCallback(List<SelectItem> oldList, List<SelectItem> newList) {
            mOldList = oldList;
            mNewList = newList;
        }

        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            SelectItem oldItem = mOldList.get(oldItemPosition);
            SelectItem newItem = mNewList.get(newItemPosition);
            return oldItem == newItem || oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            SelectItem oldItem = mOldList.get(oldItemPosition);
            SelectItem newItem = mNewList.get(newItemPosition);
            try {
                return oldItem == newItem ? !oldItem.changed : oldItem.equals(newItem) && oldItem.isSame(newItem);
            } finally {
                oldItem.changed = false;
                newItem.changed = false;
            }
        }

    }

}
