package me.piebridge.payment;

import java.util.UUID;

import me.piebridge.GenuineApplication;

public abstract class PaymentApplication extends GenuineApplication {

    private Object wxApi;

    private String wxState;

    private String wxCode;

    private String prepayId;

    public final String getWxState() {
        return wxState;
    }

    public final void setWxapi(Object api) {
        this.wxApi = api;
        this.wxCode = null;
        if (api != null) {
            this.wxState = UUID.randomUUID().toString();
        } else {
            this.wxState = null;
        }
    }

    public final Object getWxApi() {
        return this.wxApi;
    }

    public final void setWxCode(String code) {
        this.wxCode = code;
    }

    public final String getWxCode() {
        return this.wxCode;
    }

    public final void setPrepayId(String prepayId) {
        this.prepayId = prepayId;
    }

    public final String getPrepayId() {
        return this.prepayId;
    }

}
