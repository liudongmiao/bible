package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.AboutActivity;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/11/08.
 */
public class DonateFragment extends AbstractDialogFragment implements DialogInterface.OnClickListener {

    private int mWhich;

    public DonateFragment() {
        setArguments(new Bundle());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AboutActivity activity = ObjectUtils.requireNonNull((AboutActivity) getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setSingleChoiceItems(R.array.donate_list, -1, this);
        builder.setTitle(R.string.about_donate_title);
        builder.setPositiveButton(android.R.string.ok, this);
        builder.setNegativeButton(android.R.string.cancel, this);
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which >= 0) {
            mWhich = which;
            return;
        }
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                AboutActivity activity = (AboutActivity) getActivity();
                if (activity != null) {
                    activity.donate(mWhich);
                }
            default:
                break;
        }
    }

}
