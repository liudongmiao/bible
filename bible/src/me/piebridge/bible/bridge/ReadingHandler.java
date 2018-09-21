package me.piebridge.bible.bridge;

import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.view.ActionMode;

import java.lang.ref.WeakReference;

import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;

/**
 * Created by thom on 16/6/21.
 */
public class ReadingHandler extends Handler {

    public static final int SHOW_ANNOTATION = 0;
    public static final int SHOW_NOTE = 1;
    public static final int SHOW_SELECTION = 2;
    public static final int ADD_NOTES = 3;
    public static final int SHARE = 4;

    private final WeakReference<AbstractReadingActivity> wr;


    public ReadingHandler(AbstractReadingActivity activity) {
        wr = new WeakReference<>(activity);
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case SHOW_ANNOTATION:
                showAnnotation((Annotation) message.obj);
                break;
            case SHOW_NOTE:
                showNote((String) message.obj);
                break;
            case SHOW_SELECTION:
                showSelection((Selection) message.obj);
                break;
            case ADD_NOTES:
                addNotes((String) message.obj);
                break;
            case SHARE:
                share((Selection) message.obj);
                break;
            default:
                break;
        }
    }

    private void addNotes(String verses) {
        AbstractReadingActivity activity = wr.get();
        if (activity != null) {
            activity.doAddNotes(verses);
        }
    }

    private void share(Selection selection) {
        AbstractReadingActivity activity = wr.get();
        if (activity != null) {
            activity.doShare(selection);
        }
    }

    private void showSelection(Selection selection) {
        final AbstractReadingActivity activity = wr.get();
        if (activity != null) {
            activity.doShowSelection(selection);
        }
    }

    private void showNote(String verse) {
        final AbstractReadingActivity activity = wr.get();
        if (activity != null) {
            activity.doShowNote(verse);
        }
    }

    private void showAnnotation(Annotation annotation) {
        final AbstractReadingActivity activity = wr.get();
        if (activity != null) {
            activity.doShowAnnotation(annotation);
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

        public String getLink() {
            return link;
        }

        public String getMessage() {
            return message;
        }

        public String getOsis() {
            return osis;
        }
    }

    public static class Selection {

        final boolean highlight;
        final String verses;
        final String content;

        public Selection(boolean highlight, String verses, String content) {
            this.highlight = highlight;
            this.verses = verses;
            this.content = content;
        }

        public String getVerses() {
            return verses;
        }

        public String getContent() {
            return content;
        }

        public boolean isHighlight() {
            return highlight;
        }

    }

    public static class SelectionActionMode implements ActionMode.Callback {

        private final WeakReference<AbstractReadingActivity> mReference;

        private Selection mSelection;

        public SelectionActionMode(AbstractReadingActivity activity, Selection selection) {
            this.mReference = new WeakReference<>(activity);
            this.mSelection = selection;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTag(mSelection);
            mode.setTitle(mSelection.verses);
            mode.getMenuInflater().inflate(R.menu.action, menu);
            mSelection = null;
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            Selection selection = (Selection) mode.getTag();
            mode.setTitle(selection.verses);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            final AbstractReadingActivity activity = mReference.get();
            if (activity == null) {
                return false;
            }
            Selection selection = (Selection) mode.getTag();
            switch (item.getItemId()) {
                case R.id.action_highlight:
                    activity.setHighlight(selection.verses, true);
                    return true;
                case R.id.action_unhighlight:
                    activity.setHighlight(selection.verses, false);
                    return true;
                case R.id.action_notes:
                    activity.addNotes(selection.verses);
                    return true;
                case R.id.action_share:
                    activity.share(selection);
                    return true;
                default:
                    return false;

            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }

    }

}
