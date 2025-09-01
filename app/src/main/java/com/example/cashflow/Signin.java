package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class Signin extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private EditText emailInput, passwordInput;
    private ImageView togglePasswordVisibility;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        initializeUI();
        setupGoogleSignIn();
        setupClickListeners();
        setupGoogleSignInLauncher();
    }

    private void initializeUI() {
        emailInput = findViewById(R.id.email);
        passwordInput = findViewById(R.id.password);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
    }

    private void setupClickListeners() {
        findViewById(R.id.signinButton).setOnClickListener(v -> attemptEmailSignIn());
        findViewById(R.id.signUpText).setOnClickListener(v -> startActivity(new Intent(this, Signup.class)));
        findViewById(R.id.btnGoogleSignIn).setOnClickListener(v -> signInWithGoogle());
        findViewById(R.id.forgotPassword).setOnClickListener(v -> startActivity(new Intent(this, ForgotPassword.class)));
        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());

        // Guest Login Button
        findViewById(R.id.guestButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("isGuest", true); // Add flag for guest mode
            startActivity(intent);
            finish();
        });
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            firebaseAuthWithGoogle(account.getIdToken());
                        } catch (ApiException e) {
                            Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void attemptEmailSignIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required.");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                navigateToHomePage();
            } else {
                Toast.makeText(Signin.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                navigateToHomePage();
            } else {
                Toast.makeText(Signin.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void navigateToHomePage() {
        Intent intent = new Intent(this, HomePage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        isPasswordVisible = !isPasswordVisible;
        passwordInput.setSelection(passwordInput.length()); // Keep cursor at the end
    }
}
