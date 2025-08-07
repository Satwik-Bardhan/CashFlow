package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class Splash extends AppCompatActivity {
    // The delay time for the splash screen in milliseconds (e.g., 3000ms = 3s).
    private static final int SPLASH_DELAY = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide the action bar for a fullscreen experience.
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Use a Handler to delay the start of the next activity.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // This code runs after the SPLASH_DELAY.
            Intent intent = new Intent(Splash.this, MainActivity.class);
            startActivity(intent);

            // Finish this activity so the user can't navigate back to it.
            finish();
        }, SPLASH_DELAY);
    }
}
