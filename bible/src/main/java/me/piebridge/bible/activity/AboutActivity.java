package me.piebridge.bible.activity;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import org.json.JSONObject;

import java.math.BigInteger;

import me.piebridge.bible.R;
import me.piebridge.bible.utils.DeprecationUtils;
import me.piebridge.payment.NetworkResponse;

public class AboutActivity extends ToolbarPaymentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        showBack(true);
        TextView textView = findViewById(R.id.about_detail);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setText(DeprecationUtils.fromHtmlLegacy(getString(R.string.about_detail)));
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.menu_about));
    }

    @Override
    protected String getWxId() {
        return null;
    }

    @Override
    protected NetworkResponse loginWechat(String wxCode) {
        return null;
    }

    @Override
    protected void onWechatLogin(JSONObject login) {

    }

    @Override
    protected NetworkResponse makeWechatPrepay() {
        return null;
    }

    @Override
    protected NetworkResponse checkWechatPayment(String prepayId) {
        return null;
    }

    @Override
    protected void onWechatPayment(JSONObject wechat) {

    }

    @Override
    protected BigInteger getPlayModulus() {
        return null;
    }

}
