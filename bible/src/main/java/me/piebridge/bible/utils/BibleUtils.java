package me.piebridge.bible.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.component.VersionComponent;
import me.piebridge.bible.provider.VersionProvider;

import static me.piebridge.bible.activity.AbstractReadingActivity.CONTENT;
import static me.piebridge.bible.activity.AbstractReadingActivity.OSIS;
import static me.piebridge.bible.activity.AbstractReadingActivity.SHANGTI;
import static me.piebridge.bible.activity.AbstractReadingActivity.VERSION;

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
        if (osis == null) {
            return null;
        }
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
        if (VersionComponent.INTRO.equalsIgnoreCase(chapter)) {
            return context.getString(R.string.reading_chapter_intro);
        } else {
            return chapter;
        }
    }

    public static String getChapterVerse(Context context, Bundle bundle) {
        String osis = bundle.getString(OSIS);
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
        return bookName + " " + chapterVerse;
    }

    public static String getFontPath(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir != null) {
            File font = new File(dir, "custom.ttf");
            if (font.isFile()) {
                return font.getAbsolutePath();
            }
        }
        return null;
    }

    public static int[] getChapterVerse(String string) {
        int value = new BigDecimal(string).multiply(new BigDecimal(VersionProvider.THOUSAND)).intValue();
        int chapter = value / VersionProvider.THOUSAND;
        int verse = value % VersionProvider.THOUSAND;
        return new int[] {chapter, verse};
    }

    public static boolean isDemoVersion(String version) {
        switch (version) {
            case "cu89sdemo":
            case "cu89tdemo":
            case "asvdemo":
                return true;
            default:
                return false;
        }
    }

    public static String removeDemo(String version) {
        switch (version) {
            case "cu89sdemo":
                return "cu89s";
            case "cu89tdemo":
                return "cu89t";
            case "asvdemo":
                return "asv";
            default:
                return version;
        }
    }

    public static boolean isCJK(String s) {
        for (char c : s.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    public static String getVerse(String verses) {
        int index1 = verses.indexOf('-');
        int index2 = verses.indexOf(',');
        if (index1 > 0 && index2 > 0) {
            return verses.substring(0, Math.min(index1, index2));
        } else if (index1 > 0) {
            return verses.substring(0, index1);
        } else if (index2 > 0) {
            return verses.substring(0, index2);
        } else {
            return verses;
        }
    }

    public static String getLastVerse(String verses) {
        int index1 = verses.lastIndexOf('-');
        int index2 = verses.lastIndexOf(',');
        int index = Math.max(index1, index2);
        if (index < 0) {
            return verses;
        } else {
            return verses.substring(index + 1);
        }
    }

    public static void fixItems(ArrayList<OsisItem> items) {
        Iterator<OsisItem> it = items.iterator();
        while (it.hasNext()) {
            OsisItem item = it.next();
            if (TextUtils.isEmpty(item.chapter)) {
                it.remove();
            }
        }
    }

    public static void startLauncher(Context context, Bundle bundle) {
        Intent launcher = context.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
        if (launcher != null) {
            Intent intent = new Intent();
            intent.setComponent(launcher.getComponent());
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (bundle != null) {
                intent.putExtras(bundle);
            }
            context.startActivity(intent);
        }
    }


    private static boolean isZhCN(String version) {
        switch (version) {
            case "ccb":
            case "cnvs":
            case "csbs":
            case "cunpss":
            case "cuvmps":
            case "cuvmpsdemo":
            case "rcu17ss":
            case "cu89s":
            case "cu89sdemo":
                return true;
            default:
                return false;
        }

    }

    private static String fix(String content, String version, boolean shangti) {
        String fixed = content;
        if (fixed.contains("\uD84C\uDFB4") && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            fixed = fixed.replaceAll("\uD84C\uDFB4", "墩");
        }
        if (isZhCN(version)) {
            fixed = fixed.replaceAll("「", "“").replaceAll("」", "”")
                    .replaceAll("『", "‘").replaceAll("』", "’");
        }
        fixed = fixDouble(fixed, "pn");
        fixed = fixDouble(fixed, "name");
        fixed = fixDouble(fixed, "place");
        fixed = fixDouble(fixed, "person");
        if (shangti) {
            return fixed.replaceAll("　神", "上帝")
                    .replaceAll("　<span class=\"add\">神", "<span class=\"add\">上帝");
        } else {
            return fixed.replaceAll("上帝", "　神");
        }
    }

    private static String fixDouble(String content, String clazz) {
        Pattern pattern = null;
        int index = content.indexOf("class=\"" + clazz);
        if (index > 0 && content.indexOf("class=\"" + clazz + "2", index + 1) == -1) {
            switch (clazz) {
                case "pn":
                    pattern = Holder.PN;
                    break;
                case "name":
                    pattern = Holder.NAME;
                    break;
                case "place":
                    pattern = Holder.PLACE;
                    break;
                case "person":
                    pattern = Holder.PERSON;
                default:
                    break;
            }
        }
        if (pattern != null) {
            return pattern.matcher(content).replaceAll("$1" + clazz + "-" + clazz + " $2");
        }
        return content;
    }

    public static String fix(BibleApplication application, String content) {
        boolean shangti = PreferenceManager.getDefaultSharedPreferences(application).getBoolean(SHANGTI, false);
        return fix(content, application.getVersion(), shangti);
    }

    public static boolean putAll(Bundle oldBundle, Bundle newBundle) {
        Set<String> keys = newBundle.keySet();
        for (String key : keys) {
            Object oldValue = oldBundle.get(key);
            Object newValue = newBundle.get(key);
            boolean same;
            switch (key) {
                case VERSION:
                    same = isSameVersion((String) oldValue, (String) newValue);
                    break;
                case CONTENT:
                    same = isSameContent((byte[]) oldValue, (byte[]) newValue);
                    break;
                default:
                    same = ObjectUtils.equals(oldValue, newValue);
                    break;
            }
            if (!same) {
                oldBundle.putAll(newBundle);
                return true;
            }
        }
        return false;
    }

    private static boolean isSameVersion(String oldValue, String newValue) {
        String oldVersion = BibleUtils.removeDemo(oldValue);
        String newVersion = BibleUtils.removeDemo(newValue);
        return ObjectUtils.equals(oldVersion, newVersion);
    }

    private static boolean isSameContent(byte[] oldValue, byte[] newValue) {
        byte[] oldContent = FileUtils.uncompress(oldValue);
        byte[] newContent = FileUtils.uncompress(newValue);
        return Arrays.equals(oldContent, newContent);
    }

    private static class Holder {
        static final Pattern PN = Pattern.compile("(<span class=\"\\bpn\\b[^<>]*\">[^<>]*</span><span class=\")" +
                "(\\bpn\\b[^<>]*\")");
        static final Pattern NAME = Pattern.compile("(<span class=\"\\bname\\b[^<>]*\">[^<>]*</span><span class=\")" +
                "(\\bname\\b[^<>]*\")");
        static final Pattern PLACE = Pattern.compile("(<span class=\"\\bplace\\b[^<>]*\">[^<>]*</span><(?:u|span) class=\")" +
                "(\\b(?:place|person)\\b[^<>]*\")");
        static final Pattern PERSON = Pattern.compile("(<u class=\"\\bperson\\b[^<>]*\">[^<>]*</u><(?:u|span) class=\")" +
                "(\\b(?:place|person)\\b[^<>]*\")");
    }

}
