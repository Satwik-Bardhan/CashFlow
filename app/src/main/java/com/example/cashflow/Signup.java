package com.example.cashflow;

import android.app.Activity;
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Signup extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private GoogleSignInClient mGoogleSignInClient;
    private EditText emailInput, passwordInput, confirmPasswordInput;
    private ImageView togglePasswordVisibility;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private boolean isPasswordVisible = false;

    // A placeholder for the Users class structure.
    // You should have a Users.java file with this structure.
    public static class Users {
        private String userId, mail, userName;

        public Users() {
            // Default constructor required for calls to DataSnapshot.getValue(Users.class)
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getMail() { return mail; }
        public void setMail(String mail) { this.mail = mail; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupGoogleSignIn();
        setupClickListeners();
        setupGoogleSignInLauncher();
    }

    private void initializeUI() {
        emailInput = findViewById(R.id.email);
        passwordInput = findViewById(R.id.password);
        confirmPasswordInput = findViewById(R.id.confirm_password); // Updated ID
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
    }

    private void setupClickListeners() {
        findViewById(R.id.btnSignUp).setOnClickListener(v -> attemptEmailSignUp()); // Updated ID
        findViewById(R.id.tvSignIn).setOnClickListener(v -> { // Updated ID
            startActivity(new Intent(this, Signin.class));
            finish();
        });
        findViewById(R.id.btnGoogleSignUp).setOnClickListener(v -> signInWithGoogle());
        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
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
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            firebaseAuthWithGoogle(account);
                        } catch (ApiException e) {
                            Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void attemptEmailSignUp() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        if (!validateInput(email, password, confirmPassword)) return;

        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (firebaseUser != null) {
                    saveNewUserToDatabase(firebaseUser);
                }
            } else {
                String message = "Sign up failed.";
                if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                    message = "This email address is already in use.";
                }
                Toast.makeText(Signup.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (firebaseUser != null) {
                    // Check if the user is new to save their data to the database
                    if (task.getResult().getAdditionalUserInfo().isNewUser()) {
                        saveNewUserToDatabase(firebaseUser);
                    } else {
                        navigateToHomePage();
                    }
                }
            } else {
                Toast.makeText(Signup.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveNewUserToDatabase(FirebaseUser firebaseUser) {
        Users newUser = new Users();
        newUser.setUserId(firebaseUser.getUid());
        newUser.setMail(firebaseUser.getEmail());
        // For email signup, display name might be null initially.
        // You might want to prompt the user to set a username later.
        newUser.setUserName(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");

        mDatabase.child("users").child(firebaseUser.getUid()).setValue(newUser)
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()) {
                        navigateToHomePage();
                    } else {
                        Toast.makeText(Signup.this, "Failed to save user data.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean validateInput(String email, String password, String confirmPassword) {
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address.");
            emailInput.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < MIN_PASSWORD_LENGTH) {
            passwordInput.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            passwordInput.requestFocus();
            return false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match.");
            confirmPasswordInput.requestFocus();
            return false;
        }
        return true;
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
        passwordInput.setSelection(passwordInput.length());
        confirmPasswordInput.setSelection(confirmPasswordInput.length());
        isPasswordVisible = !isPasswordVisible;
    }

    private void navigateToHomePage() {
        Intent intent = new Intent(this, HomePage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
}
