package me.piebridge.bible.activity;

import android.view.Menu;
import android.view.MenuItem;

import me.piebridge.bible.R;

/**
 * Created by thom on 2018/11/12.
 */
public class PlanActivity extends AbstractPlanActivity {

    @Override
    protected String[] getPlan() {
        return getResources().getStringArray(R.array.plan);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.plan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_yesterday:
                loadData(-1);
                refreshAdapter();
                return true;
            case R.id.action_tomorrow:
                loadData(1);
                refreshAdapter();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
