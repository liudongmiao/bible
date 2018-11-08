package me.piebridge.payment;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by thom on 2017/2/17.
 */
abstract class PlayServiceConnection extends Handler implements ServiceConnection {

    static final int MESSAGE_ACTIVATE = 0;

    static final int MESSAGE_PAYMENT = 1;

    private static final int MESSAGE_CHECK = 2;

    private static final int DELAY = 1000;

    private static final int VERSION = 0x3;

    private static final String TYPE = "inapp";

    static final String ACTION_BIND = "com.android.vending.billing.InAppBillingService.BIND";

    private final WeakReference<PaymentActivity> mReference;

    private final String mPackageName;

    private IInAppBillingService mInApp;

    private final Object lock = new Object();

    private Handler uiHandler;

    private final int mType;

    private final String mSku;

    private final String mTag;

    PlayServiceConnection(int type, Looper looper, PaymentActivity paymentActivity) {
        super(looper);
        mType = type;
        mTag = paymentActivity.getTag();
        mReference = new WeakReference<>(paymentActivity);
        mPackageName = paymentActivity.getApplicationId();
        uiHandler = new UiHandler(paymentActivity);
        mSku = mType == MESSAGE_PAYMENT ? paymentActivity.getSku() : null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        synchronized (lock) {
            mInApp = IInAppBillingService.Stub.asInterface(service);
        }
        obtainMessage(mType, mSku).sendToTarget();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        synchronized (lock) {
            mInApp = null;
        }
        getLooper().quit();
    }

    boolean isConnected() {
        synchronized (lock) {
            return mInApp != null;
        }
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case MESSAGE_ACTIVATE:
                activate();
                break;
            case MESSAGE_PAYMENT:
                pay((String) message.obj);
                break;
        }
    }

    private void pay(String sku) {
        try {
            Bundle bundle = mInApp.getBuyIntent(VERSION, mPackageName, sku, TYPE, null);
            PendingIntent intent = bundle.getParcelable("BUY_INTENT");
            PaymentActivity paymentActivity = mReference.get();
            if (paymentActivity != null && intent != null) {
                uiHandler.obtainMessage(MESSAGE_PAYMENT, intent.getIntentSender()).sendToTarget();
            }
        } catch (RemoteException e) {
            Log.w(mTag, "Can't getBuyIntent", e);
        }
    }

    private void activate() {
        try {
            Collection<String> purchased = null;
            uiHandler.sendEmptyMessageDelayed(MESSAGE_CHECK, DELAY);
            synchronized (lock) {
                if (mInApp != null && mInApp.isBillingSupported(VERSION, mPackageName, TYPE) == 0) {
                    Bundle inapp = mInApp.getPurchases(VERSION, mPackageName, TYPE, null);
                    purchased = checkPurchased(inapp);
                }
            }
            uiHandler.removeMessages(MESSAGE_CHECK);
            uiHandler.obtainMessage(MESSAGE_ACTIVATE, purchased).sendToTarget();
        } catch (RemoteException e) {
            Log.w(mTag, "Can't check Play", e);
        }
    }

    private Collection<String> checkPurchased(Bundle bundle) {
        List<String> data = bundle.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
        List<String> sigs = bundle.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
        PaymentActivity paymentActivity = mReference.get();
        if (paymentActivity == null || data == null || sigs == null) {
            return Collections.emptyList();
        }
        Collection<String> purchased = paymentActivity.checkPurchased(data, sigs);
        if (ObjectUtils.equals(purchased, data)) {
            for (String token : sigs) {
                try {
                    int consumed = mInApp.consumePurchase(VERSION, mPackageName, token);
                    if (consumed != 0) {
                        Log.w(mTag, "Can't consume " + token + ": " + consumed);
                    }
                } catch (RemoteException e) {
                    Log.w(mTag, "Can't consume " + token, e);
                }
            }
        }
        return purchased;
    }

    private static class UiHandler extends Handler {

        private final WeakReference<PaymentActivity> mReference;

        UiHandler(PaymentActivity paymentActivity) {
            super(paymentActivity.getMainLooper());
            mReference = new WeakReference<>(paymentActivity);
        }

        @Override
        public void handleMessage(Message message) {
            PaymentActivity paymentActivity = mReference.get();
            if (paymentActivity != null) {
                switch (message.what) {
                    case MESSAGE_ACTIVATE:
                        paymentActivity.showPlay((Collection<String>) message.obj);
                        break;
                    case MESSAGE_PAYMENT:
                        IntentSender sender = (IntentSender) message.obj;
                        paymentActivity.payViaPlay(sender);
                        break;
                    case MESSAGE_CHECK:
                        paymentActivity.showPlayCheck();
                        break;
                }
            }
        }

    }
}
