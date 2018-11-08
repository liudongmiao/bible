package me.piebridge.payment;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * Created by thom on 2018/6/22.
 */
public class WechatPrepayTask extends AsyncTask<Void, Void, NetworkResponse> {

    private final WeakReference<PaymentActivity> mReference;

    WechatPrepayTask(PaymentActivity paymentActivity) {
        mReference = new WeakReference<>(paymentActivity);
    }

    @Override
    protected NetworkResponse doInBackground(Void... voids) {
        PaymentActivity paymentActivity = mReference.get();
        if (paymentActivity != null) {
            return paymentActivity.makeWechatPrepay();
        } else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(NetworkResponse response) {
        PaymentActivity paymentActivity = mReference.get();
        if (paymentActivity != null) {
            String error = response.getError();
            if (!TextUtils.isEmpty(error)) {
                Toast.makeText(paymentActivity, error, Toast.LENGTH_LONG).show();
            }
            JSONObject prepay = response.getResult();
            PaymentApplication application = (PaymentApplication) paymentActivity.getApplication();
            if (prepay == null || !WxApiActivity.pay(application, paymentActivity.getWxId(), prepay)) {
                paymentActivity.hidePaymentDialog();
            }
        }
    }

}
