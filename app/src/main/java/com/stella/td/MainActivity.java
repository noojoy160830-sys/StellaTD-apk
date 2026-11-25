package com.stella.td;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        setContentView(view);
        hideBars();
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pauseGame();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!view.onBack()) super.onBackPressed();
    }
}
