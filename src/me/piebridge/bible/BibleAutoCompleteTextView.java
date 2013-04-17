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

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.AutoCompleteTextView;

public class BibleAutoCompleteTextView extends AutoCompleteTextView {

    private boolean show = false;

    public BibleAutoCompleteTextView(Context context) {
        super(context);
    }

    public BibleAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BibleAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean enoughToFilter() {
        return true;
    }

    @Override
    public void showDropDown() {
        super.showDropDown();
        if (!show) {
            show = true;
            performFiltering(getText(), 0);
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused && show) {
            performFiltering(getText(), 0);
        } else if (!focused) {
            show = false;
        }
    }

}
