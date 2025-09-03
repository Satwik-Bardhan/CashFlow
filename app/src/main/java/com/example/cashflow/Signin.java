package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cashflow.databinding.ActivitySigninBinding;
import com.example.cashflow.utils.ErrorHandler;
import com.example.cashflow.utils.ThemeManager;
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
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class Signin extends AppCompatActivity {

    private static final String TAG = "Signin";

    // NEW: ViewBinding declaration
    private ActivitySigninBinding binding;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private boolean isPasswordVisible = false;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // NEW: Apply theme before super.onCreate
        ThemeManager.applyTheme(this);

        super.onCreate(savedInstanceState);

        // NEW: Initialize ViewBinding
        binding = ActivitySigninBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();

        // NEW: Log activity start for analytics
        try {
            FirebaseCrashlytics.getInstance().log("Signin: Activity started");
        } catch (Exception e) {
            Log.d(TAG, "Crashlytics not available");
        }

        initializeGoogleSignIn();
        setupClickListeners();
        setupGoogleSignInLauncher();
        setupInitialState();
    }

    private void initializeGoogleSignIn() {
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Google Sign-In", e);
            ErrorHandler.handleAuthError(this, e);
        }
    }

    private void setupClickListeners() {
        // NEW: All findViewById replaced with binding
        binding.signinButton.setOnClickListener(v -> attemptEmailSignIn());
        binding.signUpText.setOnClickListener(v -> navigateToSignUp());
        binding.btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        binding.forgotPassword.setOnClickListener(v -> navigateToForgotPassword());
        binding.togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
        binding.guestButton.setOnClickListener(v -> continueAsGuest());
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
                            Log.e(TAG, "Google sign in failed", e);

                            // NEW: Enhanced error handling
                            String errorMsg = getGoogleSignInErrorMessage(e.getStatusCode());
                            showError(errorMsg);

                            try {
                                FirebaseCrashlytics.getInstance().recordException(e);
                            } catch (Exception crashlyticsError) {
                                Log.d(TAG, "Crashlytics not available for error reporting");
                            }
                        }
                    }
                });
    }

    private void setupInitialState() {
        // NEW: Check if user came from error recovery
        boolean fromErrorRecovery = getIntent().getBooleanExtra("error_recovery", false);
        if (fromErrorRecovery) {
            showError("Please sign in again to continue");
        }

        // NEW: Set initial loading state
        setLoadingState(false);
    }

    private void attemptEmailSignIn() {
        if (isLoading) return;

        // NEW: Get values using binding
        String email = binding.email.getText().toString().trim();
        String password = binding.password.getText().toString().trim();

        // NEW: Enhanced validation
        if (!validateInput(email, password)) {
            return;
        }

        setLoadingState(true);

        // NEW: Log sign-in attempt
        try {
            FirebaseCrashlytics.getInstance().log("Signin: Email sign-in attempted");
        } catch (Exception e) {
            Log.d(TAG, "Crashlytics not available");
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoadingState(false);

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "Email sign-in successful: " + (user != null ? user.getUid() : "null"));

                        // NEW: Set user info for Crashlytics
                        try {
                            if (user != null) {
                                FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                                crashlytics.setUserId(user.getUid());
                                crashlytics.setCustomKey("sign_in_method", "email");
                                crashlytics.log("Signin: Email authentication successful");
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Crashlytics not available");
                        }

                        navigateToHomePage();
                    } else {
                        Log.w(TAG, "Email sign-in failed", task.getException());

                        // NEW: Enhanced error messages
                        String errorMsg = getFirebaseAuthErrorMessage(task.getException());
                        showError(errorMsg);

                        try {
                            FirebaseCrashlytics.getInstance().recordException(task.getException());
                        } catch (Exception e) {
                            Log.d(TAG, "Crashlytics not available for error reporting");
                        }
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (isLoading) return;

        setLoadingState(true);

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoadingState(false);

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "Google sign-in successful: " + (user != null ? user.getUid() : "null"));

                        // NEW: Set user info for Crashlytics
                        try {
                            if (user != null) {
                                FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                                crashlytics.setUserId(user.getUid());
                                crashlytics.setCustomKey("sign_in_method", "google");
                                crashlytics.log("Signin: Google authentication successful");
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Crashlytics not available");
                        }

                        navigateToHomePage();
                    } else {
                        Log.w(TAG, "Google sign-in failed", task.getException());
                        showError("Google sign-in failed. Please try again.");

                        try {
                            FirebaseCrashlytics.getInstance().recordException(task.getException());
                        } catch (Exception e) {
                            Log.d(TAG, "Crashlytics not available for error reporting");
                        }
                    }
                });
    }

    private void signInWithGoogle() {
        if (isLoading) return;

        try {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching Google Sign-In", e);
            showError("Unable to start Google sign-in. Please try again.");
        }
    }

    private void navigateToHomePage() {
        Intent intent = new Intent(this, HomePage.class);
        intent.putExtra("isGuest", false);
        intent.putExtra("from_signin", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // NEW: Add smooth transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void navigateToSignUp() {
        Intent intent = new Intent(this, Signup.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private void navigateToForgotPassword() {
        Intent intent = new Intent(this, ForgotPassword.class);
        startActivity(intent);
    }

    private void continueAsGuest() {
        Intent intent = new Intent(this, HomePage.class);
        intent.putExtra("isGuest", true);
        intent.putExtra("from_signin", true);
        startActivity(intent);

        // NEW: Log guest mode for analytics
        try {
            FirebaseCrashlytics.getInstance().log("Signin: Guest mode selected");
        } catch (Exception e) {
            Log.d(TAG, "Crashlytics not available");
        }

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            // NEW: Hide password
            binding.password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            binding.togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            // NEW: Show password
            binding.password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            binding.togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        isPasswordVisible = !isPasswordVisible;

        // NEW: Keep cursor at the end
        binding.password.setSelection(binding.password.length());
    }

    // NEW: Enhanced validation with detailed error messages
    private boolean validateInput(String email, String password) {
        boolean isValid = true;

        // Clear previous errors
        binding.email.setError(null);
        binding.password.setError(null);

        // Email validation
        if (TextUtils.isEmpty(email)) {
            binding.email.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.email.setError("Please enter a valid email address");
            isValid = false;
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            binding.password.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            binding.password.setError("Password must be at least 6 characters");
            isValid = false;
        }

        return isValid;
    }

    // NEW: Enhanced loading state management
    private void setLoadingState(boolean loading) {
        isLoading = loading;

        // NEW: Disable/enable all interactive elements
        binding.email.setEnabled(!loading);
        binding.password.setEnabled(!loading);
        binding.signinButton.setEnabled(!loading);
        binding.btnGoogleSignIn.setEnabled(!loading);
        binding.guestButton.setEnabled(!loading);
        binding.signUpText.setEnabled(!loading);
        binding.forgotPassword.setEnabled(!loading);
        binding.togglePasswordVisibility.setEnabled(!loading);

        // NEW: Update button text to show loading
        if (loading) {
            binding.signinButton.setText("Signing In...");
            binding.loadingIndicator.setVisibility(View.VISIBLE);
        } else {
            binding.signinButton.setText("Sign In");
            binding.loadingIndicator.setVisibility(View.GONE);
        }
    }

    // NEW: Enhanced error message handling
    private String getFirebaseAuthErrorMessage(Exception exception) {
        if (exception == null) return "Authentication failed. Please try again.";

        String message = exception.getMessage();
        if (message == null) return "Authentication failed. Please try again.";

        // Convert Firebase error codes to user-friendly messages
        if (message.contains("invalid-email")) {
            return "Invalid email address format";
        } else if (message.contains("user-disabled")) {
            return "This account has been disabled";
        } else if (message.contains("user-not-found")) {
            return "No account found with this email";
        } else if (message.contains("wrong-password")) {
            return "Incorrect password";
        } else if (message.contains("too-many-requests")) {
            return "Too many failed attempts. Please try again later";
        } else if (message.contains("network-request-failed")) {
            return "Network error. Please check your connection";
        }

        return "Sign-in failed. Please check your credentials";
    }

    // NEW: Google Sign-In error handling
    private String getGoogleSignInErrorMessage(int statusCode) {
        switch (statusCode) {
            case 12501: // SIGN_IN_CANCELLED
                return "Google sign-in was cancelled";
            case 12502: // SIGN_IN_FAILED
                return "Google sign-in failed. Please try again";
            case 7: // NETWORK_ERROR
                return "Network error. Please check your connection";
            default:
                return "Google sign-in failed. Please try again";
        }
    }

    // NEW: Centralized error display
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Error shown to user: " + message);
    }

    // NEW: Critical memory leak prevention
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;

        try {
            FirebaseCrashlytics.getInstance().log("Signin: Activity destroyed");
        } catch (Exception e) {
            Log.d(TAG, "Crashlytics not available");
        }
    }
}
