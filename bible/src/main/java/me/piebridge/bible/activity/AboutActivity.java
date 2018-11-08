package me.piebridge.bible.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.DonateFragment;
import me.piebridge.bible.utils.DeprecationUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.payment.NetworkResponse;

public class AboutActivity extends ToolbarPaymentActivity {

    private static final String KEY_DONATE_SHOW = "donate_show";
    private static final String KEY_DONATE_AMOUNT = "donate_amount";

    private boolean mPlayAvailable;

    private String mSku;

    private Collection<String> mPurchased;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPurchased = new ArraySet<>();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        showBack(true);
        TextView textView = findViewById(R.id.about_detail);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setText(DeprecationUtils.fromHtmlLegacy(getString(R.string.about_detail)));
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.menu_about));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.about, menu);
        MenuItem donate = menu.findItem(R.id.action_donate);
        MenuItem toggle = menu.findItem(R.id.action_toggle);
        if (shouldShowDonate()) {
            toggle.setTitle(R.string.about_donate_hide);
            donate.setVisible(true);
            super.showPayment(true);
            return mPlayAvailable;
        } else {
            toggle.setTitle(R.string.about_donate_show);
            donate.setVisible(false);
            return true;
        }
    }

    private boolean shouldShowDonate() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(KEY_DONATE_SHOW, true);
    }

    private void toggleShowDonate() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showed = sharedPreferences.getBoolean(KEY_DONATE_SHOW, true);
        sharedPreferences.edit().putBoolean(KEY_DONATE_SHOW, !showed).apply();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_donate:
                new DonateFragment().show(getSupportFragmentManager(), "fragment-donate");
                return true;
            case R.id.action_toggle:
                toggleShowDonate();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
    public void showPlayCheck() {
        super.showPlayCheck();
        LogUtils.d("show play check");
    }

    @Override
    public void showPlay(@Nullable Collection<String> purchased) {
        super.showPlay(purchased);
        mPlayAvailable = purchased != null;
        invalidateOptionsMenu();
        LogUtils.d("showPlay, purchased: " + purchased);
        if (purchased != null) {
            mPurchased.addAll(purchased);
            invalidateOptionsMenu();
            int amount = 0;
            for (String s : purchased) {
                amount += parse(s);
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            int oldAmount = sharedPreferences.getInt(KEY_DONATE_AMOUNT, 0);
            LogUtils.d("amount: " + amount);
            if (oldAmount != amount) {
                String text = getString(R.string.about_donate_play, amount);
                LogUtils.d("toast: " + text);
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                sharedPreferences.edit().putInt(KEY_DONATE_AMOUNT, amount).apply();
            }
        }
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

    @Override
    protected boolean usePlayCache() {
        return false;
    }

    @Override
    protected List<String> getAllSkus() {
        List<String> skus = new ArrayList<>();
        int[] vv = new int[] {1, 3, 5};
        for (int v : vv) {
            skus.addAll(getSkus(v));
        }
        LogUtils.d("skus: " + skus);
        return skus;
    }

    private List<String> getSkus(int v) {
        List<String> skus = new ArrayList<>();
        for (int j = 0; j < 0x5; ++j) {
            char a = (char) ('a' + j);
            skus.add("bible" + v + a + "_" + v);
        }
        return skus;
    }

    @Override
    public String getSku() {
        return mSku;
    }

    @Override
    protected boolean acceptPayment() {
        return shouldShowDonate();
    }

    public void donate(int which) {
        int value = 0;
        switch (which) {
            case 0:
                value = 1;
                break;
            case 1:
                value = 3;
                break;
            case 2:
                value = 5;
                break;
            default:
                break;
        }
        mSku = null;
        if (value != 0) {
            List<String> skus = getSkus(value);
            for (String sku : skus) {
                if (!mPurchased.contains(sku)) {
                    mSku = sku;
                    break;
                }
            }
        }
        LogUtils.d("mSku: " + mSku);
        if (!TextUtils.isEmpty(mSku)) {
            super.payViaPlay();
        } else if (value != 0) {
            String text = getString(R.string.about_donate_of_stock, value);
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        }
    }

}
