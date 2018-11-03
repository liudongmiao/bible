package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/11/03.
 */
public class CopyrightFragment extends AbstractDialogFragment {

    private static final String TITLE = "title";

    private static final String MESSAGE = "message";

    public CopyrightFragment() {
        setArguments(new Bundle());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ObjectUtils.requireNonNull(getActivity()));
        Bundle arguments = ObjectUtils.requireNonNull(getArguments());
        builder.setTitle(arguments.getCharSequence(TITLE));
        builder.setMessage(arguments.getCharSequence(MESSAGE));
        builder.setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        TextView textView = dialog.findViewById(android.R.id.message);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void setMessage(CharSequence title, CharSequence message) {
        Bundle arguments = ObjectUtils.requireNonNull(getArguments());
        arguments.putCharSequence(TITLE, title);
        arguments.putCharSequence(MESSAGE, message);
    }

}
