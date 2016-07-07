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
public class ReadingHandler extends Handler implements View.OnClickListener {

    public static final int SHOW_ANNOTATION = 0;
    public static final int SHOW_NOTE = 1;

    private final WeakReference<Context> wr;

    private AlertDialog dialog;

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
            case SHOW_NOTE:
                String[] noteVerse = (String[]) message.obj;
                showNote(noteVerse[0x00], noteVerse[0x1]);
            default:
                break;
        }
    }

    private void showNote(String verse, String osis) {
        final Context context = wr.get();
        if (context == null) {
            return;
        }
        Bible bible = Bible.getInstance(context);
        Bible.Note note = bible.getNote(osis, verse);
        dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.note)
                .setMessage(note.content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
        dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(Html.fromHtml(message))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        if (isCross && !TextUtils.isEmpty(cross)) {
            TextView messageView = (TextView) dialog.findViewById(android.R.id.message);
            messageView.setTag(cross.contains("<") ? messageView.getText().toString() : cross);
            messageView.setOnClickListener(this);
        }
    }

    private void showReference(Context context, String search) {
        Intent intent = new Intent(context, Passage.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(Passage.CROSS, true);
        intent.putExtra(SearchManager.QUERY, search);
        context.startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        Context context = wr.get();
        if (context != null) {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
            showReference(context, String.valueOf(v.getTag()));
        }
    }

}
