package me.piebridge.payment;

import org.json.JSONObject;

/**
 * Created by thom on 2018/6/24.
 */
public class NetworkResponse {

    private final String error;

    private final JSONObject result;

    public NetworkResponse(String error, JSONObject result) {
        this.error = error;
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public JSONObject getResult() {
        return result;
    }

}
