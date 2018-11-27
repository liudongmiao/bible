package me.piebridge.bible.component;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NoteBundle;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 2018/10/27.
 */
public class AnnotationComponent {

    public static final String SORT_TIME = "time";
    public static final String SORT_BOOK = "book";

    private SQLiteOpenHelper mOpenHelper;

    private static final String TABLE_ANNOTATIONS = "annotations";
    private static final String TABLE_BOOKS = "books";
    public static final String COLUMN_ID = BaseColumns._ID;
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_OSIS = "osis";
    public static final String COLUMN_VERSE = "verse";
    public static final String COLUMN_VERSES = "verses";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_CREATETIME = "createtime";
    public static final String COLUMN_UPDATETIME = "updatetime";

    private static final String TYPE_HIGHLIGHT = "highlight";
    private static final String TYPE_NOTE = "note";

    public AnnotationComponent(Context context) {
        this.mOpenHelper = new AnnotationsDatabaseHelper(wrapContext(context));
    }

    private Context wrapContext(Context context) {
        return new Wrapper(context);
    }

    public Bundle getNoteVerses(String osis) {
        if (TextUtils.isEmpty(osis)) {
            return Bundle.EMPTY;
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return Bundle.EMPTY;
        }

        try (
                Cursor cursor = db.query(TABLE_ANNOTATIONS, new String[] {COLUMN_ID, COLUMN_VERSE},
                        COLUMN_OSIS + " = ? and " + COLUMN_TYPE + " = ?",
                        new String[] {osis, TYPE_NOTE}, null, null, null)
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(COLUMN_ID);
                int verseIndex = cursor.getColumnIndex(COLUMN_VERSE);
                Bundle bundle = new Bundle();
                do {
                    long id = cursor.getInt(idIndex);
                    String verse = cursor.getString(verseIndex);
                    if (!TextUtils.isEmpty(verse) && TextUtils.isDigitsOnly(verse)) {
                        bundle.putLong(verse, id);
                    } else {
                        LogUtils.w("invalid note, osis: " + osis + ", verse: " + verse);
                    }
                } while (cursor.moveToNext());
                if (bundle.isEmpty()) {
                    return Bundle.EMPTY;
                } else {
                    return bundle;
                }
            } else {
                return Bundle.EMPTY;
            }
        }
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
        long time = System.currentTimeMillis() / 1000;
        values.put(COLUMN_UPDATETIME, time);

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
            values.put(COLUMN_CREATETIME, time);
            db.insert(TABLE_ANNOTATIONS, null, values);
        }
        return true;
    }

    private boolean isDatabaseIntegrityOk(SQLiteDatabase database) {
        return database.isDatabaseIntegrityOk();
    }

    private Cursor searchHighlightBook() {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return null;
        }
        Cursor cursor = db.rawQuery("select a.* from annotations a left join books b" +
                " on (substr(a.osis, 1, instr(a.osis, '.') - 1) = b.osis)" +
                " where a.type = 'highlight' and a.verses != ''" +
                " order by b.number asc, cast(substr(a.osis, instr(a.osis, '.') + 1) as int) asc", null);
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    private Cursor searchHighlightTime(String book) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        List<String> arguments = new ArrayList<>();
        sb.append("type = ? and verses != ''");
        arguments.add("highlight");
        if (!TextUtils.isEmpty(book)) {
            sb.append(" and osis like ?");
            arguments.add(book + ".%");
        }
        Cursor cursor = db.query(TABLE_ANNOTATIONS, null,
                sb.toString(), arguments.toArray(new String[0]),
                null, null, "updatetime desc");
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    public Cursor searchHighlight(String book, String sort) {
        if (ObjectUtils.equals(SORT_BOOK, sort)) {
            return searchHighlightBook();
        } else {
            return searchHighlightTime(book);
        }
    }

    private Cursor searchNotesName(String query) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return null;
        }
        Cursor cursor = db.rawQuery("select a.* from annotations a left join books b" +
                        " on (substr(a.osis, 1, instr(a.osis, '.') - 1) = b.osis)" +
                        " where a.type = 'note' and a.verses != ''" +
                        (TextUtils.isEmpty(query) ? " and a.content != ''" : " and a.content like ?") +
                        " order by b.number asc, cast(substr(a.osis, instr(a.osis, '.') + 1) as int) asc",
                (TextUtils.isEmpty(query) ? null : new String[] {"%" + query + "%"}));
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    private Cursor searchNotesTime(String book, String query) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (!isDatabaseIntegrityOk(db)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        List<String> arguments = new ArrayList<>();
        sb.append("type = ? and verses != ''");
        arguments.add("note");
        if (!TextUtils.isEmpty(book)) {
            sb.append(" and osis like = ?");
            arguments.add(book + ".%");
        }
        if (TextUtils.isEmpty(query)) {
            sb.append(" and content != ''");
        } else {
            sb.append(" and content like ?");
            arguments.add("%" + query + "%");
        }
        Cursor cursor = db.query(TABLE_ANNOTATIONS, null,
                sb.toString(), arguments.toArray(new String[0]),
                null, null, "updatetime desc");
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    public Cursor searchNotes(String book, String query, String sort) {
        if (ObjectUtils.equals(SORT_BOOK, sort)) {
            return searchNotesName(query);
        } else {
            return searchNotesTime(book, query);
        }
    }

    private static class AnnotationsDatabaseHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 3;
        private static final String DATABASE_NAME = "annotations.db";

        AnnotationsDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            LogUtils.d("onCreate " + db);
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
            addOsis(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            LogUtils.d("will update  " + db + " from " + oldVersion + " to " + newVersion);
            if (oldVersion < 2) {
                onCreate(db);
                addOsis(db);
            } else if (oldVersion < 0x3) {
                addOsis(db);
            }
        }

        private synchronized void addOsis(SQLiteDatabase db) {
            LogUtils.d("will add osis");
            // @formatter:off
            db.execSQL("DROP TABLE IF EXISTS books");
            db.execSQL(
                    "CREATE TABLE books (number INTEGER PRIMARY KEY, osis TEXT NOT NULL, human TEXT NOT NULL, chapters INTEGER NOT NULL);");
            List<String> statements = Arrays.asList(
                    "INSERT INTO books VALUES(1,'Gen','Genesis',50);",
                    "INSERT INTO books VALUES(2,'Exod','Exodus',40);",
                    "INSERT INTO books VALUES(3,'Lev','Leviticus',27);",
                    "INSERT INTO books VALUES(4,'Num','Numbers',36);",
                    "INSERT INTO books VALUES(5,'Deut','Deuteronomy',34);",
                    "INSERT INTO books VALUES(6,'Josh','Joshua',24);",
                    "INSERT INTO books VALUES(7,'Judg','Judges',21);",
                    "INSERT INTO books VALUES(8,'Ruth','Ruth',4);",
                    "INSERT INTO books VALUES(9,'1Sam','1 Samuel',31);",
                    "INSERT INTO books VALUES(10,'2Sam','2 Samuel',24);",
                    "INSERT INTO books VALUES(11,'1Kgs','1 Kings',22);",
                    "INSERT INTO books VALUES(12,'2Kgs','2 Kings',25);",
                    "INSERT INTO books VALUES(13,'1Chr','1 Chronicles',29);",
                    "INSERT INTO books VALUES(14,'2Chr','2 Chronicles',36);",
                    "INSERT INTO books VALUES(15,'Ezra','Ezra',10);",
                    "INSERT INTO books VALUES(16,'Neh','Nehemiah',13);",
                    "INSERT INTO books VALUES(17,'Tob','Tobit',14);",
                    "INSERT INTO books VALUES(18,'Jdt','Judith',16);",
                    "INSERT INTO books VALUES(19,'Esth','Esther',10);",
                    "INSERT INTO books VALUES(20,'1Macc','1 Maccabees',16);",
                    "INSERT INTO books VALUES(21,'2Macc','2 Maccabees',15);",
                    "INSERT INTO books VALUES(22,'Job','Job',42);",
                    "INSERT INTO books VALUES(23,'Ps','Psalm',150);",
                    "INSERT INTO books VALUES(24,'Prov','Proverbs',31);",
                    "INSERT INTO books VALUES(25,'Eccl','Ecclesiastes',12);",
                    "INSERT INTO books VALUES(26,'Song','Song of Solomon',8);",
                    "INSERT INTO books VALUES(27,'Wis','Wisdom',19);",
                    "INSERT INTO books VALUES(28,'Sir','Sirach',51);",
                    "INSERT INTO books VALUES(29,'Isa','Isaiah',66);",
                    "INSERT INTO books VALUES(30,'Jer','Jeremiah',52);",
                    "INSERT INTO books VALUES(31,'Lam','Lamentations',5);",
                    "INSERT INTO books VALUES(32,'Bar','Baruch',5);",
                    "INSERT INTO books VALUES(33,'Ezek','Ezekiel',48);",
                    "INSERT INTO books VALUES(34,'Dan','Daniel',12);",
                    "INSERT INTO books VALUES(35,'Hos','Hosea',14);",
                    "INSERT INTO books VALUES(36,'Joel','Joel',3);",
                    "INSERT INTO books VALUES(37,'Amos','Amos',9);",
                    "INSERT INTO books VALUES(38,'Obad','Obadiah',1);",
                    "INSERT INTO books VALUES(39,'Jonah','Jonah',4);",
                    "INSERT INTO books VALUES(40,'Mic','Micah',7);",
                    "INSERT INTO books VALUES(41,'Nah','Nahum',3);",
                    "INSERT INTO books VALUES(42,'Hab','Habakkuk',3);",
                    "INSERT INTO books VALUES(43,'Zeph','Zephaniah',3);",
                    "INSERT INTO books VALUES(44,'Hag','Haggai',2);",
                    "INSERT INTO books VALUES(45,'Zech','Zechariah',14);",
                    "INSERT INTO books VALUES(46,'Mal','Malachi',4);",
                    "INSERT INTO books VALUES(47,'Matt','Matthew',28);",
                    "INSERT INTO books VALUES(48,'Mark','Mark',16);",
                    "INSERT INTO books VALUES(49,'Luke','Luke',24);",
                    "INSERT INTO books VALUES(50,'John','John',21);",
                    "INSERT INTO books VALUES(51,'Acts','Acts',28);",
                    "INSERT INTO books VALUES(52,'Rom','Romans',16);",
                    "INSERT INTO books VALUES(53,'1Cor','1 Corinthians',16);",
                    "INSERT INTO books VALUES(54,'2Cor','2 Corinthians',13);",
                    "INSERT INTO books VALUES(55,'Gal','Galatians',6);",
                    "INSERT INTO books VALUES(56,'Eph','Ephesians',6);",
                    "INSERT INTO books VALUES(57,'Phil','Philippians',4);",
                    "INSERT INTO books VALUES(58,'Col','Colossians',4);",
                    "INSERT INTO books VALUES(59,'1Thess','1 Thessalonians',5);",
                    "INSERT INTO books VALUES(60,'2Thess','2 Thessalonians',3);",
                    "INSERT INTO books VALUES(61,'1Tim','1 Timothy',6);",
                    "INSERT INTO books VALUES(62,'2Tim','2 Timothy',4);",
                    "INSERT INTO books VALUES(63,'Titus','Titus',3);",
                    "INSERT INTO books VALUES(64,'Phlm','Philemon',1);",
                    "INSERT INTO books VALUES(65,'Heb','Hebrews',13);",
                    "INSERT INTO books VALUES(66,'Jas','James',5);",
                    "INSERT INTO books VALUES(67,'1Pet','1 Peter',5);",
                    "INSERT INTO books VALUES(68,'2Pet','2 Peter',3);",
                    "INSERT INTO books VALUES(69,'1John','1 John',5);",
                    "INSERT INTO books VALUES(70,'2John','2 John',1);",
                    "INSERT INTO books VALUES(71,'3John','3 John',1);",
                    "INSERT INTO books VALUES(72,'Jude','Jude',1);",
                    "INSERT INTO books VALUES(73,'Rev','Revelation',22);"
            );
            // @formatter:on
            for (String statement : statements) {
                db.execSQL(statement);
            }
        }
    }

    private static class Wrapper extends ContextWrapper {

        public Wrapper(Context base) {
            super(base);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return openOrCreateDatabase(name, mode, factory, null);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                                   DatabaseErrorHandler errorHandler) {
            File path = getDatabasePath(name);
            int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
            if ((mode & MODE_ENABLE_WRITE_AHEAD_LOGGING) != 0) {
                flags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
            }
            if ((mode & MODE_NO_LOCALIZED_COLLATORS) != 0) {
                flags |= SQLiteDatabase.NO_LOCALIZED_COLLATORS;
            }
            return SQLiteDatabase.openDatabase(path.getPath(), factory, flags, errorHandler);
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
