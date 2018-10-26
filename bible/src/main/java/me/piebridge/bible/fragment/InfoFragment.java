package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

/**
 * Created by thom on 2018/10/25.
 */
public class InfoFragment extends AbstractDialogFragment {

    private static final String TITLE = "title";

    private static final String MESSAGE = "message";

    public InfoFragment() {
        setArguments(new Bundle());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Bundle bundle = getArguments();
        builder.setTitle(bundle.getString(TITLE));
        builder.setMessage(bundle.getString(MESSAGE));
        builder.setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    public void setMessage(String title, String message, String extra) {
        Bundle arguments = getArguments();
        arguments.putString(TITLE, title);
        arguments.putString(MESSAGE, message);
    }

}
