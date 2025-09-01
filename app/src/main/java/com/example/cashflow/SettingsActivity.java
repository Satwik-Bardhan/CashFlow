package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cashflow.databinding.ActivitySettingsBinding;
import com.example.cashflow.databinding.LayoutBottomNavigationBinding;
import com.example.cashflow.utils.ErrorHandler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // NEW: ViewBinding declarations
    private ActivitySettingsBinding binding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ValueEventListener userProfileListener;
    private ValueEventListener cashbookNameListener;
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NEW: Initialize ViewBinding
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // NEW: Initialize bottom navigation binding from included layout
        bottomNavBinding = LayoutBottomNavigationBinding.bind(binding.bottomNavBar.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            currentCashbookId = prefs.getString("active_cashbook_id_" + currentUser.getUid(), null);
        }

        initializeUI();
        setupClickListeners();

        // NEW: Access bottom navigation through bottomNavBinding
        bottomNavBinding.btnSettings.setSelected(true);
    }

    private void initializeUI() {
        // NEW: Using binding for all TextView updates with bold titles
        setTextWithBoldTitle(binding.helpSupport, "Help & Support", "FAQ, Contact us");
        setTextWithBoldTitle(binding.appSettings, "App Settings", "Language, Theme, Security, Backup");
        setTextWithBoldTitle(binding.yourProfile, "Your Profile", "Name, Mobile Number, Email");
        setTextWithBoldTitle(binding.aboutCashFlow, "About Cash Flow", "Privacy Policy, T&C, About us");

        // Set initial data location text
        binding.dataLocation.setText("Firebase Cloud Storage");
    }

    private void setupClickListeners() {
        // NEW: All findViewById replaced with binding

        // Navigation
        binding.backButton.setOnClickListener(v -> finish());

        // Profile management
        binding.editButton.setOnClickListener(v -> openEditProfile());
        binding.yourProfile.setOnClickListener(v -> openEditProfile());

        // Cashbook management
        binding.saveCashbookNameButton.setOnClickListener(v -> saveCashbookName());

        // App sections
        binding.appSettings.setOnClickListener(v -> openAppSettings());
        binding.helpSupport.setOnClickListener(v -> openHelpSupport());
        binding.aboutCashFlow.setOnClickListener(v -> openAboutApp());

        // Account actions
        binding.logoutSection.setOnClickListener(v -> showLogoutConfirmation());
        binding.deleteAccountButton.setOnClickListener(v -> showDeleteAccountConfirmation());

        // Bottom navigation setup
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        // NEW: Using bottomNavBinding for navigation
        bottomNavBinding.btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomePage.class));
            finish();
        });

        bottomNavBinding.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        bottomNavBinding.btnSettings.setOnClickListener(v ->
                Toast.makeText(this, "Already on Settings", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForUserProfile(currentUser.getUid());
            if (currentCashbookId != null) {
                startListeningForCashbookName(currentUser.getUid(), currentCashbookId);
            } else {
                // NEW: Using binding for UI updates
                binding.editCashbookName.setText("No Active Cashbook");
                binding.editCashbookName.setEnabled(false);
            }
        } else {
            // Guest mode
            binding.userName.setText("Guest User");
            binding.uidText.setText("UID: GUEST");
            binding.dataLocation.setText("Local Device Storage");
            binding.createdDate.setText("Guest Session");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    // NEW: Critical memory leak prevention
    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeFirebaseListeners();
        binding = null;
        bottomNavBinding = null;
    }

    private void startListeningForUserProfile(String userId) {
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
        }

        userProfileListener = new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Users userProfile = dataSnapshot.getValue(Users.class);
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (userProfile != null) {
                    // NEW: Using binding for all UI updates
                    binding.userName.setText(userProfile.getUserName());
                    binding.uidText.setText("UID: " + userProfile.getUserId());
                    binding.dataLocation.setText("Firebase Cloud Storage");

                    if (firebaseUser != null && firebaseUser.getMetadata() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        binding.createdDate.setText("Created: " +
                                sdf.format(new Date(firebaseUser.getMetadata().getCreationTimestamp())));
                    }
                } else {
                    // Handle case where user profile doesn't exist
                    if (firebaseUser != null) {
                        binding.userName.setText(firebaseUser.getEmail());
                        binding.uidText.setText("UID: " + firebaseUser.getUid());
                        binding.dataLocation.setText("Firebase Cloud Storage");
                        binding.createdDate.setText("Profile Not Found");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // NEW: Proper error handling
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };
        mDatabase.child("users").child(userId).addValueEventListener(userProfileListener);
    }

    private void startListeningForCashbookName(String userId, String cashbookId) {
        if (cashbookNameListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(cashbookId).removeEventListener(cashbookNameListener);
        }

        cashbookNameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                CashbookModel cashbook = dataSnapshot.getValue(CashbookModel.class);
                if (cashbook != null) {
                    // NEW: Using binding
                    binding.editCashbookName.setText(cashbook.getName());
                } else {
                    binding.editCashbookName.setText("Cashbook Not Found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // NEW: Proper error handling
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };
        mDatabase.child("users").child(userId).child("cashbooks").child(cashbookId).addValueEventListener(cashbookNameListener);
    }

    private void saveCashbookName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentCashbookId == null) {
            Toast.makeText(this, "Cannot save: No active cashbook", Toast.LENGTH_SHORT).show();
            return;
        }

        // NEW: Using binding for input validation
        String newName = binding.editCashbookName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            binding.editCashbookName.setError("Name cannot be empty");
            return;
        }

        if (newName.length() < 2) {
            binding.editCashbookName.setError("Name too short (minimum 2 characters)");
            return;
        }

        if (newName.length() > 50) {
            binding.editCashbookName.setError("Name too long (maximum 50 characters)");
            return;
        }

        // Show loading state
        binding.saveCashbookNameButton.setEnabled(false);

        mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("name").setValue(newName)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Cashbook name saved successfully!", Toast.LENGTH_SHORT).show();
                    binding.saveCashbookNameButton.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    ErrorHandler.handleExportError(this, e);
                    binding.saveCashbookNameButton.setEnabled(true);
                });
    }

    private void openEditProfile() {
        startActivity(new Intent(this, EditProfileActivity.class));
    }

    private void openAppSettings() {
        startActivity(new Intent(this, AppSettingsActivity.class));
    }

    private void openHelpSupport() {
        // Create help & support dialog
        new AlertDialog.Builder(this)
                .setTitle("Help & Support")
                .setMessage("Need assistance with Cash Flow?\n\n" +
                        "📧 Email: support@cashflowapp.com\n" +
                        "🌐 Website: www.cashflowapp.com\n" +
                        "📱 Phone: +1-800-CASHFLOW\n\n" +
                        "Our support team is available 24/7 to help you!")
                .setPositiveButton("OK", null)
                .setNeutralButton("Contact Support", (dialog, which) -> {
                    // Open email intent
                    Intent emailIntent = new Intent(Intent.ACTION_SEND);
                    emailIntent.setType("text/plain");
                    emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@cashflowapp.com"});
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Cash Flow App Support Request");
                    emailIntent.putExtra(Intent.EXTRA_TEXT, "Hi Cash Flow Support Team,\n\nI need help with...\n\n");

                    try {
                        startActivity(Intent.createChooser(emailIntent, "Send email via..."));
                    } catch (Exception e) {
                        Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void openAboutApp() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            new AlertDialog.Builder(this)
                    .setTitle("About Cash Flow")
                    .setMessage("💰 Cash Flow - Personal Finance Manager\n\n" +
                            "Track your income and expenses with ease using our intuitive interface.\n\n" +
                            "✨ Features:\n" +
                            "• Multiple cashbooks\n" +
                            "• Cloud sync with Firebase\n" +
                            "• Detailed transaction tracking\n" +
                            "• Export & import data\n" +
                            "• Dark & Light themes\n\n" +
                            "Version: " + version + "\n" +
                            "© 2025 Cash Flow App. All rights reserved.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to load app information", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?\n\nYour data will remain safely stored in the cloud.")
                .setPositiveButton("Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logoutUser() {
        // Show loading
        binding.logoutSection.setEnabled(false);

        mAuth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Clear any stored preferences
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDeleteAccountConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Delete Account")
                .setMessage("This action will permanently delete:\n\n" +
                        "• Your account and profile\n" +
                        "• All cashbooks and transactions\n" +
                        "• All stored data in the cloud\n\n" +
                        "❌ This action CANNOT be undone!\n\n" +
                        "Are you absolutely sure you want to proceed?")
                .setPositiveButton("Delete Forever", (dialog, which) -> showFinalDeleteConfirmation())
                .setNegativeButton("Keep Account", null)
                .show();
    }

    private void showFinalDeleteConfirmation() {
        // Create EditText for confirmation
        android.widget.EditText confirmationInput = new android.widget.EditText(this);
        confirmationInput.setHint("Type 'DELETE' to confirm");
        confirmationInput.setPadding(50, 30, 50, 30);

        new AlertDialog.Builder(this)
                .setTitle("Final Confirmation Required")
                .setMessage("Type 'DELETE' (all capitals) to permanently delete your account:")
                .setView(confirmationInput)
                .setPositiveButton("Delete Account", (dialog, which) -> {
                    String confirmation = confirmationInput.getText().toString().trim();
                    if ("DELETE".equals(confirmation)) {
                        deleteUserAccount();
                    } else {
                        Toast.makeText(this, "Confirmation text doesn't match. Account not deleted.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user found to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        binding.deleteAccountButton.setEnabled(false);
        binding.deleteAccountButton.setText("Deleting Account...");

        String userId = user.getUid();

        // First delete all user data from Firebase Database
        mDatabase.child("users").child(userId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Then delete the user authentication account
                    user.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Account deleted successfully. Sorry to see you go!", Toast.LENGTH_LONG).show();

                            // Clear local preferences
                            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                            prefs.edit().clear().apply();

                            // Redirect to main activity
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(this, "Failed to delete account. Please try signing out and back in, then try again.", Toast.LENGTH_LONG).show();
                            binding.deleteAccountButton.setEnabled(true);
                            binding.deleteAccountButton.setText("Delete Account");
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    ErrorHandler.handleExportError(this, e);
                    binding.deleteAccountButton.setEnabled(true);
                    binding.deleteAccountButton.setText("Delete Account");
                });
    }

    private void setTextWithBoldTitle(android.widget.TextView textView, String title, String description) {
        String fullText = title + "\n" + description;
        SpannableString ss = new SpannableString(fullText);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ss);
    }

    // NEW: Enhanced Firebase listener cleanup
    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
            userProfileListener = null;
        }

        if (cashbookNameListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).removeEventListener(cashbookNameListener);
            cashbookNameListener = null;
        }
    }
}
