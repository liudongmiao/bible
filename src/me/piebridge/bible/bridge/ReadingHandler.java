package me.piebridge.bible.bridge;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import me.piebridge.bible.Bible;
import me.piebridge.bible.Passage;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.DeprecationUtils;

/**
 * Created by thom on 16/6/21.
 */
public class ReadingHandler extends Handler implements View.OnClickListener {

    public static final int SHOW_ANNOTATION = 0;
    public static final int SHOW_NOTE = 1;

    private final WeakReference<Context> wr;

    private AlertDialog dialog;

    public ReadingHandler(Context context) {
        wr = new WeakReference<>(context);
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case SHOW_ANNOTATION:
                showAnnotation((Annotation) message.obj);
                break;
            case SHOW_NOTE:
                showNote((Note) message.obj);
                break;
            default:
                break;
        }
    }

    private void showNote(Note note) {
        final Context context = wr.get();
        if (context == null) {
            return;
        }
        Bible bible = Bible.getInstance(context);
        Bible.Note bibleNote = bible.getNote(note.osis, note.verse);
        dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.note)
                .setMessage(bibleNote.content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showAnnotation(Annotation annotation) {
        final Context context = wr.get();
        if (context == null) {
            return;
        }
        String message = annotation.message;
        if (TextUtils.isEmpty(message)) {
            Bible bible = Bible.getInstance(context);
            bible.loadAnnotations(annotation.osis, true);
            message = bible.getAnnotation(annotation.link);
        }
        if (message != null) {
            setShowAnnotation(context, annotation.link, message);
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
                .setMessage(DeprecationUtils.fromHtml(message))
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

    public static class Note {
        private final String verse;
        private final String osis;

        public Note(String verse, String osis) {
            this.verse = verse;
            this.osis = osis;
        }
    }

    public static class Annotation {
        private final String link;
        private final String message;
        private final String osis;

        public Annotation(String link, String message, String osis) {
            this.link = link;
            this.message = message;
            this.osis = osis;
        }
    }

}
