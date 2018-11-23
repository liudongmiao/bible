package me.piebridge.bible.activity;

import me.piebridge.bible.R;

/**
 * Created by thom on 2018/11/12.
 */
public class PlanActivity extends AbstractPlanActivity {

    @Override
    protected String[] getPlan() {
        return getResources().getStringArray(R.array.plan);
    }

}
