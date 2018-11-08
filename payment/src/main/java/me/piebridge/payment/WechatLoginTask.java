package me.piebridge.payment;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * Created by thom on 2018/6/23.
 */
public class WechatLoginTask extends AsyncTask<String, Void, NetworkResponse> {
    private final WeakReference<PaymentActivity> mReference;

    WechatLoginTask(PaymentActivity paymentActivity) {
        mReference = new WeakReference<>(paymentActivity);
    }

    @Override
    protected NetworkResponse doInBackground(String... strings) {
        PaymentActivity paymentActivity = mReference.get();
        if (paymentActivity != null) {
            return paymentActivity.loginWechat(strings[0]);
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
            JSONObject login = response.getResult();
            if (login != null) {
                paymentActivity.onWechatLogin(login);
            }
            PaymentApplication application = (PaymentApplication) paymentActivity.getApplication();
            application.setWxCode(null);
            paymentActivity.hidePaymentDialog();
        }
    }

}
