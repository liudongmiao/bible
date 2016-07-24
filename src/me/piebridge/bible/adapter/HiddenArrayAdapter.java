package me.piebridge.bible.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * Created by thom on 16/7/23.
 */
public class HiddenArrayAdapter extends ArrayAdapter<CharSequence> {

    public HiddenArrayAdapter(Context context, int resource, CharSequence[] data) {
        super(context, resource, data);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        ((TextView) view).setText("");
        return view;
    }

}
