package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.AnnotationComponent;
import me.piebridge.bible.Bible;
import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.utils.NoteBundle;

/**
 * Created by thom on 2018/9/30.
 */
public class ShowNotesFragment extends AbstractDialogFragment
        implements DialogInterface.OnClickListener {

    public ShowNotesFragment() {
        setArguments(new Bundle());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Bundle bundle = getArguments();
        builder.setTitle(bundle.getString(NoteBundle.VERSES));
        builder.setMessage(bundle.getString(NoteBundle.CONTENT));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(R.string.editnote, this);
        builder.setNeutralButton(R.string.deletenote, this);
        return builder.create();
    }

    public void setNote(@NonNull Bundle note) {
        Bundle arguments = getArguments();
        arguments.putAll(note);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        AbstractReadingActivity activity = (AbstractReadingActivity) getActivity();
        if (activity == null) {
            return;
        }
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                activity.doAddNotes(getVerses());
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                activity.deleteNote(getNoteId(), getVerses());
                break;
            default:
                break;
        }
    }

    private String getVerses() {
        return getArguments().getString(NoteBundle.VERSES);
    }

    private long getNoteId() {
        return getArguments().getLong(NoteBundle.ID);
    }

}
