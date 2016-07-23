package me.piebridge.bible.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Created by thom on 16/7/23.
 */
public class ItemsAdapter extends ArrayAdapter<CharSequence> {

    private final Spinner mSpinner;

    public ItemsAdapter(Spinner spinner, Context context, int resource, CharSequence[] data) {
        super(context, resource, data);
        mSpinner = spinner;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(mSpinner.getSelectedItemPosition(), convertView, parent);
        if (!mSpinner.isEnabled()) {
            fixColor((TextView) view);
        }
        return view;
    }

    private void fixColor(TextView view) {
        // set color to color, not color state list
        view.setTextColor(view.getCurrentTextColor());
    }

}
