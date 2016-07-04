package me.piebridge.bible;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Created by thom on 16/6/21.
 */
public class ReadingHandler extends Handler {

    public static final int SHOW_ANNOTATION = 0;

    private final WeakReference<Context> wr;

    public ReadingHandler(Context context) {
        wr = new WeakReference<Context>(context);
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case SHOW_ANNOTATION:
                String[] linkAnnotation = (String[]) message.obj;
                showAnnotation(linkAnnotation[0x0], linkAnnotation[0x1], linkAnnotation[0x2]);
                break;
            default:
                break;
        }
    }

    private void showAnnotation(String link, String message, String osis) {
        final Context context = wr.get();
        if (context == null) {
            return;
        }
        String annotation = message;
        if (TextUtils.isEmpty(annotation)) {
            Bible bible = Bible.getInstance(context);
            bible.loadAnnotations(osis, true);
            annotation = bible.getAnnotation(link);
        }
        if (annotation != null) {
            setShowAnnotation(context, link, annotation);
        }
    }

    private void setShowAnnotation(final Context context, String link, String annotation) {
        String title = link;
        boolean isCross = false;
        String message = annotation;
        final String cross = message.replaceAll("^.*?/passage/\\?search=([^&]*?)&.*?$", "$1").replaceAll(",", ";");
        if (link.contains("!f.") || link.startsWith("f")) {
            title = context.getString(R.string.flink);
        } else if (link.contains("!x.") || link.startsWith("c")) {
            isCross = true;
            title = context.getString(R.string.xlink);
        }
        message = message.replaceAll("<span class=\"fr\">(.*?)</span>", "<strong>$1&nbsp;</strong>");
        message = message.replaceAll("<span class=\"xo\">(.*?)</span>", "");
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(Html.fromHtml(message))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        if (isCross && !TextUtils.isEmpty(cross)) {
            showReference(context, dialog, cross);
        }
    }

    private void showReference(final Context context, final AlertDialog dialog, final String cross) {
        final TextView messageView = (TextView) dialog.findViewById(android.R.id.message);
        String reference;
        if (cross.contains("<")) {
            reference = messageView.getText().toString();
        } else {
            reference = cross;
        }
        final String search = reference;
        messageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                showReference(context, search);
            }
        });
    }

    private void showReference(Context context, String search) {
        Intent intent = new Intent(context, Passage.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(Passage.CROSS, true);
        intent.putExtra(SearchManager.QUERY, search);
        context.startActivity(intent);
    }

}
