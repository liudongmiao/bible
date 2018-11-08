package me.piebridge.payment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * payment progress
 * <p>
 * Created by thom on 2017/2/13.
 */
public class ProgressFragment extends AbstractDialogFragment {

    public ProgressFragment() {
        super();
        setCancelable(false);
        setStyle(STYLE_NO_TITLE, 0);
    }

    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_payment_progress, container);
    }

}