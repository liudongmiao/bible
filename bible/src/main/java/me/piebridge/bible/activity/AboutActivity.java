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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.DonateFragment;
import me.piebridge.bible.utils.DeprecationUtils;
import me.piebridge.bible.utils.HideApiWrapper;
import me.piebridge.bible.utils.LogUtils;

public class AboutActivity extends ToolbarPaymentActivity {

    private static final String KEY_PLAY = String.valueOf(new char[] {
            'd', 'e', 'b', 'u', 'g', '.',
            'm', 'e', '.', 'p', 'i', 'e', 'b', 'r', 'i', 'd', 'g', 'e', '.', 'b', 'i', 'b', 'l', 'e', '.',
            'p', 'l', 'a', 'y'
    });

    private static final String KEY_GSM = String.valueOf(new char[] {
            'g', 's', 'm', '.',
            'o', 'p', 'e', 'r', 'a', 't', 'o', 'r', '.', 'n', 'u', 'm', 'e', 'r', 'i', 'c'
    });

    private static final String KEY_GSM_SIM = String.valueOf(new char[] {
            'g', 's', 'm', '.', 's', 'i', 'm', '.',
            'o', 'p', 'e', 'r', 'a', 't', 'o', 'r', '.', 'n', 'u', 'm', 'e', 'r', 'i', 'c'
    });

    static final String KEY_DONATE_SHOW = "donate_show";
    static final String KEY_DONATE_AMOUNT = "donate_amount";

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
        setTitle(getString(R.string.about_about));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.about, menu);
        MenuItem donate = menu.findItem(R.id.action_donate);
        MenuItem toggle = menu.findItem(R.id.action_toggle);
        if (shouldShowDonate()) {
            toggle.setTitle(R.string.about_donate_hide);
            donate.setVisible(mPlayAvailable && canPlay());
        } else {
            toggle.setTitle(R.string.about_donate_show);
            donate.setVisible(false);
        }
        return true;
    }

    private boolean shouldShowDonate() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(KEY_DONATE_SHOW, true);
    }

    private void toggleShowDonate() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showed = sharedPreferences.getBoolean(KEY_DONATE_SHOW, true);
        sharedPreferences.edit().putBoolean(KEY_DONATE_SHOW, !showed).apply();
        invalidateOptionsMenu();
        if (!showed && !mPlayAvailable) {
            super.showPayment(true);
        }
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
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showPlay(@Nullable Collection<String> purchased) {
        super.showPlay(purchased);
        LogUtils.d("showPlay, purchased: " + purchased);
        mPurchased.clear();
        if (purchased != null) {
            mPurchased.addAll(purchased);
            BibleApplication application = (BibleApplication) getApplication();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            int oldAmount = sharedPreferences.getInt(KEY_DONATE_AMOUNT, 0);
            int amount = application.getAmount();
            LogUtils.d("amount: " + amount);
            if (amount > 0 && oldAmount != amount) {
                String text = getString(R.string.about_donate_play, amount);
                LogUtils.d("toast: " + text);
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                sharedPreferences.edit().putInt(KEY_DONATE_AMOUNT, amount).apply();
            }
        }
        boolean playAvailable = purchased != null;
        if (playAvailable != mPlayAvailable) {
            mPlayAvailable = playAvailable;
            invalidateOptionsMenu();
        }
    }

    @Override
    protected boolean usePlayCache() {
        return false;
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

    @Override
    protected boolean isPlayAvailable() {
        return true;
    }

    private boolean canPlay() {
        if ("true".equals(HideApiWrapper.getProperty(KEY_PLAY, ""))) {
            return true;
        }
        return !HideApiWrapper.getProperty(KEY_GSM, "").startsWith("460")
                && !HideApiWrapper.getProperty(KEY_GSM_SIM, "").startsWith("460");
    }

}
