package me.piebridge.bible;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NoteBundle;

/**
 * Created by thom on 2018/10/27.
 */
public class AnnotationComponent {

    private SQLiteOpenHelper mOpenHelper;

    private static final String TABLE_ANNOTATIONS = "annotations";
    private static final String COLUMN_ID = BaseColumns._ID;
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_OSIS = "osis";
    private static final String COLUMN_VERSE = "verse";
    private static final String COLUMN_VERSES = "verses";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_CREATETIME = "createtime";
    private static final String COLUMN_UPDATETIME = "updatetime";

    private static final String TYPE_HIGHLIGHT = "highlight";
    private static final String TYPE_NOTE = "note";

    public AnnotationComponent(Context context) {
        this.mOpenHelper = new AnnotationsDatabaseHelper(wrapContext(context));
    }

    private Context wrapContext(Context context) {
        return new Wrapper(context);
    }

    public Bundle getNoteVerses(String osis) {
        Bundle bundle = new Bundle();
        if (TextUtils.isEmpty(osis)) {
            return bundle;
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return bundle;
        }

        try (
                Cursor cursor = db.query(TABLE_ANNOTATIONS, new String[] {COLUMN_ID, COLUMN_VERSE},
                        COLUMN_OSIS + " = ? and " + COLUMN_TYPE + " = ?",
                        new String[] {osis, TYPE_NOTE}, null, null, null)
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(COLUMN_ID);
                int verseIndex = cursor.getColumnIndex(COLUMN_VERSE);
                do {
                    long id = cursor.getInt(idIndex);
                    String verse = cursor.getString(verseIndex);
                    if (!TextUtils.isEmpty(verse) && TextUtils.isDigitsOnly(verse)) {
                        bundle.putLong(verse, id);
                    } else {
                        LogUtils.w("invalid note, osis: " + osis + ", verse: " + verse);
                    }
                } while (cursor.moveToNext());
            }
        }
        return bundle;
    }

    public Bundle getNote(long id) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return null;
        }

        try (
                Cursor cursor = db.query(TABLE_ANNOTATIONS, null, COLUMN_ID + " = ?",
                        new String[] {Long.toString(id)}, null, null, null)
        ) {
            if (cursor != null && cursor.moveToNext()) {
                String verse = cursor.getString(cursor.getColumnIndex(COLUMN_VERSE));
                String verses = cursor.getString(cursor.getColumnIndex(COLUMN_VERSES));
                String content = cursor.getString(cursor.getColumnIndex(COLUMN_CONTENT));
                Bundle bundle = new Bundle();
                bundle.putLong(NoteBundle.ID, id);
                bundle.putString(NoteBundle.VERSE, verse);
                bundle.putString(NoteBundle.VERSES, verses);
                bundle.putString(NoteBundle.CONTENT, content);
                return bundle;
            }
        }
        return null;
    }

    public boolean saveNote(long id, String osis, String verse, String verses, String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            LogUtils.d("cannot save note");
            return false;
        }

        long time = System.currentTimeMillis() / 1000;
        if (id == 0) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_TYPE, TYPE_NOTE);
            values.put(COLUMN_OSIS, osis);
            values.put(COLUMN_VERSE, verse);
            values.put(COLUMN_VERSES, verses);
            values.put(COLUMN_CONTENT, content);
            values.put(COLUMN_CREATETIME, time);
            values.put(COLUMN_UPDATETIME, time);
            db.insert(TABLE_ANNOTATIONS, null, values);
        } else {
            ContentValues values = new ContentValues();
            values.put(COLUMN_VERSES, verses);
            values.put(COLUMN_CONTENT, content);
            values.put(COLUMN_UPDATETIME, time);
            db.update(TABLE_ANNOTATIONS, values, COLUMN_ID + " = ?", new String[] {Long.toString(id)});
        }
        return true;
    }

    public boolean deleteNote(long id) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return false;
        }
        db.delete(TABLE_ANNOTATIONS, COLUMN_ID + " = ?", new String[] {Long.toString(id)});
        return true;
    }

    public String getHighlight(String osis) {
        String highlighted = "";
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return highlighted;
        }
        try (
                Cursor cursor = db.query(TABLE_ANNOTATIONS, new String[] {COLUMN_ID, COLUMN_VERSES},
                        COLUMN_OSIS + " = ? and " + COLUMN_TYPE + " = ?",
                        new String[] {osis, TYPE_HIGHLIGHT}, null, null, null)
        ) {
            while (cursor != null && cursor.moveToNext()) {
                highlighted = cursor.getString(1);
            }
        } catch (SQLiteException e) {
            LogUtils.d("sqlite", e);
        }
        return highlighted;
    }

    public boolean saveHighlight(String osis, String verses) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_OSIS, osis);
        values.put(COLUMN_TYPE, TYPE_HIGHLIGHT);
        values.put(COLUMN_VERSES, verses);

        long id = 0;
        boolean found = false;
        try (
                Cursor cursor = db.query(TABLE_ANNOTATIONS, new String[] {COLUMN_ID},
                        COLUMN_OSIS + " = ? and " + COLUMN_TYPE + " = ?",
                        new String[] {osis, TYPE_HIGHLIGHT}, null, null, null)
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                found = true;
                id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID));
            }
        }
        if (found) {
            db.update(TABLE_ANNOTATIONS, values, COLUMN_ID + " = ?", new String[] {Long.toString(id)});
        } else {
            db.insert(TABLE_ANNOTATIONS, null, values);
        }
        return true;
    }

    private boolean isDatabaseIntegrityOk(SQLiteDatabase database) {
        return database.isDatabaseIntegrityOk();
    }

    private class AnnotationsDatabaseHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 2;
        private static final String DATABASE_NAME = "annotations.db";

        AnnotationsDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANNOTATIONS);
            db.execSQL("CREATE TABLE " + TABLE_ANNOTATIONS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    COLUMN_OSIS + " VARCHAR NOT NULL," +
                    COLUMN_TYPE + " VARCHAR NOT NULL," +
                    COLUMN_VERSE + " VARCHAR," +
                    COLUMN_VERSES + " TEXT," +
                    COLUMN_CONTENT + " TEXT," +
                    COLUMN_CREATETIME + " DATETIME," +
                    COLUMN_UPDATETIME + " DATETIME" +
                    ");");

            db.execSQL("CREATE INDEX osis_tye ON " + TABLE_ANNOTATIONS + " (" + COLUMN_OSIS + ", " + COLUMN_TYPE + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                onCreate(db);
            }
        }

    }

    private static class Wrapper extends ContextWrapper {

        public Wrapper(Context base) {
            super(base);
        }

        @Override
        public File getDatabasePath(String name) {
            File oldPath = super.getDatabasePath(name);
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                LogUtils.w("annotations file: " + oldPath);
                return oldPath;
            }

            File oldParent = oldPath.getParentFile();
            File newParent = new File(dir.getParentFile(), "databases");
            File newPath = new File(newParent, oldPath.getName());
            makeDir(newParent);

            if (oldPath.exists() && (!newPath.exists() || oldPath.lastModified() > newPath.lastModified())) {
                String[] children = oldParent.list();
                for (String child : children) {
                    if (child.startsWith(name)) {
                        copy(new File(oldParent, child), new File(newParent, child));
                    }
                }
            }

            LogUtils.i("annotations file: " + newPath);
            return newPath;
        }

        private void makeDir(File dir) {
            if (dir.exists() && !dir.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                dir.delete();
            }
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
        }

        private void copy(File src, File dst) {
            try (
                    OutputStream os = new FileOutputStream(dst);
                    InputStream is = new FileInputStream(src)
            ) {
                int length;
                byte[] bytes = new byte[0x2000];
                while ((length = is.read(bytes)) != -1) {
                    os.write(bytes, 0, length);
                }
                LogUtils.i("copy " + src + " to " + dst);
                //noinspection ResultOfMethodCallIgnored
                dst.setLastModified(src.lastModified());
            } catch (IOException e) {
                LogUtils.w("cannot copy " + src + " to " + dst, e);
            }
        }

    }

}
