package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class Splash extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000; // 3 seconds
    private ProgressBar splashProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide the action bar for fullscreen experience
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize and show progress bar
        splashProgress = findViewById(R.id.splash_progress);
        splashProgress.setVisibility(View.VISIBLE);

        // Use Handler to delay launching the next activity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Hide progress bar before transitioning (optional)
            splashProgress.setVisibility(View.GONE);

            // Start MainActivity
            Intent intent = new Intent(Splash.this, MainActivity.class);
            startActivity(intent);

            // Finish splash activity
            finish();
        }, SPLASH_DELAY);
    }
}
