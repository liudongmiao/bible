package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

/**
 * Created by thom on 2018/10/25.
 */
public abstract class AbstractConfirmFragment extends AbstractDialogFragment implements DialogInterface.OnClickListener {

    private static final String TITLE = "title";

    private static final String MESSAGE = "message";

    private static final String EXTRA = "extra";

    public AbstractConfirmFragment() {
        setArguments(new Bundle());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Bundle bundle = getArguments();
        builder.setTitle(bundle.getString(TITLE));
        builder.setMessage(bundle.getString(MESSAGE));
        builder.setPositiveButton(android.R.string.ok, this);
        builder.setNegativeButton(android.R.string.cancel, this);
        return builder.create();
    }

    public abstract void onClick(DialogInterface dialog, int which);

    public void setMessage(String title, String message, String extra) {
        Bundle arguments = getArguments();
        arguments.putString(TITLE, title);
        arguments.putString(MESSAGE, message);
        arguments.putString(EXTRA, extra);
    }

    protected String getExtra() {
        return getArguments().getString(EXTRA);
    }

}
