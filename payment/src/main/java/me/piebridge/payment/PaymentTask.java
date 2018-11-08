package me.piebridge.payment;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * Payment Task - show available payments
 * <p>
 * Created by thom on 2017/2/13.
 */
class PaymentTask extends AsyncTask<PaymentActivity.PaymentItem, PaymentActivity.PaymentItem, Void> {

    private final WeakReference<PaymentActivity> mReference;

    PaymentTask(PaymentActivity activity) {
        mReference = new WeakReference<>(activity);
    }

    @Override
    protected Void doInBackground(PaymentActivity.PaymentItem... params) {
        PaymentActivity activity = mReference.get();
        if (activity != null) {
            PackageManager packageManager = activity.getPackageManager();
            for (PaymentActivity.PaymentItem item : params) {
                ActivityInfo ai = packageManager.resolveActivity(item.intent, 0).activityInfo;
                item.label = ai.loadLabel(packageManager);
                item.icon = ai.loadIcon(packageManager);
                this.publishProgress(item);
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(PaymentActivity.PaymentItem... values) {
        PaymentActivity activity = mReference.get();
        if (activity != null) {
            PaymentActivity.PaymentItem item = values[0];
            ImageView imageView = item.imageView;
            imageView.setContentDescription(item.label);
            imageView.setImageDrawable(item.icon);
            imageView.setClickable(true);
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPostExecute(Void params) {
        PaymentActivity activity = mReference.get();
        if (activity != null) {
            activity.showPayment();
        }
    }

}