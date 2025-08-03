package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Objects;
import java.util.regex.Pattern;

public class Signup extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private static final int RC_SIGN_IN = 9001;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private ImageView btnGoogleSignUp;
    private ImageView togglePasswordVisibility;

    private boolean isPasswordVisible = false;

    // Removed: Bottom Navigation Buttons
    // private LinearLayout btnTransactions;
    // private LinearLayout btnHome;
    // private LinearLayout btnSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); // Full padding as no fixed bar
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);


        emailInput = findViewById(R.id.email);
        passwordInput = findViewById(R.id.password);
        confirmPasswordInput = findViewById(R.id.confirm_password);
        Button btnSignUp = findViewById(R.id.btnSignUp);
        TextView tvSignIn = findViewById(R.id.tvSignIn);
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);

        // Removed initialization of bottom nav buttons
        // btnTransactions = findViewById(R.id.btnTransactions);
        // btnHome = findViewById(R.id.btnHome);
        // btnSettings = findViewById(R.id.btnSettings);


        final Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+");
        final Pattern passwordPattern = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{4,}$");

        if (emailInput == null || passwordInput == null || confirmPasswordInput == null || btnSignUp == null ||
                tvSignIn == null || btnGoogleSignUp == null || togglePasswordVisibility == null) {
            Log.e(TAG, "onCreate: One or more core UI components not found. Check activity_signup.xml IDs.");
            Toast.makeText(this, "Application error: Missing core UI elements.", Toast.LENGTH_LONG).show();
            return;
        }


        btnSignUp.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
                Toast.makeText(Signup.this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!emailPattern.matcher(email).matches()) {
                emailInput.setError("Invalid email format");
                emailInput.requestFocus();
                return;
            }

            if (!passwordPattern.matcher(password).matches()) {
                passwordInput.setError("Password must be strong (A-Z, a-z, 0-9, special char)");
                passwordInput.requestFocus();
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordInput.setError("Passwords do not match");
                confirmPasswordInput.requestFocus();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmailAndPassword:success");
                            Toast.makeText(Signup.this, "Account created successfully", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(Signup.this, Signin.class));
                            finish();
                        } else {
                            Log.w(TAG, "createUserWithEmailAndPassword:failure", task.getException());
                            String errorMessage = "Error: " + Objects.requireNonNull(task.getException()).getMessage();
                            Toast.makeText(Signup.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvSignIn.setOnClickListener(v -> {
            Intent intent = new Intent(Signup.this, Signin.class);
            startActivity(intent);
            finish();
        });

        btnGoogleSignUp.setOnClickListener(v -> signInWithGoogle());

        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());

        // Removed bottom navigation button listeners
        // if (btnTransactions != null) { ... }
        // if (btnHome != null) { ... }
        // if (btnSettings != null) { ... }
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google sign in success, ID Token: " + account.getIdToken());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase Google sign in success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(Signup.this, "Signed in with Google: " + user.getEmail(), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Signup.this, HomePage.class));
                        finish();
                    } else {
                        Log.w(TAG, "Firebase Google sign in failed", task.getException());
                        Toast.makeText(Signup.this, "Google Sign-Up failed: " + Objects.requireNonNull(task.getException()).getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            confirmPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            confirmPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        passwordInput.setSelection(passwordInput.getText().length());
        confirmPasswordInput.setSelection(confirmPasswordInput.getText().length());
        isPasswordVisible = !isPasswordVisible;
    }
}