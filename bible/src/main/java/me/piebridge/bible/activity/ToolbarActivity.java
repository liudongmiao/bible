package me.piebridge.bible.activity;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import me.piebridge.GenuineActivity;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 16/7/23.
 */
public abstract class ToolbarActivity extends GenuineActivity {

    private TextView titleView;

    private static final String FINISHED = "finished";

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        setTheme();
        super.onCreate(savedInstanceState);
    }

    protected void setTheme() {
        ThemeUtils.setTheme(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setToolbar(findViewById(getToolbarActionbarId()));

    }

    protected void setToolbar(Toolbar toolbar) {
        if (toolbar != null) {
            titleView = toolbar.findViewById(getToolbarTitleId());
            setTitle(getTitle());
            setSupportActionBar(toolbar);
        }
    }

    protected int getToolbarActionbarId() {
        return R.id.toolbar_actionbar;
    }

    protected int getToolbarTitleId() {
        return R.id.toolbar_title;
    }

    @Override
    public void setTitle(CharSequence title) {
        if (titleView != null) {
            titleView.setText(title);
        } else {
            super.setTitle(title);
        }
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (hasFinished()) {
                    onSupportNavigateUp();
                } else {
                    finish();
                }
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected boolean hasFinished() {
        return getIntent().getBooleanExtra(FINISHED, false);
    }

    public Intent setFinished(Intent intent, boolean finished) {
        intent.putExtra(FINISHED, finished);
        return intent;
    }

    protected void showBack(boolean show) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(show);
        }
    }

    protected void updateTaskDescription(String label) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(label));
        }
    }

}
