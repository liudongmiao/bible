package me.piebridge.bible.utils;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;

import java.io.File;

import me.piebridge.bible.Bible;
import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;

/**
 * Created by thom on 16/7/1.
 */
public class BibleUtils {

    private BibleUtils() {

    }

    public static String getBook(String osis) {
        int index = osis.indexOf('.');
        if (index > 0) {
            return osis.substring(0, index);
        } else {
            LogUtils.w("invalid osis: " + osis);
            return osis;
        }
    }

    public static String getChapter(String osis) {
        int index = osis.indexOf('.');
        if (index > 0) {
            return osis.substring(index + 1);
        } else {
            LogUtils.w("invalid osis: " + osis);
            return "1";
        }
    }

    public static String getChapter(String osis, Context context) {
        String chapter = getChapter(osis);
        if (Bible.INTRO.equalsIgnoreCase(chapter)) {
            return context.getString(R.string.intro);
        } else {
            return chapter;
        }
    }

    public static String getChapterVerse(Context context, Bundle bundle) {
        String osis = bundle.getString(AbstractReadingActivity.OSIS);
        String chapter = getChapter(osis, context);
        int verseStart = NumberUtils.parseInt(bundle.getString(AbstractReadingActivity.VERSE_START));
        int verseEnd = NumberUtils.parseInt(bundle.getString(AbstractReadingActivity.VERSE_END));
        if (verseStart > 0) {
            StringBuilder sb = new StringBuilder(chapter);
            sb.append(":");
            sb.append(verseStart);
            if (verseEnd > 0) {
                sb.append("-");
                sb.append(verseEnd);
            }
            return sb.toString();
        } else {
            return chapter;
        }
    }

    public static String getBookChapterVerse(String bookName, String chapterVerse) {
        StringBuilder sb = new StringBuilder(bookName);
        if (!Bible.isCJK(bookName)) {
            sb.append(" ");
        }
        sb.append(chapterVerse);
        return sb.toString();
    }

    public static String getFontPath(Context context) {
        File font;
        File[] dirs = ContextCompat.getExternalFilesDirs(context, null);
        if (dirs != null && dirs.length > 0 && dirs[0] != null) {
            font = new File(dirs[0], "custom.ttf");
            if (font.isFile()) {
                return font.getAbsolutePath();
            }
        }
        return null;
    }

}
