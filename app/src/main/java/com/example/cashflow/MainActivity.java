package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cashflow.utils.ErrorHandler;
import com.example.cashflow.utils.ThemeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // [FIXED] The redundant call to applyTheme has been removed.
        // The theme is already set by MyApplication.java before this activity starts.
        super.onCreate(savedInstanceState);

        Log.d(TAG, "MainActivity started - checking authentication status");

        try {
            FirebaseCrashlytics.getInstance().log("MainActivity: App startup initiated");
        } catch (Exception e) {
            Log.d(TAG, "Crashlytics not available");
        }

        checkAuthenticationAndNavigate();
    }

    private void checkAuthenticationAndNavigate() {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Intent navigationIntent;

            if (currentUser == null) {
                Log.d(TAG, "No authenticated user found - navigating to Sign-in");
                navigationIntent = new Intent(this, SigninActivity.class);
            } else {
                Log.d(TAG, "Authenticated user found: " + currentUser.getUid() + " - navigating to HomePage");
                navigationIntent = new Intent(this, HomePage.class);
                navigationIntent.putExtra("isGuest", false);

                try {
                    FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                    crashlytics.setUserId(currentUser.getUid());
                    crashlytics.setCustomKey("user_email", currentUser.getEmail() != null ? currentUser.getEmail() : "unknown");
                } catch (Exception e) {
                    Log.d(TAG, "Crashlytics not available");
                }
            }
            startActivity(navigationIntent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Log.e(TAG, "Error during authentication check", e);
            // Fallback to Sign-in on any error
            startActivity(new Intent(this, SigninActivity.class));
        } finally {
            finish();
        }
    }
}

