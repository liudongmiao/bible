package me.piebridge.payment;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.tencent.mm.opensdk.constants.Build;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.modelpay.PayResp;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import org.json.JSONObject;

public class WxApiActivity extends AbstractActivity implements IWXAPIEventHandler {

    private static final String TAG = "WXAPI";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleWxapi();
    }

    private void handleWxapi() {
        IWXAPI api = (IWXAPI) ((PaymentApplication) getApplication()).getWxApi();
        if (api != null) {
            api.handleIntent(getIntent(), this);
        }
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWxapi();
    }

    @Override
    public void onReq(BaseReq req) {
        Bundle bundle = new Bundle();
        req.toBundle(bundle);
        Log.d(TAG, "req: " + bundle);
    }

    @Override
    public void onResp(BaseResp resp) {
        Bundle bundle = new Bundle();
        resp.toBundle(bundle);
        Log.d(TAG, "resp: " + bundle);
        PaymentApplication application = (PaymentApplication) getApplication();
        if (resp instanceof SendAuth.Resp) {
            SendAuth.Resp res = (SendAuth.Resp) resp;
            if (ObjectUtils.equals(application.getWxState(), res.state)) {
                application.setWxCode(res.code);
            }
        } else if (resp instanceof PayResp) {
            PayResp res = (PayResp) resp;
            if (res.errCode == 0) {
                application.setPrepayId(res.prepayId);
            }
        }
    }

    public static IWXAPI init(PaymentApplication application, String wxId) {
        try {
            ApplicationInfo applicationInfo = application.getPackageManager()
                    .getApplicationInfo(PaymentActivity.PACKAGE_WECHAT, 0);
            if (applicationInfo == null || !applicationInfo.enabled) {
                application.setWxapi(null);
                return null;
            }
        } catch (PackageManager.NameNotFoundException ignore) {
            application.setWxapi(null);
            return null;
        }
        if (TextUtils.isEmpty(wxId)) {
            return null;
        }
        IWXAPI api = (IWXAPI) application.getWxApi();
        if (api != null) {
            return api;
        }
        api = WXAPIFactory.createWXAPI(application, wxId, true);
        final int sdk = Build.LAUNCH_MINIPROGRAM_SUPPORTED_SDK_INT;
        if (api.isWXAppInstalled() && api.getWXAppSupportAPI() >= sdk) {
            application.setWxapi(api);
            return api;
        }
        api.detach();
        return null;
    }

    public static boolean login(PaymentApplication application, String wxId) {
        IWXAPI api = init(application, wxId);
        if (api != null) {
            final SendAuth.Req req = new SendAuth.Req();
            req.scope = "snsapi_userinfo";
            req.state = application.getWxState();
            return api.sendReq(req);
        } else {
            return false;
        }
    }

    public static boolean pay(PaymentApplication application, String wxId, JSONObject prepay) {
        IWXAPI api = init(application, wxId);
        if (api != null) {
            final PayReq req = new PayReq();
            req.appId = wxId;
            req.partnerId = prepay.optString("partnerid");
            req.prepayId = prepay.optString("prepayid");
            req.packageValue = prepay.optString("package");
            req.nonceStr = prepay.optString("noncestr");
            req.timeStamp = prepay.optString("timestamp");
            req.sign = prepay.optString("sign");
            return api.sendReq(req);
        } else {
            return false;
        }
    }

}
