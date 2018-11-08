package me.piebridge.payment;

import android.os.Looper;

/**
 * Created by thom on 2017/9/7.
 */
class PaymentPlayServiceConnection extends PlayServiceConnection {

    PaymentPlayServiceConnection(Looper looper, PaymentActivity paymentActivity) {
        super(MESSAGE_PAYMENT, looper, paymentActivity);
    }

}