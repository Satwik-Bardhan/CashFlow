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

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Objects;
import java.util.regex.Pattern;

public class Signin extends AppCompatActivity {

    private static final String TAG = "SignInActivity";
    private static final int RC_SIGN_IN = 9001;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private EditText emailInput;
    private EditText passwordInput;
    private Button btnSignIn;
    private ImageView btnGoogleSignIn;
    private ImageView togglePasswordVisibility;

    private boolean isPasswordVisible = false;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{4,}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signin);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
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
        btnSignIn = findViewById(R.id.signinButton);
        Button btnGuest = findViewById(R.id.guestButton);
        TextView signUpText = findViewById(R.id.SignUp);
        TextView forgotPasswordText = findViewById(R.id.forgotPassword);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);


        if (emailInput == null || passwordInput == null || btnSignIn == null ||
                btnGuest == null || signUpText == null || forgotPasswordText == null ||
                btnGoogleSignIn == null || togglePasswordVisibility == null) {
            Log.e(TAG, "onCreate: One or more core UI components not found. Check activity_signin.xml IDs.");
            Toast.makeText(this, "Application error: Missing core UI elements.", Toast.LENGTH_LONG).show();
            return;
        }


        btnSignIn.setOnClickListener(v -> {
            Log.d(TAG, "Sign In button clicked.");
            attemptSignIn();
        });
        btnGuest.setOnClickListener(v -> {
            Log.d(TAG, "Guest button clicked.");
            continueAsGuest();
        });
        signUpText.setOnClickListener(v -> {
            Log.d(TAG, "Sign Up text clicked.");
            navigateToSignUp();
        });
        forgotPasswordText.setOnClickListener(v -> {
            Log.d(TAG, "Forgot Password text clicked.");
            attemptPasswordReset();
        });
        btnGoogleSignIn.setOnClickListener(v -> {
            Log.d(TAG, "Google Sign In button clicked.");
            signInWithGoogle();
        });

        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "onStart: User already signed in: " + currentUser.getEmail());
            navigateToHomePage(false);
        } else {
            Log.d(TAG, "onStart: No user currently signed in.");
        }
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
                        Toast.makeText(Signin.this, "Signed in with Google: " + user.getEmail(), Toast.LENGTH_SHORT).show();
                        navigateToHomePage(false);
                    } else {
                        Log.w(TAG, "Firebase Google sign in failed", task.getException());
                        Toast.makeText(Signin.this, "Google Sign-In failed: " + Objects.requireNonNull(task.getException()).getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void attemptSignIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        Log.d(TAG, "attemptSignIn: Attempting sign-in for email: " + (TextUtils.isEmpty(email) ? "[EMPTY]" : email));

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            Log.d(TAG, "attemptSignIn: Email is empty.");
            Toast.makeText(this, "Please enter email.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            emailInput.setError("Invalid email format");
            emailInput.requestFocus();
            Log.d(TAG, "attemptSignIn: Invalid email format.");
            Toast.makeText(this, "Invalid email format.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            Log.d(TAG, "attemptSignIn: Password is empty.");
            Toast.makeText(this, "Please enter password.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            passwordInput.setError("Weak password. Use A-Z, a-z, 0-9 & special char");
            passwordInput.requestFocus();
            Log.d(TAG, "attemptSignIn: Weak password format.");
            Toast.makeText(this, "Weak password format. Check requirements.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "attemptSignIn: Validation passed. Calling Firebase signInWithEmailAndPassword.");
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmailAndPassword:success");
                        Toast.makeText(Signin.this, "Sign in successful", Toast.LENGTH_SHORT).show();
                        navigateToHomePage(false);
                    } else {
                        Log.w(TAG, "signInWithEmailAndPassword:failure", task.getException());
                        String errorMessage;
                        try { throw Objects.requireNonNull(task.getException());
                        } catch (FirebaseAuthInvalidUserException e) { errorMessage = "No user found with this email.";
                        } catch (FirebaseAuthInvalidCredentialsException e) { errorMessage = "Invalid password. Please check your credentials.";
                        } catch (Exception e) { errorMessage = "Sign in failed: " + (TextUtils.isEmpty(e.getMessage()) ? "Check network or try again." : e.getMessage()); }
                        Toast.makeText(Signin.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        passwordInput.setSelection(passwordInput.getText().length());
        isPasswordVisible = !isPasswordVisible;
    }


    private void continueAsGuest() {
        Log.d(TAG, "Continuing as Guest");
        Toast.makeText(Signin.this, "Continuing as Guest", Toast.LENGTH_SHORT).show();
        navigateToHomePage(true);
    }

    private void navigateToSignUp() {
        Log.d(TAG, "Navigating to Sign Up");
        Intent intent = new Intent(Signin.this, Signup.class);
        startActivity(intent);
    }

    private void attemptPasswordReset() {
        String email = emailInput.getText().toString().trim();

        Log.d(TAG, "attemptPasswordReset: Attempting reset for email: " + (TextUtils.isEmpty(email) ? "[EMPTY]" : email));

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Enter your email to reset password");
            emailInput.requestFocus();
            Log.d(TAG, "attemptPasswordReset: Email is empty.");
            Toast.makeText(this, "Please enter email to reset password.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            emailInput.setError("Invalid email format");
            emailInput.requestFocus();
            Log.d(TAG, "attemptPasswordReset: Invalid email format.");
            Toast.makeText(this, "Invalid email format for reset.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "sendPasswordResetEmail:success");
                        Toast.makeText(Signin.this, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                    } else {
                        Log.w(TAG, "sendPasswordResetEmail:failure", task.getException());
                        String errorMessage = "Failed to send reset email: " + Objects.requireNonNull(task.getException()).getMessage();
                        Toast.makeText(Signin.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToHomePage(boolean isGuest) {
        Intent intent = new Intent(Signin.this, HomePage.class);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);
        finish();
    }
}