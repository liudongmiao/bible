package me.piebridge.bible.utils;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import me.piebridge.bible.BaseActivity;
import me.piebridge.bible.R;

/**
 * Created by thom on 16/7/1.
 */
public class BibleUtils {

    private BibleUtils() {

    }

    public static int getNumber(Bundle bundle, String key) {
        String s = bundle.getString(key);
        if (!TextUtils.isEmpty(s) && TextUtils.isDigitsOnly(s)) {
            return Integer.parseInt(s);
        } else {
            return -1;
        }
    }

    public static String getBook(String osis) {
        int index = osis.indexOf('.');
        if (index > 0) {
            return osis.substring(0, index);
        } else {
            LogUtils.wtf("invalid osis: " + osis);
            return osis;
        }
    }

    public static String getChapter(String osis, Context context) {
        int index = osis.indexOf('.');
        if (index > 0) {
            String chapter = osis.substring(index + 1);
            if ("int".equalsIgnoreCase(chapter)) {
                return context.getString(R.string.intro);
            } else {
                return chapter;
            }
        } else {
            LogUtils.wtf("invalid osis: " + osis);
            return "1";
        }
    }

    public static String getChapterVerse(Context context, Bundle bundle) {
        String osis = bundle.getString(BaseActivity.OSIS);
        String chapter = getChapter(osis, context);
        int verseStart = BibleUtils.getNumber(bundle, BaseActivity.VERSE_START);
        int verseEnd = BibleUtils.getNumber(bundle, BaseActivity.VERSE_END);
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

}
