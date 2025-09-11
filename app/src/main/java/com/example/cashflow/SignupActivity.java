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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText emailEditText, passwordEditText, confirmPasswordEditText;
    private Button signUpButton;
    private TextView signInText;
    private ImageView togglePasswordVisibility, toggleConfirmPasswordVisibility, backButton, helpButton;
    private CardView googleSignUpCard;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private DatabaseReference mDatabase;


    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();


        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // From your google-services.json
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        initializeUI();
        setupClickListeners();
    }

    private void initializeUI() {
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        confirmPasswordEditText = findViewById(R.id.confirm_password);
        signUpButton = findViewById(R.id.btnSignUp);
        signInText = findViewById(R.id.tvSignIn);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
        toggleConfirmPasswordVisibility = findViewById(R.id.toggleConfirmPasswordVisibility);
        googleSignUpCard = findViewById(R.id.googleSignUpCard);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    private void setupClickListeners() {
        // Sign up with email and password
        signUpButton.setOnClickListener(v -> createAccount());

        // Navigate to Sign In screen
        signInText.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, SigninActivity.class));
        });

        // Toggle password visibility
        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
        toggleConfirmPasswordVisibility.setOnClickListener(v -> toggleConfirmPasswordVisibility());

        // Sign up with Google
        googleSignUpCard.setOnClickListener(v -> signUpWithGoogle());

        // Back button
        backButton.setOnClickListener(v -> onBackPressed());

        // Help button
        helpButton.setOnClickListener(v -> {
            Toast.makeText(this, "Help button clicked", Toast.LENGTH_SHORT).show();
        });
    }

    private void createAccount() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required.");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required.");
            passwordEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters.");
            passwordEditText.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match.");
            confirmPasswordEditText.requestFocus();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(SignupActivity.this, "Account created.", Toast.LENGTH_SHORT).show();
                        updateUI(user);
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(SignupActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    }
                });
    }

    private void signUpWithGoogle() {
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
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign up failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                        if (isNewUser && user != null) {
                            Log.d(TAG, "New user signed up with Google.");
                            Users newUser = new Users();
                            newUser.setUserName(user.getDisplayName());
                            // This is the corrected line
                            newUser.setMail(user.getEmail());
                            mDatabase.child("users").child(user.getUid()).setValue(newUser);
                        }
                        updateUI(user);
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(SignupActivity.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    }
                });
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

    private void toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            confirmPasswordEditText.setTransformationMethod(new PasswordTransformationMethod());
            toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            confirmPasswordEditText.setTransformationMethod(null);
            toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        isConfirmPasswordVisible = !isConfirmPasswordVisible;
        confirmPasswordEditText.setSelection(confirmPasswordEditText.length());
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Intent intent = new Intent(SignupActivity.this, HomePage.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
}