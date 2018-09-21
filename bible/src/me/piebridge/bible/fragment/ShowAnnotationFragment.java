package me.piebridge.bible.fragment;

import android.app.Dialog;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import me.piebridge.bible.Passage;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.DeprecationUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/9/30.
 */
public class ShowAnnotationFragment extends AbstractDialogFragment implements View.OnClickListener {

    private static final String LINK = "link";

    private static final String TITLE = "title";

    private static final String CROSS = "cross";

    private static final String MESSAGE = "message";

    public ShowAnnotationFragment() {
        setArguments(new Bundle());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Bundle bundle = getArguments();
        int titleRes = bundle.getInt(TITLE);
        if (titleRes != 0) {
            builder.setTitle(titleRes);
        } else {
            builder.setTitle(bundle.getString(LINK));
        }
        builder.setMessage(DeprecationUtils.fromHtml(bundle.getString(MESSAGE)));
        builder.setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        String cross = getArguments().getString(CROSS);
        if (!TextUtils.isEmpty(cross)) {
            TextView messageView = getDialog().findViewById(android.R.id.message);
            messageView.setOnClickListener(this);
        }
    }

    public void setAnnotation(String link, String annotation) {
        Bundle arguments = getArguments();
        arguments.putString(LINK, link);
        arguments.putInt(TITLE, formatTitleRes(link));
        arguments.putString(MESSAGE, formatMessage(annotation));
        arguments.putString(CROSS, formatCross(link, annotation));
    }

    private String formatCross(String link, String annotation) {
        if (link.contains("!x.") || link.startsWith("c")) {
            String cross = annotation.replaceAll("^.*?/passage/\\?search=([^&]*?)&.*?$", "$1")
                    .replaceAll(",", ";");
            LogUtils.d("cross: " + cross);
            return cross;
        } else {
            return null;
        }
    }

    private static int formatTitleRes(String link) {
        if (link.contains("!f.") || link.startsWith("f")) {
            return R.string.flink;
        } else if (link.contains("!x.") || link.startsWith("c")) {
            return R.string.xlink;
        } else {
            return 0;
        }
    }

    private static String formatMessage(String annotation) {
        return annotation.replaceAll("<span class=\"fr\">(.*?)</span>", "<strong>$1&nbsp;</strong>")
                .replaceAll("<span class=\"xo\">(.*?)</span>", "");
    }

    @Override
    public void onClick(View v) {
        FragmentActivity activity = getActivity();
        Intent intent = new Intent(activity, Passage.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(Passage.CROSS, true);
        intent.putExtra(SearchManager.QUERY, getArguments().getString(CROSS));
        activity.startActivity(intent);
    }

}
