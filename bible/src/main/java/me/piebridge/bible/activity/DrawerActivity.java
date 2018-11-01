package me.piebridge.bible.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import me.piebridge.bible.R;

/**
 * Created by thom on 16/7/24.
 */
public abstract class DrawerActivity extends AbstractReadingActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final int DELAY = 250;

    private DrawerLayout drawer;

    private ActionBarDrawerToggle drawerToggle;

    private NavigationView navigation;

    protected void setupDrawer() {
        drawer = findViewById(R.id.drawer);
        if (drawerToggle == null) {
            drawerToggle = new ActionBarDrawerToggle(this, drawer, R.string.install,
                    R.string.uninstall);
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
            case R.id.menu_search:
                startActivity(new Intent(this, SearchActivity.class));
                break;
            case R.id.menu_settings:
                openSettings();
                break;
            case R.id.menu_download:
                startActivity(new Intent(this, VersionsActivity.class));
                break;
            default:
                break;
        }
    }

    protected void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
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
