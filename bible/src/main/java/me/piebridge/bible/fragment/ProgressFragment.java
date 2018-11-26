package me.piebridge.bible.fragment;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import me.piebridge.bible.R;

/**
 * Created by thom on 2018/11/25.
 */
public class ProgressFragment extends AbstractDialogFragment {

    public ProgressFragment() {
        super();
        setCancelable(false);
        setStyle(STYLE_NO_TITLE, 0);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_progress, container);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        FragmentActivity activity = getActivity();
        if (activity instanceof DialogInterface.OnCancelListener) {
            ((DialogInterface.OnCancelListener) activity).onCancel(dialog);
        }
    }

}
