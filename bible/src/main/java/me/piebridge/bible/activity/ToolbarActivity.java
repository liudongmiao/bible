package me.piebridge.bible.activity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import java.util.Locale;

import me.piebridge.GenuineActivity;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.LocaleUtils;
import me.piebridge.bible.utils.PreferencesUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 16/7/23.
 */
public abstract class ToolbarActivity extends GenuineActivity {

    private TextView titleView;

    private static final String FINISHED = "finished";

    private Locale locale;

    protected boolean recreated;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // TaskDescription since 5.X
            setTaskDescription(new ActivityManager.TaskDescription(label));
        }
    }

    @Override
    @CallSuper
    protected void attachBaseContext(Context base) {
        locale = LocaleUtils.getOverrideLocale(base);
        super.attachBaseContext(LocaleUtils.updateResources(base, locale));
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (LocaleUtils.isChanged(locale, this)) {
            recreate();
            recreated = true;
        }
    }

    @Override
    public void recreate() {
        forceRecreate();
    }

    public void forceRecreate() {
        // https://stackoverflow.com/a/3419987/3289354
        // recreate has no appropriate event
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        super.finish();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    public void updateLocale() {
        super.recreate();
    }

    public void updateTheme() {
        super.recreate();
    }

    public void togglePreference(String key) {
        SharedPreferences preferences = PreferencesUtils.getPreferences(this);
        boolean oldValue = preferences.getBoolean(key, false);
        preferences.edit().putBoolean(key, !oldValue).apply();
        super.recreate();
    }

}
