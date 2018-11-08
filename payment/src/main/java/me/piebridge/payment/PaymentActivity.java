package me.piebridge.payment;

import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.drawable.Drawable;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import me.piebridge.GenuineActivity;

/**
 * Payment activity, support wechat, play store
 * <p>
 * Created by thom on 2017/2/9.
 */
public abstract class PaymentActivity extends GenuineActivity implements View.OnClickListener {

    private static final byte[] SHA_EXPECTED = {-23, -73, -17, -27, 64, -2, -89, 121, 97, -67,
            59, -119, 71, 50, -47, -2, 119, 72, -48, 80};

    public static final String PACKAGE_PLAY = "com.android.vending";

    public static final String PACKAGE_WECHAT = "com.tencent.mm";

    private static final int REQUEST_PLAY_PAYMENT = 0x4122;

    private static final String FRAGMENT_PAYMENT_PROGRESS = "fragment_payment_progress";

    private View mPayment;
    private TextView mPaymentTip;

    private PlayServiceConnection activateConnection;

    private PlayServiceConnection paymentConnection;

    private List<String> mSkus;

    private volatile boolean mShowPayment = true;

    private volatile boolean stopped;

    @Override
    protected void onStart() {
        super.onStart();
        if (mPayment == null) {
            mPayment = findViewById(R.id.payment);
            mPaymentTip = findViewById(R.id.payment_tip);
        }
        updatePayment();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        PaymentApplication application = (PaymentApplication) getApplication();
        String prepayId = application.getPrepayId();
        String wxCode = application.getWxCode();
        if (!TextUtils.isEmpty(prepayId)) {
            new WechatCheckTask(this).execute(prepayId);
        } else if (!TextUtils.isEmpty(wxCode)) {
            new WechatLoginTask(this).execute(wxCode);
        } else {
            hidePaymentDialog();
        }
    }

    @Override
    protected void onStop() {
        unbindService();
        stopped = true;
        super.onStop();
    }

    public boolean isStopped() {
        return stopped;
    }

    public final void updatePayment() {
        if (acceptPayment()) {
            if (!usePlayCache()) {
                activatePlay();
            }
            if (!isPlayInstaller()) {
                activatePayment();
            }
        } else {
            showPayment(false);
        }
    }

    protected boolean usePlayCache() {
        return true;
    }

    public final void showPayment(boolean showPayment) {
        if (!mShowPayment && showPayment) {
            activatePlay();
        }
        mShowPayment = showPayment;
        showPayment();
    }

    void showPayment() {
        mPayment.setVisibility(mShowPayment ? View.VISIBLE : View.GONE);
    }

    private synchronized void activatePlay() {
        if (!isPlayAvailable()) {
            return;
        }
        showPlayCheck();
        HandlerThread thread = new HandlerThread("PaymentService");
        thread.start();
        unbindActivateService();
        activateConnection = new ActivatePlayServiceConnection(thread.getLooper(), this);
        Intent serviceIntent = new Intent(PlayServiceConnection.ACTION_BIND);
        serviceIntent.setPackage(PACKAGE_PLAY);
        try {
            if (!bindService(serviceIntent, activateConnection, Context.BIND_AUTO_CREATE)) {
                showPlay(null);
            }
        } catch (IllegalArgumentException e) {
            Log.w(getTag(), "Can't bind activateConnection", e);
        }
    }

    protected boolean isPlayAvailable() {
        return true;
    }

    private void unbindActivateService() {
        if (activateConnection != null) {
            try {
                unbindService(activateConnection);
            } catch (IllegalArgumentException e) {
                Log.w(getTag(), "Can't unbind activateConnection", e);
            }
            activateConnection = null;
        }
    }

    private void unbindPaymentService() {
        if (paymentConnection != null) {
            try {
                unbindService(paymentConnection);
            } catch (IllegalArgumentException e) {
                Log.w(getTag(), "Can't unbind paymentConnection", e);
            }
            paymentConnection = null;
        }
    }

    @Override
    @CallSuper
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.wechat) {
            payViaWechat();
        } else if (id == R.id.play) {
            payViaPlay();
        }
    }

    public synchronized void payViaPlay() {
        HandlerThread thread = new HandlerThread("PaymentService");
        thread.start();
        unbindPaymentService();
        paymentConnection = new PaymentPlayServiceConnection(thread.getLooper(), this);
        Intent serviceIntent = new Intent(PlayServiceConnection.ACTION_BIND);
        serviceIntent.setPackage(PACKAGE_PLAY);
        if (!bindService(serviceIntent, paymentConnection, Context.BIND_AUTO_CREATE)) {
            unbindService(paymentConnection);
        }
    }

    public void payViaWechat() {
        showPaymentDialog();
        new WechatPrepayTask(this).execute();
    }

    public void loginViaWechat() {
        showPaymentDialog();
        PaymentApplication application = (PaymentApplication) getApplication();
        WxApiActivity.login(application, getWxId());
    }

    @Override
    @CallSuper
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PLAY_PAYMENT && data != null) {
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
            if (verify(purchaseData, dataSignature)) {
                activatePlay();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected void startPaymentActivity(Intent intent, String type) {
        showPaymentDialog();
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            hidePaymentDialog();
        }
    }

    void hidePaymentDialog() {
        DialogFragment fragment = (DialogFragment) getFragmentManager()
                .findFragmentByTag(FRAGMENT_PAYMENT_PROGRESS);
        if (fragment != null && !isStopped()) {
            fragment.dismiss();
        }
    }

    private void showPaymentDialog() {
        if (!isStopped()) {
            new ProgressFragment().show(getFragmentManager(), FRAGMENT_PAYMENT_PROGRESS);
        }
    }

    protected void activatePayment() {
        Collection<PaymentItem> items = new ArrayList<>(0x1);
        if (findViewById(R.id.wechat) != null
                && WxApiActivity.init((PaymentApplication) getApplication(), getWxId()) != null) {
            checkPackage(items, R.id.wechat, PACKAGE_WECHAT);
        }
        if (!items.isEmpty()) {
            mPaymentTip.setText(R.string.payment);
            new PaymentTask(this).execute(items.toArray(new PaymentItem[items.size()]));
        }
    }

    private void checkPackage(Collection<PaymentItem> items, int resId, String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            PaymentItem item = new PaymentItem();
            item.intent = intent;
            item.imageView = findViewById(resId);
            item.imageView.setOnClickListener(this);
            if (items != null) {
                items.add(item);
            }
        }
    }

    protected abstract String getWxId();

    protected abstract NetworkResponse loginWechat(String wxCode);

    protected abstract void onWechatLogin(JSONObject login);

    protected abstract NetworkResponse makeWechatPrepay();

    protected abstract NetworkResponse checkWechatPayment(String prepayId);

    protected abstract void onWechatPayment(JSONObject wechat);

    protected abstract BigInteger getPlayModulus();

    protected boolean acceptPayment() {
        return true;
    }

    protected String getApplicationId() {
        return getPackageName();
    }

    protected final boolean isPlayInstaller() {
        return PACKAGE_PLAY.equals(getPackageManager().getInstallerPackageName(getApplicationId()));
    }

    @CallSuper
    public void showPlay(@Nullable Collection<String> purchased) {
        unbindActivateService();
        if (purchased == null) {
            if (isPlayInstaller()) {
                mPaymentTip.setText(R.string.payment_play_unavailable);
                mPayment.setVisibility(View.GONE);
            }
        } else if (canPayViaPlay(purchased)) {
            Collection<PaymentItem> items = new ArrayList<>(0x1);
            checkPackage(items, R.id.play, PACKAGE_PLAY);
            if (!items.isEmpty()) {
                mPaymentTip.setText(R.string.payment);
                new PaymentTask(this).execute(items.toArray(new PaymentItem[items.size()]));
            } else if (isPlayInstaller()) {
                mPaymentTip.setText(R.string.payment_play_unavailable);
                mPayment.setVisibility(View.GONE);
            }
        }
    }

    protected String getTag() {
        return "Payment";
    }

    @CallSuper
    public void showPlayCheck() {
        if (isPlayInstaller()) {
            mPaymentTip.setText(R.string.payment_play_checking);
            findViewById(R.id.play).setVisibility(View.GONE);
        }
    }

    protected List<String> getAllSkus() {
        List<String> skus = new ArrayList<>();
        for (int i = 0x1; i <= 0x3; ++i) {
            for (int j = 0; j < 0x5; ++j) {
                char a = (char) ('a' + j);
                skus.add("brevent" + i + a + "_" + i);
            }
        }
        return skus;
    }

    @NonNull
    protected List<String> getPlaySkus() {
        if (mSkus == null) {
            mSkus = getAllSkus();
        }
        return mSkus;
    }

    protected boolean canPayViaPlay(Collection<String> purchased) {
        if (mSkus == null) {
            mSkus = getAllSkus();
        }
        Iterator<String> iterator = mSkus.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (purchased.contains(next)) {
                iterator.remove();
            }
        }
        return !mSkus.isEmpty();
    }

    public synchronized void unbindService() {
        unbindActivateService();
        unbindPaymentService();
    }

    public void payViaPlay(IntentSender sender) {
        try {
            startIntentSenderForResult(sender, REQUEST_PLAY_PAYMENT, new Intent(), 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.w(getTag(), "Can't pay", e);
        }
    }

    public String getSku() {
        List<String> skus = new ArrayList<>(getPlaySkus());
        for (String sku : skus) {
            if (mSkus.contains(sku)) {
                return sku;
            }
        }
        return mSkus.get(0);
    }

    @NonNull
    public Collection<String> checkPurchased(List<String> data, List<String> sigs) {
        Collection<String> purchased = new ArraySet<>();
        if (isEmpty(data) || isEmpty(sigs)) {
            return purchased;
        }

        int size = data.size();
        if (size > sigs.size()) {
            size = sigs.size();
        }

        for (int i = 0; i < size; ++i) {
            String datum = data.get(i);
            String sig = sigs.get(i);
            if (verify(datum, sig)) {
                checkProductId(purchased, datum);
            } else {
                Log.w(getTag(), "cannot verify, data: " + datum + ", sig: " + sig);
            }
        }
        return purchased;
    }

    private void checkProductId(Collection<String> purchased, String datum) {
        try {
            JSONObject json = new JSONObject(datum);
            if (json.optInt("purchaseState", -1) == 0) {
                String productId = json.optString("productId");
                if (!TextUtils.isEmpty(productId)) {
                    purchased.add(productId);
                }
            } else {
                Log.w(getTag(), "invalid purchase state: " + datum);
            }
        } catch (JSONException e) {
            Log.w(getTag(), "Can't check productId from " + datum, e);
        }
    }

    private boolean verify(String data, String signature) {
        if (TextUtils.isEmpty(data) || TextUtils.isEmpty(signature)) {
            return false;
        }
        BigInteger exponent = BigInteger.valueOf(0x10001);
        BigInteger modulus = getPlayModulus();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(data.getBytes());
            byte[] digest = md.digest();
            byte[] key = Base64.decode(signature, Base64.DEFAULT);
            byte[] sign = new BigInteger(1, key).modPow(exponent, modulus).toByteArray();
            for (int i = digest.length - 1, j = sign.length - 1; i >= 0; --i, --j) {
                sign[j] ^= digest[i];
            }
            md.reset();
            md.update(sign);
            digest = md.digest();
            for (int i = digest.length - 1; i >= 0; --i) {
                if (digest[i] != SHA_EXPECTED[i]) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            Log.w(getTag(), "Can't verify", e);
        }
        return false;
    }

    private static boolean isEmpty(List<String> collection) {
        return collection == null || collection.isEmpty();
    }

    static class PaymentItem {
        Intent intent;
        Drawable icon;
        CharSequence label;
        ImageView imageView;
    }

}
