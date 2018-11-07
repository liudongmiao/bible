package me.piebridge.bible.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import me.piebridge.bible.R;
import me.piebridge.bible.fragment.FeedbackFragment;

/**
 * Created by thom on 16/7/24.
 */
public abstract class DrawerActivity extends ToolbarActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    static final int REQUEST_CODE_SETTINGS = 1190;

    private static final int DELAY = 250;

    private DrawerLayout drawer;

    private ActionBarDrawerToggle drawerToggle;

    private NavigationView navigation;

    protected void setupDrawer() {
        drawer = findViewById(R.id.drawer);
        if (drawerToggle == null) {
            drawerToggle = new ActionBarDrawerToggle(this, drawer, R.string.translation_install,
                    R.string.translation_uninstall);
            drawer.addDrawerListener(drawerToggle);
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        navigation = findViewById(R.id.navigation);
        navigation.setCheckedItem(R.id.menu_reading);
        navigation.setNavigationItemSelectedListener(this);

        updateOdb();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        drawer.closeDrawer(GravityCompat.START);
        drawer.postDelayed(new Runnable() {
            @Override
            public void run() {
                navigate(item.getItemId());
            }
        }, DELAY);
        return true;
    }

    protected void navigate(final int itemId) {
        switch (itemId) {
            case R.id.menu_reading:
                onSupportNavigateUp();
                break;
            case R.id.menu_search:
                startActivity(new Intent(this, SearchActivity.class));
                break;
            case R.id.menu_highlight:
                startActivity(new Intent(this, HighlightActivity.class));
                break;
            case R.id.menu_notes:
                startActivity(new Intent(this, NotesActivity.class));
                break;
            case R.id.menu_webview:
                startActivity(new Intent(this, WebViewActivity.class));
                break;
            case R.id.menu_settings:
                openSettings();
                break;
            case R.id.menu_download:
                startActivity(new Intent(this, VersionsActivity.class));
                break;
            case R.id.menu_feedback:
                new FeedbackFragment().show(getSupportFragmentManager(), "fragment-feedback");
                break;
            default:
                break;
        }
    }

    protected void openSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_CODE_SETTINGS);
    }

    @Override
    @CallSuper
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            updateOdb();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void updateOdb() {
        boolean odb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("odb", false);
        navigation.getMenu().setGroupVisible(R.id.odb, odb);
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerToggle != null) {
            drawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void setCheckedItem(@IdRes int id) {
        if (navigation != null) {
            navigation.setCheckedItem(id);
        }
    }

}
