package me.piebridge.payment;

/**
 * Created by thom on 2018/6/29.
 */
public class WxApi {

    private WxApi() {

    }

    public static boolean isSupported(PaymentApplication application, String wxId) {
        return WxApiActivity.init(application, wxId) != null;
    }

    public static boolean login(PaymentApplication application, String wxId) {
        return WxApiActivity.login(application, wxId);
    }

}
