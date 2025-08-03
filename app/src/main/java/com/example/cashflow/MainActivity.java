package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
            Log.w(TAG, "ActionBar found despite NoActionBar theme. Hiding it.");
        }

        View rootLayout = findViewById(R.id.main_scroll_view);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                Log.d(TAG, "Insets applied to root layout.");
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_scroll_view) not found. Insets might not be applied.");
        }

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.d(TAG, "No user logged in. Redirecting to Signup.");
            Intent intent = new Intent(MainActivity.this, Signup.class);
            startActivity(intent);
            finish();
        } else {
            Log.d(TAG, "User " + currentUser.getEmail() + " already logged in. Redirecting to HomePage.");
            Intent intent = new Intent(MainActivity.this, HomePage.class);
            intent.putExtra("isGuest", false);
            startActivity(intent);
            finish();
        }
    }
}