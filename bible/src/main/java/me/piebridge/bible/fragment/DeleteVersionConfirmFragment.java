package me.piebridge.bible.fragment;

import android.content.DialogInterface;

import me.piebridge.bible.activity.VersionsActivity;

/**
 * Created by thom on 2018/10/25.
 */
public class DeleteVersionConfirmFragment extends AbstractConfirmFragment {

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                VersionsActivity activity = (VersionsActivity) getActivity();
                activity.confirmDelete(getExtra());
                break;
            default:
                break;
        }

    }

}
