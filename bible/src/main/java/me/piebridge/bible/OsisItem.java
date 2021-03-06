/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2013 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ObjectUtils;

public class OsisItem implements Parcelable {

    public String book;
    public String chapter = "";
    public String verseStart = "";
    public String verseEnd = "";
    public String verses = "";

    OsisItem(Parcel parcel) {
        this.book = parcel.readString();
        this.chapter = parcel.readString();
        this.verseStart = parcel.readString();
        this.verseEnd = parcel.readString();
        this.verses = parcel.readString();
    }

    public OsisItem(String book) {
        this.book = book;
    }

    public OsisItem(String book, int chapter) {
        this.book = book;
        this.chapter = String.valueOf(chapter);
    }

    public OsisItem(String book, String chapter) {
        this.book = book;
        this.chapter = chapter;
    }

    public OsisItem(String book, String chapter, String verseStart) {
        this.book = book;
        this.chapter = chapter;
        if (isValidVerse(verseStart)) {
            this.verseStart = verseStart;
        }
    }

    public OsisItem(String book, int chapter, int verseStart) {
        this.book = book;
        this.chapter = String.valueOf(chapter);
        if (verseStart > 0) {
            this.verseStart = String.valueOf(verseStart);
        }
    }

    public OsisItem(String book, String chapter, String verseStart, String verseEnd) {
        this.book = book;
        this.chapter = chapter;
        if (isValidVerse(verseStart)) {
            this.verseStart = verseStart;
            if (isValidVerse(verseEnd)) {
                this.verseEnd = verseEnd;
            }
        }
    }

    private boolean isValidVerse(String verse) {
        return NumberUtils.parseInt(verse) > 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(book);
        sb.append(" ");
        sb.append(chapter);
        if (!TextUtils.isEmpty(verses)) {
            sb.append(":");
            sb.append(verses);
        } else if (!TextUtils.isEmpty(verseStart)) {
            sb.append(":");
            sb.append(verseStart);
            if (!TextUtils.isEmpty(verseEnd)) {
                sb.append("-");
                sb.append(verseEnd);
            }
        }
        return sb.toString();
    }

    public String toBook() {
        return book;
    }

    public String toOsis() {
        return String.format("%s.%s", book, chapter);
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(AbstractReadingActivity.OSIS, toOsis());
        bundle.putString(AbstractReadingActivity.VERSE_START, verseStart);
        bundle.putString(AbstractReadingActivity.VERSE_END, verseEnd);
        return bundle;
    }

    public static ArrayList<OsisItem> parseSearch(String s, BibleApplication application) {
        String previous = PreferenceManager.getDefaultSharedPreferences(application).getString("osis", "Gen.1");
        return parseSearch(s, application, previous);
    }

    public static String fixOsis(String s) {
        return s.replaceAll("([A-Za-z]+)\\.", "$1")
                .replaceAll("(\\d?)\\s*(\\D?)", "$1$2");
    }

    private static boolean isDigitsOnly(String s) {
        return !TextUtils.isEmpty(s) && TextUtils.isDigitsOnly(s);
    }

    public static ArrayList<OsisItem> parseSearch(String query, BibleApplication application, String previous) {
        ArrayList<OsisItem> items = new ArrayList<>();
        if (TextUtils.isEmpty(query)) {
            return items;
        }

        String s = fixOsis(query);
        s = s.replace("cf", "");
        s = s.replace("+", " ");
        s = s.replace("\u00b7", ":");
        s = s.replace("\u2027", ":");
        s = s.replace("\uff1a", ":");
        s = s.replace("\ufe55", ":");
        s = s.replace("\uff0d", "-");
        s = s.replace("\u2010", "-");
        s = s.replace("\u2212", "-");
        s = s.replace("\u2012", "-");
        s = s.replace("\u2013", "-");
        s = s.replace("\u2014", "-");
        s = s.replace("\u2015", "-");
        s = s.replace("\ufe63", "-");
        s = s.replace("\u3002", ":");
        s = s.replace("\ufe12", ":");
        s = s.replace("\uff61", ":");
        s = s.replace("\u002e", ":");
        s = s.replace("\uff0e", ":");
        s = s.replace("\ufe52", ":");
        s = s.replace("\u7ae0", ":");
        s = s.replace("\uff1b", ";");
        s = s.replace("\ufe14", ";");
        s = s.replace("\ufe54", ";");
        s = s.replace("\uff0c", ",");
        s = s.replace("\ufe50", ",");
        s = s.replace("(", "");
        s = s.replace(")", "");
        s = s.replace("\uff08", "");
        s = s.replace("\uff09", "");
        s = s.replace("\u3010", "");
        s = s.replace("\u3011", "");
        s = s.replace("\u3016", "");
        s = s.replace("\u3017", "");
        s = s.replace("[", "");
        s = s.replace("]", "");
        s = s.replaceAll("-\\d?[A-Za-z]+\\s*", "-");

        // John 3; John 3:16; John 3:16-17; John 3-4; John 3-4:5; John 3:16-4:6
        Pattern p = Pattern.compile("\\s*(\\d?\\s*?[^\\d\\s:-;]*)\\s*(\\d*):?(\\d*)\\s*?-?\\s*?(\\d*):?(\\d*);?");
        Matcher m = p.matcher(s);
        String prevbook = "";
        String prevchap = "";
        String prevosis = "";
        String prevgroup = "";
        while (m.find()) {
            String group = m.group();
            if (group == null || group.length() == 0) {
                continue;
            }
            String book = m.group(1);
            String startChapter = m.group(2);
            String startVerse = m.group(3);
            String endChapter = m.group(4);
            String endVerse = m.group(5);

            book = book.replace(":", "");
            if (book.startsWith(",")) {
                String subbook = book.substring(1);
                if (BibleUtils.isCJK(subbook)) {
                    book = subbook;
                } else {
                    book = prevbook;
                    if ("".equals(startVerse) && !"".equals(prevchap)) {
                        startVerse = startChapter;
                        startChapter = prevchap;
                    }
                }
            } else if (!BibleUtils.isCJK(book) && book.length() < 2) {
                startChapter = book + startChapter;
                book = prevbook;
            }

            prevbook = book;
            if (!"".equals(startVerse)) {
                prevchap = startChapter;
            } else {
                prevchap = "";
            }

            String osis = null;
            if (isDigitsOnly(startChapter) && (TextUtils.isEmpty(book) || book.equalsIgnoreCase("ch"))) {
                if (!TextUtils.isEmpty(prevosis)) {
                    osis = prevosis;
                } else if (!TextUtils.isEmpty(previous) && isDigitsOnly(startVerse)) {
                    osis = BibleUtils.getBook(previous);
                }
            } else if (book.equalsIgnoreCase("v") || book.equalsIgnoreCase("vv") || book.equalsIgnoreCase("ver")) {
                startVerse = startChapter;
                endVerse = endChapter;
                endChapter = "";
                if (!TextUtils.isEmpty(prevosis) && !TextUtils.isEmpty(prevchap)) {
                    osis = prevosis;
                    startChapter = prevchap;
                } else if (!TextUtils.isEmpty(previous)) {
                    osis = BibleUtils.getBook(previous);
                    startChapter = BibleUtils.getChapter(previous);
                }
            } else {
                osis = application.getOsis(book);
            }
            if (osis == null) {
                continue;
            }

            if (endChapter.equals(startChapter)) {
                endChapter = endVerse;
                endVerse = "";
            }

            if ("".equals(startChapter)) {
                items.add(new OsisItem(osis));
            } else if ("".equals(endChapter) || (!"".equals(startVerse) && "".equals(endVerse))) {
                // 3, 3:16, 3:16-17
                OsisItem newItem = new OsisItem(osis, startChapter, startVerse, endChapter);
                if (ObjectUtils.equals(osis, prevosis) && !items.isEmpty()) {
                    if (prevgroup.endsWith("-")) {
                        int index = items.size() - 1;
                        OsisItem oldItem = items.get(index);
                        if (ObjectUtils.equals(oldItem.chapter, startChapter)
                                && TextUtils.isEmpty(oldItem.verseEnd)
                                && TextUtils.isEmpty(newItem.verseEnd)) {
                            oldItem.verseEnd = newItem.verseStart;
                            newItem = null;
                        }
                    } else if (group.startsWith(",")) {
                        int index = items.size() - 1;
                        OsisItem oldItem = items.get(index);
                        if (ObjectUtils.equals(oldItem.chapter, startChapter)) {
                            //noinspection StringConcatenationInLoop
                            StringBuilder verses = new StringBuilder(oldItem.verses);
                            if (TextUtils.isEmpty(verses)) {
                                verses.append(oldItem.verseStart);
                                if (!TextUtils.isEmpty(oldItem.verseEnd)) {
                                    verses.append("-");
                                    verses.append(oldItem.verseEnd);
                                }
                            }
                            verses.append(",");
                            verses.append(startVerse);
                            if (!TextUtils.isEmpty(endChapter)) {
                                verses.append("-");
                                verses.append(endChapter);
                            }
                            oldItem.verses = verses.toString();
                            newItem = null;
                        }
                    }
                }
                if (newItem != null) {
                    items.add(newItem);
                }
            } else if ("".equals(startVerse) || (!"".equals(startVerse) && !"".equals(endVerse))) {
                // 3-4, 3-4:5, 3:16-4:6
                int start = NumberUtils.parseInt(startChapter);
                int end = NumberUtils.parseInt(endChapter);
                items.add(new OsisItem(osis, startChapter, startVerse));
                for (int i = start + 1; i < end; i++) {
                    items.add(new OsisItem(osis, i));
                }
                if (end > start) {
                    items.add(new OsisItem(osis, endChapter, endVerse.equals("") ? "" : "1", endVerse));
                }
            }

            prevosis = osis;
            prevgroup = group;
        }

        return items;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int falgs) {
        parcel.writeString(book);
        parcel.writeString(chapter);
        parcel.writeString(verseStart);
        parcel.writeString(verseEnd);
        parcel.writeString(verses);
    }

    public static final Parcelable.Creator<OsisItem> CREATOR = new Parcelable.Creator<OsisItem>() {
        public OsisItem createFromParcel(Parcel parcel) {
            return new OsisItem(parcel);
        }

        public OsisItem[] newArray(int size) {
            return new OsisItem[size];
        }
    };

    public String forceVerses() {
        StringBuilder sb = new StringBuilder();
        sb.append(verseStart);
        if (!TextUtils.isEmpty(verseEnd)) {
            sb.append("-");
            sb.append(verseEnd);
        }
        return sb.toString();
    }

}
