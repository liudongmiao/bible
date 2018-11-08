package me.piebridge.payment;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * Created by thom on 2018/6/22.
 */
public class WechatCheckTask extends AsyncTask<String, Void, NetworkResponse> {

    private final WeakReference<PaymentActivity> mReference;

    WechatCheckTask(PaymentActivity paymentActivity) {
        mReference = new WeakReference<>(paymentActivity);
    }

    @Override
    protected NetworkResponse doInBackground(String... prepayIds) {
        PaymentActivity paymentActivity = mReference.get();
        if (paymentActivity != null) {
            return paymentActivity.checkWechatPayment(prepayIds[0]);
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
            JSONObject payment = response.getResult();
            if (payment != null) {
                paymentActivity.onWechatPayment(payment);
            }
            PaymentApplication application = (PaymentApplication) paymentActivity.getApplication();
            application.setPrepayId(null);
            paymentActivity.hidePaymentDialog();
        }
    }

}
