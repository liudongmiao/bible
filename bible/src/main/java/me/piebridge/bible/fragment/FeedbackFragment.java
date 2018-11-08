package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.DrawerActivity;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/11/3.
 */
public class FeedbackFragment extends AbstractDialogFragment implements DialogInterface.OnClickListener {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DrawerActivity activity = ObjectUtils.requireNonNull((DrawerActivity) getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_feedback);
        builder.setMessage(R.string.feedback_message);
        builder.setPositiveButton(R.string.feedback_email, this);
        builder.setNeutralButton(R.string.feedback_report_bug, this);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        TextView textView = ObjectUtils.requireNonNull(dialog.findViewById(android.R.id.message));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onResume() {
        super.onResume();
        DrawerActivity activity = (DrawerActivity) getActivity();
        if (activity != null) {
            boolean hasEmailClient = activity.hasEmailClient();
            AlertDialog dialog = (AlertDialog) getDialog();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(hasEmailClient);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(hasEmailClient);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DrawerActivity activity = (DrawerActivity) getActivity();
        if (activity == null) {
            return;
        }
        if (DialogInterface.BUTTON_POSITIVE == which) {
            activity.sendEmail();
        } else if (DialogInterface.BUTTON_NEUTRAL == which) {
            activity.reportBug();
        }
    }
}
