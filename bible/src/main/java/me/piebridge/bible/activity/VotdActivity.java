package me.piebridge.bible.activity;

import android.text.TextUtils;

import java.util.List;

import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/11/8.
 */
public class VotdActivity extends AbstractPlanActivity {

    @Override
    protected String[] getPlan() {
        return getResources().getStringArray(R.array.votd);
    }

    protected void setItems(List<OsisItem> items) {
        for (OsisItem item : items) {
            if (TextUtils.isEmpty(item.verses)) {
                item.verses = item.forceVerses();
            }
            LogUtils.d("item: " + item + ", verses: " + item.verses);
        }
        super.setItems(items);
    }

}
