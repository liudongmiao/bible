package me.piebridge.bible.activity;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.collection.SimpleArrayMap;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.LocaleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;

/**
 * Created by thom on 2018/11/8.
 */
public abstract class AbstractPlanActivity extends ReadingItemsActivity {

    SimpleArrayMap<Integer, String> mPlans;

    private Calendar mCalendar;

    private TextView mDate;

    private int mForcePosition;

    @Override
    protected void initializeHeader(View header) {
        super.initializeHeader(header);
        mDate = header.findViewById(R.id.plan_date);
        updateDate();
    }

    @Override
    protected int getContentLayout() {
        return R.layout.activity_plan;
    }

    @Override
    protected void initItems() {
        mPlans = new SimpleArrayMap<>();
        for (String plan : getPlan()) {
            String key = plan.substring(0, 4);
            String value = plan.substring(5);
            mPlans.put(NumberUtils.parseInt(key), value);
        }
        mCalendar = Calendar.getInstance(Locale.US);
        loadData(0);
    }

    protected abstract String[] getPlan();

    protected void loadData(int delta) {
        mCalendar.add(Calendar.DATE, delta);
        int month = mCalendar.get(Calendar.MONTH) + 1;
        int day = mCalendar.get(Calendar.DAY_OF_MONTH);
        int key = month * 100 + day;
        String reading = mPlans.get(key);
        LogUtils.d("month: " + month + ", day: " + day + ", reading: " + reading);
        BibleApplication application = (BibleApplication) getApplication();
        ArrayList<OsisItem> osisItems = OsisItem.parseSearch(reading, application);
        setItems(osisItems);

        updateDate();
        mForcePosition = 0;
    }

    private void updateDate() {
        Locale locale = LocaleUtils.getOverrideLocale(this);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getString(R.string.plan_date_pattern), locale);
        String text = simpleDateFormat.format(mCalendar.getTime());
        if (mDate != null) {
            LogUtils.d("text: " + text);
            mDate.setText(text);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setCheckedItem(R.id.menu_plan);
    }

    @Override
    protected void refreshAdapterOnWork() {
        if (mForcePosition > -1) {
            refreshAdapterOnWork(mForcePosition);
            mForcePosition = -1;
        } else {
            super.refreshAdapterOnWork();
        }
    }

    @Override
    protected void refreshAdapterOnMain(int position, int count) {
        super.refreshAdapterOnMain(position, count);
    }

    @Override
    protected void onStop() {
        super.saveOsis();
        super.onStop();
    }

    @Override
    public void finish() {
        setResult();
        super.finish();
    }

    @Override
    protected void setResult() {
        Intent data = new Intent();
        data.putExtra(OSIS, getCurrentOsis());
        LogUtils.d("set result, data: " + data.getExtras());
        setResult(RESULT_OK, data);
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
