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
        // NEW: Apply theme before super.onCreate to prevent UI flicker
        ThemeManager.applyTheme(this);

        super.onCreate(savedInstanceState);

        // This activity has no UI - it's purely a navigation decision maker
        Log.d(TAG, "MainActivity started - checking authentication status");

        // NEW: Log app startup for Crashlytics (if available)
        try {
            FirebaseCrashlytics.getInstance().log("MainActivity: App startup initiated");
        } catch (Exception e) {
            Log.d(TAG, "Crashlytics not available");
        }

        checkAuthenticationAndNavigate();
    }

    private void checkAuthenticationAndNavigate() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = auth.getCurrentUser();

            Intent navigationIntent;

            if (currentUser == null) {
                // No user is signed in - go to Sign-in screen
                Log.d(TAG, "No authenticated user found - navigating to Sign-in");

                navigationIntent = new Intent(this, Signin.class);
                navigationIntent.putExtra("from_launcher", true);

            } else {
                // User is already signed in - go to HomePage
                Log.d(TAG, "Authenticated user found: " + currentUser.getUid() + " - navigating to HomePage");

                navigationIntent = new Intent(this, HomePage.class);
                navigationIntent.putExtra("isGuest", false);
                navigationIntent.putExtra("from_launcher", true);
                navigationIntent.putExtra("user_id", currentUser.getUid());

                // NEW: Set user info for Crashlytics (if available)
                try {
                    FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                    crashlytics.setUserId(currentUser.getUid());
                    crashlytics.setCustomKey("user_email", currentUser.getEmail() != null ? currentUser.getEmail() : "unknown");
                    crashlytics.log("MainActivity: Navigating to HomePage (authenticated)");
                } catch (Exception e) {
                    Log.d(TAG, "Crashlytics not available");
                }
            }

            // Enhanced navigation with transition animations
            startActivity(navigationIntent);

            // Add smooth transition animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        } catch (Exception e) {
            // Comprehensive error handling
            Log.e(TAG, "Error during authentication check", e);

            try {
                FirebaseCrashlytics.getInstance().recordException(e);
            } catch (Exception crashlyticsError) {
                Log.d(TAG, "Crashlytics not available for error reporting");
            }

            ErrorHandler.handleAuthError(this, e);

            // Fallback navigation to Sign-in on any error
            Intent fallbackIntent = new Intent(this, Signin.class);
            fallbackIntent.putExtra("error_recovery", true);
            startActivity(fallbackIntent);

        } finally {
            // Finish this activity so user can't navigate back to it
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity destroyed");
    }
}
