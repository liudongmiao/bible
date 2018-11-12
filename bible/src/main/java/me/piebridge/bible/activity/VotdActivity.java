package me.piebridge.bible.activity;

import me.piebridge.bible.R;

/**
 * Created by thom on 2018/11/8.
 */
public class VotdActivity extends AbstractPlanActivity {

    @Override
    protected String[] getPlan() {
        return getResources().getStringArray(R.array.votd);
    }

}
