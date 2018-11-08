package me.piebridge.payment;

import android.os.Looper;

/**
 * Created by thom on 2017/9/7.
 */
class ActivatePlayServiceConnection extends PlayServiceConnection {

    ActivatePlayServiceConnection(Looper looper, PaymentActivity paymentActivity) {
        super(MESSAGE_ACTIVATE, looper, paymentActivity);
    }

}