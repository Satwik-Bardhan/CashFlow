package com.example.cashflow;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // UI Elements
    private TextView userNameTextView, uidTextView;
    private LinearLayout profileSection, helpSupport, appSettings, yourProfile, aboutCashFlow, logoutSection;
    private Button deleteAccountButton;

    // Bottom Navigation
    private LinearLayout btnHome, btnTransactions, btnSettings;
    private ImageView iconHome, iconTransactions, iconSettings;
    private TextView textHome, textTransactions, textSettings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupClickListeners();
        loadUserProfile();
        updateBottomNavigationSelection(btnSettings);
    }

    private void initializeUI() {
        // Primary Settings
        profileSection = findViewById(R.id.profileSection);
        userNameTextView = findViewById(R.id.userName);
        uidTextView = findViewById(R.id.uidText);

        // General Settings
        helpSupport = findViewById(R.id.helpSupport);
        appSettings = findViewById(R.id.appSettings);
        yourProfile = findViewById(R.id.yourProfile);
        aboutCashFlow = findViewById(R.id.aboutCashFlow);

        // Account Actions
        logoutSection = findViewById(R.id.logout_section);
        deleteAccountButton = findViewById(R.id.delete_account_button);

        // Bottom Navigation
        btnHome = findViewById(R.id.btnHome);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnSettings = findViewById(R.id.btnSettings);
        iconHome = findViewById(R.id.iconHome);
        iconTransactions = findViewById(R.id.iconTransactions);
        iconSettings = findViewById(R.id.iconSettings);
        textHome = findViewById(R.id.textHome);
        textTransactions = findViewById(R.id.textTransactions);
        textSettings = findViewById(R.id.textSettings);
    }

    private void setupClickListeners() {
        // Back Button
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        // Profile Section
        profileSection.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));

        // App Settings
        appSettings.setOnClickListener(v ->
                startActivity(new Intent(this, AppSettingsActivity.class)));

        // Other sections (can be implemented later)
        helpSupport.setOnClickListener(v -> Toast.makeText(this, "Help & Support clicked", Toast.LENGTH_SHORT).show());
        yourProfile.setOnClickListener(v -> Toast.makeText(this, "Your Profile clicked", Toast.LENGTH_SHORT).show());
        aboutCashFlow.setOnClickListener(v -> Toast.makeText(this, "About Cash Flow clicked", Toast.LENGTH_SHORT).show());

        // Logout
        logoutSection.setOnClickListener(v -> showLogoutConfirmationDialog());

        // Delete Account
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountConfirmationDialog());

        // Bottom Navigation
        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomePage.class));
            finish();
        });
        btnTransactions.setOnClickListener(v -> {
            startActivity(new Intent(this, TransactionActivity.class));
            finish();
        });
        btnSettings.setOnClickListener(v -> Toast.makeText(this, "Already on Settings", Toast.LENGTH_SHORT).show());
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            uidTextView.setText("UID: " + userId.substring(0, 12) + "..."); // Show partial UID

            mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Users user = snapshot.getValue(Users.class);
                    if (user != null && user.getUserName() != null) {
                        userNameTextView.setText(user.getUserName());
                    } else {
                        // If name is not in database, use display name from auth (e.g., from Google sign-in)
                        userNameTextView.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "CashFlow User");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(SettingsActivity.this, "Failed to load user data.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Handle case where user is not logged in
            userNameTextView.setText("Guest User");
            uidTextView.setText("Not logged in");
        }
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(SettingsActivity.this, SigninActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("This action is permanent and cannot be undone. All your data will be erased. Are you sure you want to proceed?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteUserAccount();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            // First, delete user data from Realtime Database
            mDatabase.child("users").child(userId).removeValue().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Then, delete the user from Authentication
                    user.delete().addOnCompleteListener(deleteTask -> {
                        if (deleteTask.isSuccessful()) {
                            Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(SettingsActivity.this, SigninActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(this, "Failed to delete account. Please try logging in again.", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    Toast.makeText(this, "Failed to delete user data.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateBottomNavigationSelection(View selectedButton) {
        // Reset all buttons to default state
        iconHome.setColorFilter(Color.WHITE);
        textHome.setTextColor(Color.WHITE);
        iconTransactions.setColorFilter(Color.WHITE);
        textTransactions.setTextColor(Color.WHITE);
        iconSettings.setColorFilter(Color.WHITE);
        textSettings.setTextColor(Color.WHITE);

        // --- [FIXED] Use a hardcoded color value to avoid resource not found error ---
        int activeColor = Color.parseColor("#2196F3");

        // Set the selected button to active state
        if (selectedButton.getId() == R.id.btnHome) {
            iconHome.setColorFilter(activeColor);
            textHome.setTextColor(activeColor);
        } else if (selectedButton.getId() == R.id.btnTransactions) {
            iconTransactions.setColorFilter(activeColor);
            textTransactions.setTextColor(activeColor);
        } else if (selectedButton.getId() == R.id.btnSettings) {
            iconSettings.setColorFilter(activeColor);
            textSettings.setTextColor(activeColor);
        }
    }
}