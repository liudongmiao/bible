package me.piebridge.bible.provider;

import android.content.SearchRecentSuggestionsProvider;

/**
 * Created by thom on 2018/10/22.
 */
public class SearchProvider extends SearchRecentSuggestionsProvider {

    public final static String AUTHORITY = "me.piebridge.bible.provider.search";
    public final static int MODE = DATABASE_MODE_QUERIES;

    public SearchProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }


}
