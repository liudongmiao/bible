package me.piebridge.bible.activity;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import me.piebridge.bible.R;

/**
 * Created by thom on 16/7/23.
 */
public abstract class ToolbarActivity extends AppCompatActivity {

    private TextView titleView;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        Toolbar toolbar = (Toolbar) findViewById(getToolbarActionbarId());
        titleView = (TextView) toolbar.findViewById(getToolbarTitleId());
        setTitle(getTitle());
        setSupportActionBar(toolbar);
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

}
