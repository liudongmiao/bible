package me.piebridge.bible.fragment;

import android.content.DialogInterface;

import me.piebridge.bible.activity.ReadingActivity;

/**
 * Created by thom on 2018/10/25.
 */
public class SwitchVersionConfirmFragment extends AbstractConfirmFragment {

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                ReadingActivity activity = (ReadingActivity) getActivity();
                if (activity != null) {
                    activity.confirmSwitchToVersion(getExtra());
                }
                break;
            default:
                break;
        }

    }

}
