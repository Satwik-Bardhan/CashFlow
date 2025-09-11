package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailEditText;
    private Button resetPasswordButton;
    private TextView backToLoginButton;
    private TextView emailValidationText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();

        // --- [FIXED] Correctly map the views using the IDs from your XML ---
        emailEditText = findViewById(R.id.emailEditText);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        backToLoginButton = findViewById(R.id.backToLoginButton);
        emailValidationText = findViewById(R.id.emailValidationText);

        // Pre-fill email if it was passed from another activity
        if (getIntent().hasExtra("email")) {
            emailEditText.setText(getIntent().getStringExtra("email"));
        }

        // --- Setup click listeners for all buttons ---
        resetPasswordButton.setOnClickListener(v -> attemptPasswordReset());

        // Listener for the back arrow icon in the header
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Listener for the "Sign In" text
        backToLoginButton.setOnClickListener(v -> {
            // Navigate back to the SigninActivity
            Intent intent = new Intent(ForgotPasswordActivity.this, SigninActivity.class);
            // Clear the activity stack to prevent looping back here
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void attemptPasswordReset() {
        String email = emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email address.");
            emailEditText.requestFocus();
            // Show the validation text message
            emailValidationText.setVisibility(View.VISIBLE);
            return;
        } else {
            // Hide validation message if email is valid
            emailValidationText.setVisibility(View.GONE);
        }

        // Show a progress indicator here if you have one

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    // Hide progress indicator
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, "Password reset email sent successfully.", Toast.LENGTH_LONG).show();
                        // Finish the activity and return to the login screen
                        finish();
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Failed to send reset email. Please check the address and try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }
}