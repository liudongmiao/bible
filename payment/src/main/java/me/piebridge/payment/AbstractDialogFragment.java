package me.piebridge.payment;

import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * Created by thom on 2017/8/3.
 */
public abstract class AbstractDialogFragment extends DialogFragment {

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // https://stackoverflow.com/a/27084544
        if (super.getDialog() == null) {
            super.setShowsDialog(false);
        }
        try {
            super.onActivityCreated(savedInstanceState);
        } catch (NullPointerException e) { // NOSONAR
            // do nothing
        }
    }

    @Override
    public void show(FragmentManager manager, String tag) {
        Fragment fragment = manager.findFragmentByTag(tag);
        if (fragment == null || !fragment.isAdded()) {
            super.show(manager, tag);
        }
    }

}