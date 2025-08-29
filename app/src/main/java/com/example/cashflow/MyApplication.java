package com.example.cashflow;

import android.app.Application;
import com.example.cashflow.utils.ThemeManager;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved theme as soon as the app starts
        ThemeManager.applyTheme(this);
    }
}
