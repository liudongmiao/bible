package me.piebridge.bible.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IdRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * Created by thom on 2018/9/28.
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

    protected void clearFocus(@IdRes int... ids) {
        try {
            hideSoft(getActivity(), ids);
        } catch (RuntimeException ignore) {
            // do nothing
        }
    }

    private void hideSoft(Context context, @IdRes int... ids) {
        final InputMethodManager imm;
        imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (int id : ids) {
                View view = getDialog().findViewById(id);
                if (view != null && view.isFocused()) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        }
    }

}