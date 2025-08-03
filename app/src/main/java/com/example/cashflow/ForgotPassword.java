package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView; // Added import for ImageView
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import java.util.Objects;
import java.util.regex.Pattern;

public class ForgotPassword extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";
    private FirebaseAuth mAuth;
    private EditText emailInput;
    private ImageView backButton; // New: Back button reference

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View rootLayout = findViewById(R.id.main_root_layout); // Changed to main_root_layout
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); // Full padding
                Log.d(TAG, "Insets applied.");
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_root_layout) not found. Insets might not be applied.");
            Toast.makeText(this, "Layout error: root view not found.", Toast.LENGTH_LONG).show();
        }

        mAuth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.email);
        Button resetPasswordBtn = findViewById(R.id.resetPasswordBtn);
        backButton = findViewById(R.id.backButton); // Initialize back button

        if (emailInput == null || resetPasswordBtn == null || backButton == null) { // Added null check for back button
            Log.e(TAG, "Missing UI components in activity_forgot_password.xml");
            Toast.makeText(this, "Error: Missing UI elements.", Toast.LENGTH_LONG).show();
            return;
        }

        resetPasswordBtn.setOnClickListener(v -> attemptPasswordReset());
        backButton.setOnClickListener(v -> finish()); // Set click listener for back button
    }

    private void attemptPasswordReset() {
        String email = emailInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            emailInput.setError("Invalid email format");
            emailInput.requestFocus();
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "sendPasswordResetEmail:success");
                        Toast.makeText(ForgotPassword.this,
                                "Password reset email sent. Check your inbox.",
                                Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(ForgotPassword.this, Signin.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Log.w(TAG, "sendPasswordResetEmail:failure", task.getException());
                        String errorMessage = "Failed to send reset email.";
                        try {
                            throw Objects.requireNonNull(task.getException());
                        } catch (FirebaseAuthInvalidUserException e) {
                            errorMessage = "No user found with this email.";
                        } catch (Exception e) {
                            errorMessage += " " + e.getMessage();
                            if (TextUtils.isEmpty(e.getMessage())) {
                                errorMessage = "Failed to send reset email. Check your network or email address.";
                            }
                        }
                        Toast.makeText(ForgotPassword.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}