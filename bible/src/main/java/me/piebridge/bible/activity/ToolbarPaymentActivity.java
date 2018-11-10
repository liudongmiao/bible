package me.piebridge.bible.activity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.LocaleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.PreferencesUtils;
import me.piebridge.bible.utils.ThemeUtils;
import me.piebridge.payment.NetworkResponse;
import me.piebridge.payment.PaymentActivity;

/**
 * Created by thom on 16/7/23.
 */
public abstract class ToolbarPaymentActivity extends PaymentActivity {

    private TextView titleView;

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

    protected void showBack(boolean show) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(show);
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

    @Override
    protected String getWxId() {
        return null;
    }

    @Override
    protected NetworkResponse loginWechat(String wxCode) {
        return null;
    }

    @Override
    protected void onWechatLogin(JSONObject login) {

    }

    @Override
    protected NetworkResponse makeWechatPrepay() {
        return null;
    }

    @Override
    protected NetworkResponse checkWechatPayment(String prepayId) {
        return null;
    }

    @Override
    protected void onWechatPayment(JSONObject wechat) {

    }

    @Override
    protected BigInteger getPlayModulus() {
        return new BigInteger(1, BuildConfig.PLAY);
    }

    @Override
    protected boolean usePlayCache() {
        BibleApplication application = (BibleApplication) getApplication();
        return application.getAmount() >= 0;
    }

    @Override
    public void showPlay(@Nullable Collection<String> purchased) {
        super.showPlay(purchased);
        LogUtils.d("purchased: " + purchased);
        BibleApplication application = (BibleApplication) getApplication();
        int amount = 0;
        if (purchased != null) {
            for (String s : purchased) {
                amount += parse(s);
            }
        }
        application.setAmount(amount);
    }

    static int parse(String p) {
        int i = p.indexOf('_');
        if (i > 0) {
            String t = p.substring(i + 1);
            if (t.length() > 0) {
                try {
                    return Integer.parseInt(t);
                } catch (NumberFormatException ignore) {
                    // do nothing
                }
            }
        }
        return 0;
    }

}
