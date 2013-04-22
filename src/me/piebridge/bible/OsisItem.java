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

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

public class OsisItem implements Parcelable {

    public String book;
    public String chapter = "";
    public String verse = "";
    public String end = "";

    OsisItem(Parcel parcel) {
        this.book = parcel.readString();
        this.chapter = parcel.readString();
        this.verse = parcel.readString();
        this.end = parcel.readString();
    }

    OsisItem(String book) {
        this.book = book;
    }

    OsisItem(String book, int chapter) {
        this.book = book;
        this.chapter = String.valueOf(chapter);
    }

    OsisItem(String book, String chapter, String verse) {
        this.book = book;
        this.chapter = chapter;
        this.verse = verse;
    }

    OsisItem(String book, int chapter, int verse) {
        this.book = book;
        this.chapter = String.valueOf(chapter);
        this.verse = String.valueOf(verse);
    }

    OsisItem(String book, String chapter, String start, String end) {
        this.book = book;
        this.chapter = chapter;
        this.verse = start;
        this.end = end;
    }

    public static ArrayList<OsisItem> parseSearch(String s, Context context) {
        ArrayList<OsisItem> items = new ArrayList<OsisItem>();
        if (s == null) {
            return items;
        }

        s = s.replace("+", " ");
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
        s = s.replace("\uff1b", ";");
        s = s.replace("\ufe14", ";");
        s = s.replace("\ufe54", ";");
        s = s.replace("\uff0c", ",");
        s = s.replace("\ufe50", ",");
        s = s.replace("(", "");
        s = s.replace(")", "");
        s = s.replace("（", "");
        s = s.replace("）", "");
        s = s.replace("【", "");
        s = s.replace("】", "");
        s = s.replace("〖", "");
        s = s.replace("〗", "");

        Pattern p = Pattern.compile("\\s*(\\d*?\\s*?[^\\d\\s;]+)\\s*(\\d*):?(\\d*)\\s*?-?\\s*?(\\d*):?(\\d*);?");
        Matcher m = p.matcher(s);
        while (m.find()) {
            String book = m.group(1);
            // FIXME: dont support Gen 1-3,5,7
            if (book.startsWith(",")) {
                book = "";
            }
            String osis = Bible.getBible(context).getOsis(book);
            String start_chapter = m.group(2);
            String start_verse = m.group(3);
            String end_chapter = m.group(4);
            String end_verse = m.group(5);

            Log.d("OsisItem", String.format("book:%s, osis:%s, %s:%s-%s%s", book, osis, start_chapter, start_verse, end_chapter, end_verse));
            if (osis == null) {
                continue;
            }

            if ("".equals(start_chapter)) {
                items.add(new OsisItem(osis));
            // 3, 3:16, 3:16-17
            } else if ("".equals(end_chapter) || (!"".equals(start_verse) && "".equals(end_verse))) {
                items.add(new OsisItem(osis, start_chapter, start_verse, end_chapter));
            // 3-4, 3-4:5, 3:16-4:6
            } else if ("".equals(start_verse) || (!"".equals(start_verse) && !"".equals(end_verse))) {
                int start = Integer.parseInt(start_chapter);
                int end = Integer.parseInt(end_chapter);
                items.add(new OsisItem(osis, start_chapter, start_verse));
                for (int i = start + 1; i < end; i++) {
                    items.add(new OsisItem(osis, i));
                }
                if (end > start) {
                    items.add(new OsisItem(osis, end_chapter, end_verse.equals("") ? "" : "1", end_verse));
                }
            }
        }

        return items;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int falgs) {
        parcel.writeString(book);
        parcel.writeString(chapter);
        parcel.writeString(verse);
        parcel.writeString(end);
    }

    public static final Parcelable.Creator<OsisItem> CREATOR = new Parcelable.Creator<OsisItem>() {
        public OsisItem createFromParcel(Parcel parcel) {
            return new OsisItem(parcel);
        }

        public OsisItem[] newArray(int size) {
            return new OsisItem[size];
        }
    };
}
