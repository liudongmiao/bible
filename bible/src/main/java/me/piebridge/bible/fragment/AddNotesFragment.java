package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.utils.NoteBundle;

/**
 * Created by thom on 2018/9/28.
 */
public class AddNotesFragment extends AbstractDialogFragment
        implements DialogInterface.OnClickListener {

    public AddNotesFragment() {
        setArguments(new Bundle());
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getVerses());
        builder.setView(R.layout.fragment_edittext);
        builder.setPositiveButton(android.R.string.ok, this);
        builder.setNegativeButton(android.R.string.cancel, this);
        if (getNoteId() > 0) {
            builder.setNeutralButton(R.string.note_delete, this);
        }
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        EditText addnote = getDialog().findViewById(R.id.addnote);
        addnote.setText(getContent());
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        AbstractReadingActivity activity = (AbstractReadingActivity) getActivity();
        if (activity == null) {
            return;
        }
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                EditText addnote = getDialog().findViewById(R.id.addnote);
                activity.saveNotes(getNoteId(), getVerses(), addnote.getText().toString());
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                activity.deleteNote(getNoteId(), getVerses());
                break;
            default:
                break;
        }
    }

    public void setNote(String verses, Bundle note) {
        Bundle arguments = getArguments();
        arguments.putString(NoteBundle.VERSES, verses);
        if (note != null) {
            arguments.putAll(note);
        }
    }

    private String getVerses() {
        return getArguments().getString(NoteBundle.VERSES);
    }

    private long getNoteId() {
        return getArguments().getLong(NoteBundle.ID);
    }

    private String getContent() {
        return getArguments().getString(NoteBundle.CONTENT);
    }

}
