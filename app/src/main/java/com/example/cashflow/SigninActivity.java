package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;

public class SigninActivity extends AppCompatActivity {

    private static final String TAG = "SigninActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText emailEditText, passwordEditText;
    private Button signinButton, guestButton;
    private TextView forgotPasswordText, signUpText;
    private ImageView togglePasswordVisibility, backButton, helpButton;
    private CardView googleSignInCard;
    private ProgressBar loadingIndicator;

    // --- [NEW] ViewModel instance ---
    private SigninViewModel viewModel;
    private GoogleSignInClient mGoogleSignInClient;

    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        // --- [MODIFIED] Initialize ViewModel ---
        viewModel = new ViewModelProvider(this).get(SigninViewModel.class);

        // Configure Google Sign In (UI part remains in Activity)
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        initializeUI();
        setupClickListeners();
        observeViewModel(); // --- [NEW] Start observing ViewModel changes ---
    }

    private void initializeUI() {
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        signinButton = findViewById(R.id.signinButton);

        forgotPasswordText = findViewById(R.id.forgotPassword);
        signUpText = findViewById(R.id.signUpText);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
        googleSignInCard = findViewById(R.id.googleSigninCard);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    // --- [NEW] Method to observe LiveData from ViewModel ---
    private void observeViewModel() {
        viewModel.user.observe(this, firebaseUser -> {
            if (firebaseUser != null) {
                updateUI(firebaseUser);
            }
        });

        viewModel.error.observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.loading.observe(this, isLoading -> {
            setLoading(isLoading);
        });
    }

    private void setupClickListeners() {
        // --- [MODIFIED] Call ViewModel methods instead of Firebase directly ---
        signinButton.setOnClickListener(v -> attemptEmailPasswordSignIn());
        guestButton.setOnClickListener(v -> viewModel.signInAnonymously());
        googleSignInCard.setOnClickListener(v -> signInWithGoogle());

        signUpText.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        forgotPasswordText.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
        backButton.setOnClickListener(v -> onBackPressed());
        helpButton.setOnClickListener(v -> Toast.makeText(this, "Help button clicked", Toast.LENGTH_SHORT).show());
    }

    private void attemptEmailPasswordSignIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required.");
            return;
        }

        // --- [MODIFIED] Delegate to ViewModel ---
        viewModel.signInWithEmail(email, password);
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    // --- [MODIFIED] Delegate to ViewModel ---
                    viewModel.firebaseAuthWithGoogle(account);
                }
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordEditText.setTransformationMethod(new PasswordTransformationMethod());
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            passwordEditText.setTransformationMethod(null);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        isPasswordVisible = !isPasswordVisible;
        passwordEditText.setSelection(passwordEditText.length());
    }

    private void setLoading(boolean isLoading) {
        loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        signinButton.setEnabled(!isLoading);
        guestButton.setEnabled(!isLoading);
        googleSignInCard.setEnabled(!isLoading);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Intent intent = new Intent(this, HomePage.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
}