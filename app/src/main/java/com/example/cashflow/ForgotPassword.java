package com.example.cashflow;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPassword extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        emailInput = findViewById(R.id.email);
        Button resetPasswordBtn = findViewById(R.id.resetPasswordBtn);

        // Pre-fill email if it was passed from another activity
        if (getIntent().hasExtra("email")) {
            emailInput.setText(getIntent().getStringExtra("email"));
        }

        resetPasswordBtn.setOnClickListener(v -> attemptPasswordReset());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private void attemptPasswordReset() {
        String email = emailInput.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address.");
            emailInput.requestFocus();
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPassword.this, "Password reset email sent.", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(ForgotPassword.this, "Failed to send reset email. Please check the address.", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
