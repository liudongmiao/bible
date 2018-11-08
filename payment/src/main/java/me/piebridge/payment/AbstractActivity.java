package me.piebridge.payment;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by thom on 2017/9/2.
 */
public abstract class AbstractActivity extends AppCompatActivity {

    private volatile boolean stopped;

    @Override
    protected void onStop() {
        stopped = true;
        super.onStop();
    }

    public boolean isStopped() {
        return stopped;
    }

}
