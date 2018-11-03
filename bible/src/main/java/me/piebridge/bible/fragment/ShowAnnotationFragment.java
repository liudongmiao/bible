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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.SearchActivity;
import me.piebridge.bible.utils.DeprecationUtils;
import me.piebridge.bible.utils.FileUtils;
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
            String cross = annotation;
            if (cross.contains("passage/?search=")) {
                cross = cross.replaceAll("^.*?/passage/\\?search=([^&]*?)&.*?$", "$1").replaceAll(",", ";");
                if (cross.contains("%")) {
                    cross = decode(cross);
                }
            }
            if (cross.contains("class=\"xt\"")) {
                cross = cross.replaceAll("^.*?<span class=\"xt\">(.*?)</span>.*?$", "$1");
                cross = cross.replaceAll("see ", ";");
                cross = cross.replaceAll("See ", ";");
            }
            LogUtils.d("cross: " + cross);
            return cross;
        } else {
            return null;
        }
    }

    private static String decode(String cross) {
        try {
            return URLDecoder.decode(cross, FileUtils.UTF_8);
        } catch (UnsupportedEncodingException ignore) {
            return cross;
        }
    }

    private static int formatTitleRes(String link) {
        if (link.contains("!f.") || link.startsWith("f")) {
            return R.string.reading_footnote;
        } else if (link.contains("!x.") || link.startsWith("c")) {
            return R.string.reading_cross;
        } else {
            return 0;
        }
    }

    private static String formatMessage(String annotation) {
        String message = annotation;
        // for cross
        if (message.contains("class=\"note x\"")) {
            message = message.replaceAll("^<span[^>]*class=\"note x\">(.*?)</span>$", "$1");
        }
        if (message.contains("class=\"xo\"")) {
            message = message.replaceAll("<span class=\"xo\">(.*?)</span>", "<a href=\"\">$1</a> : ");
        }
        if (message.contains("class=\"xt\"")) {
            message = message.replaceAll("<span class=\"xt\">(.*?)</span>", "<a href=\"\">$1</a>");
        }
        // for note
        if (message.contains("class=\"note f\"")) {
            message = message.replaceAll("^<span[^>]*class=\"note f\">(.*?)</span>$", "$1");
        }
        if (message.contains("class=\"fr\"")) {
            message = message.replaceAll("<span class=\"fr\">(.*?)</span>", "<a href=\"\">$1</a>");
        }
        LogUtils.d("message: " + message);
        return message;
    }

    @Override
    public void onClick(View v) {
        FragmentActivity activity = getActivity();
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(SearchActivity.CROSS, true);
        intent.putExtra(SearchManager.QUERY, getArguments().getString(CROSS));
        activity.startActivity(intent);
    }

}
