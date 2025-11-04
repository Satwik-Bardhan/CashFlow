package com.example.cashflow;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cashflow.utils.ErrorHandler;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * SettingsActivity - User settings and account management
 *
 * Features:
 * - Profile management
 * - App settings
 * - Account actions (logout, delete)
 * - Integrated bottom navigation with cashbook switcher
 * - Guest and authenticated user support
 *
 * Updated: November 2025 - Complete refactor with CashbookSwitchActivity integration
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener cashbooksListener;

    // Data
    private List<CashbookModel> cashbooks = new ArrayList<>();
    private String currentCashbookId;
    private boolean isGuest;

    // UI Elements - Profile Section
    private TextView userNameTextView, uidTextView;
    private LinearLayout profileSection;

    // UI Elements - Settings Sections
    private LinearLayout helpSupport, appSettings, yourProfile, aboutCashFlow, logoutSection;
    private Button deleteAccountButton;

    // Bottom Navigation
    private FrameLayout btnHome, btnTransactions, btnCashbooks, btnSettings;
    private ImageView iconHome, iconTransactions, iconSettings, iconCashbooks;
    private TextView textHome, textTransactions, textSettings, textCashbooks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Get extras from intent
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        initializeUI();
        setupClickListeners();
        setupBottomNavigation();
        loadUserProfile();

        Log.d(TAG, "SettingsActivity created, guest mode: " + isGuest);
    }

    /**
     * Initialize all UI components
     */
    private void initializeUI() {
        // Profile Section
        profileSection = findViewById(R.id.profileSection);
        userNameTextView = findViewById(R.id.userName);
        uidTextView = findViewById(R.id.uidText);

        // Settings Sections
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
        btnCashbooks = findViewById(R.id.btnCashbookSwitch);
        btnSettings = findViewById(R.id.btnSettings);

        iconHome = findViewById(R.id.iconHome);
        iconTransactions = findViewById(R.id.iconTransactions);
        iconSettings = findViewById(R.id.iconSettings);
        iconCashbooks = findViewById(R.id.iconCashbookSwitch);

        textHome = findViewById(R.id.textHome);
        textTransactions = findViewById(R.id.textTransactions);
        textSettings = findViewById(R.id.textSettings);
        textCashbooks = findViewById(R.id.textCashbookSwitch);
    }

    /**
     * Setup bottom navigation with proper styling and cashbook switcher
     */
    private void setupBottomNavigation() {
        updateBottomNavigationSelection(btnSettings);

        // Home Button
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("isGuest", isGuest);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        // Transactions Button
        btnTransactions.setOnClickListener(v -> {
            if (isGuest || currentCashbookId == null) {
                showGuestLimitationDialog();
                return;
            }
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.putExtra("isGuest", isGuest);
            startActivity(intent);
            finish();
        });

        // Cashbook Switcher Button - Integrated with CashbookSwitchActivity
        btnCashbooks.setOnClickListener(v -> openCashbookSwitcher());

        // Settings Button (current activity)
        btnSettings.setOnClickListener(v ->
                showSnackbar("Already on Settings"));

        // Load cashbooks for badge display
        if (!isGuest) {
            loadCashbooksForBadge();
        }
    }

    /**
     * Opens CashbookSwitchActivity for comprehensive cashbook management
     */
    private void openCashbookSwitcher() {
        if (isGuest) {
            showGuestLimitationDialog();
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            showSnackbar("Please log in first");
            return;
        }

        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);

        Log.d(TAG, "Opened CashbookSwitchActivity");
    }

    /**
     * Handle result from CashbookSwitchActivity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");

            if (newCashbookId != null && !newCashbookId.equals(currentCashbookId)) {
                currentCashbookId = newCashbookId;
                showSnackbar("Switched to: " + cashbookName);
                Log.d(TAG, "Cashbook switched to: " + cashbookName);
            }
        }
    }

    /**
     * Load cashbooks from Firebase for badge display
     */
    private void loadCashbooksForBadge() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Cannot load cashbooks: user not authenticated");
            return;
        }

        String userId = currentUser.getUid();

        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    cashbooks.clear();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            if (cashbook.getCashbookId() == null) {
                                cashbook.setCashbookId(snapshot.getKey());
                            }
                            cashbooks.add(cashbook);
                        }
                    }

                    updateCashbookBadge();
                    Log.d(TAG, "Loaded " + cashbooks.size() + " cashbooks for badge");
                } catch (Exception e) {
                    Log.e(TAG, "Error processing cashbooks", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks for badge", databaseError.toException());
            }
        };

        mDatabase.child("users").child(userId).child("cashbooks")
                .addValueEventListener(cashbooksListener);
    }

    /**
     * Update cashbook count badge on bottom navigation
     */
    private void updateCashbookBadge() {
        if (btnCashbooks == null || isGuest) {
            return;
        }

        try {
            int cashbookCount = cashbooks.size();

            // Remove existing badge if present
            View existingBadge = btnCashbooks.findViewWithTag("cashbook_badge");
            if (existingBadge != null) {
                btnCashbooks.removeView(existingBadge);
            }

            if (cashbookCount > 1) {
                // Create custom badge TextView
                TextView badge = new TextView(this);
                badge.setTag("cashbook_badge");
                badge.setText(String.valueOf(cashbookCount));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(Gravity.CENTER);
                badge.setTypeface(null, Typeface.BOLD);

                // Set background color using ShapeDrawable
                ShapeDrawable drawable = new ShapeDrawable(new OvalShape());
                drawable.getPaint().setColor(ContextCompat.getColor(this, R.color.primary_blue));
                badge.setBackground(drawable);

                // Position badge at top-right corner
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        dpToPx(22),
                        dpToPx(22),
                        Gravity.TOP | Gravity.END);
                params.setMargins(0, dpToPx(2), dpToPx(2), 0);
                badge.setLayoutParams(params);

                // Add badge to FrameLayout
                btnCashbooks.addView(badge);

                Log.d(TAG, "Badge updated: " + cashbookCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating cashbook badge", e);
        }
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Setup click listeners for all UI elements
     */
    private void setupClickListeners() {
        // Back Button
        View backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Profile Section
        if (profileSection != null) {
            profileSection.setOnClickListener(v -> {
                if (isGuest) {
                    showGuestLimitationDialog();
                } else {
                    Intent intent = new Intent(this, EditProfileActivity.class);
                    startActivity(intent);
                }
            });
        }

        // App Settings
        if (appSettings != null) {
            appSettings.setOnClickListener(v -> {
                Intent intent = new Intent(this, AppSettingsActivity.class);
                startActivity(intent);
            });
        }

        // Help & Support
        if (helpSupport != null) {
            helpSupport.setOnClickListener(v -> {
                showSnackbar("Help & Support - Coming Soon");
                Log.d(TAG, "Help & Support clicked");
            });
        }

        // Your Profile (alternative access)
        if (yourProfile != null) {
            yourProfile.setOnClickListener(v -> {
                if (isGuest) {
                    showGuestLimitationDialog();
                } else {
                    Intent intent = new Intent(this, EditProfileActivity.class);
                    startActivity(intent);
                }
            });
        }

        // About CashFlow
        if (aboutCashFlow != null) {
            aboutCashFlow.setOnClickListener(v -> showAboutDialog());
        }

        // Logout
        if (logoutSection != null) {
            logoutSection.setOnClickListener(v -> {
                if (isGuest) {
                    showSignUpPromptDialog();
                } else {
                    showLogoutConfirmationDialog();
                }
            });
        }

        // Delete Account
        if (deleteAccountButton != null) {
            deleteAccountButton.setOnClickListener(v -> {
                if (isGuest) {
                    showGuestLimitationDialog();
                } else {
                    showDeleteAccountConfirmationDialog();
                }
            });
        }
    }

    /**
     * Load user profile information from Firebase
     */
    private void loadUserProfile() {
        if (isGuest) {
            if (userNameTextView != null) {
                userNameTextView.setText("Guest User");
            }
            if (uidTextView != null) {
                uidTextView.setText("Not signed in");
            }
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();

            if (uidTextView != null) {
                uidTextView.setText("UID: " + userId.substring(0, Math.min(12, userId.length())) + "...");
            }

            mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        Users user = snapshot.getValue(Users.class);
                        if (user != null && user.getUserName() != null && !user.getUserName().isEmpty()) {
                            if (userNameTextView != null) {
                                userNameTextView.setText(user.getUserName());
                            }
                        } else {
                            // Fallback to display name from Firebase Auth
                            String displayName = currentUser.getDisplayName();
                            if (userNameTextView != null) {
                                userNameTextView.setText(displayName != null ? displayName : "CashFlow User");
                            }
                        }
                        Log.d(TAG, "User profile loaded successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user profile", e);
                        if (userNameTextView != null) {
                            userNameTextView.setText("CashFlow User");
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to load user data", error.toException());
                    ErrorHandler.handleFirebaseError(SettingsActivity.this, error);
                }
            });
        } else {
            if (userNameTextView != null) {
                userNameTextView.setText("Guest User");
            }
            if (uidTextView != null) {
                uidTextView.setText("Not signed in");
            }
        }
    }

    /**
     * Show logout confirmation dialog
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Perform logout operation
     */
    private void performLogout() {
        try {
            mAuth.signOut();
            Log.d(TAG, "User logged out successfully");

            Intent intent = new Intent(SettingsActivity.this, SigninActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error during logout", e);
            showSnackbar("Error logging out. Please try again.");
        }
    }

    /**
     * Show delete account confirmation dialog
     */
    private void showDeleteAccountConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("⚠️ WARNING: This action is permanent and cannot be undone.\n\n" +
                        "All your data including:\n" +
                        "• All cashbooks\n" +
                        "• All transactions\n" +
                        "• Profile information\n" +
                        "• Settings\n\n" +
                        "will be permanently deleted.\n\n" +
                        "Are you absolutely sure you want to proceed?")
                .setPositiveButton("Delete Permanently", (dialog, which) -> deleteUserAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Delete user account and all associated data from Firebase
     */
    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showSnackbar("Not logged in");
            return;
        }

        String userId = user.getUid();

        // Show progress
        showSnackbar("Deleting account...");

        // First, delete user data from Realtime Database
        mDatabase.child("users").child(userId).removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Then, delete the user from Authentication
                        user.delete().addOnCompleteListener(deleteTask -> {
                            if (deleteTask.isSuccessful()) {
                                Toast.makeText(this,
                                        "Account deleted successfully.",
                                        Toast.LENGTH_LONG).show();

                                Log.d(TAG, "Account deleted successfully");

                                Intent intent = new Intent(SettingsActivity.this,
                                        SigninActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                Log.e(TAG, "Failed to delete account", deleteTask.getException());
                                Toast.makeText(this,
                                        "Failed to delete account. Please try logging in again.",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to delete user data", task.getException());
                        Toast.makeText(this,
                                "Failed to delete user data.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show about dialog with app information
     */
    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About CashFlow")
                .setMessage("CashFlow - Personal Finance Manager\n\n" +
                        "Version: 1.0.0\n" +
                        "Build: November 2025\n\n" +
                        "Track your income and expenses with ease.\n" +
                        "Manage multiple cashbooks, analyze spending patterns, " +
                        "and export reports.\n\n" +
                        "Developed with ❤️ for better financial management.")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Show guest limitation dialog
     */
    private void showGuestLimitationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Guest Mode Limitation")
                .setMessage("This feature is not available in guest mode.\n\n" +
                        "Sign up to access full functionality including:\n\n" +
                        "• Profile customization\n" +
                        "• Multiple cashbooks\n" +
                        "• Cloud sync\n" +
                        "• Data backup")
                .setPositiveButton("Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(this, SignupActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
    }

    /**
     * Show sign up prompt for guest users
     */
    private void showSignUpPromptDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Guest Mode")
                .setMessage("You're currently using CashFlow as a guest.\n\n" +
                        "Sign up to save your data permanently and access " +
                        "premium features.")
                .setPositiveButton("Sign Up Now", (dialog, which) -> {
                    Intent intent = new Intent(this, SignupActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
    }

    /**
     * Update bottom navigation visual state based on selection
     */
    private void updateBottomNavigationSelection(View selectedButton) {
        // Reset all buttons to default state
        resetBottomNavItem(iconHome, textHome);
        resetBottomNavItem(iconTransactions, textTransactions);
        resetBottomNavItem(iconSettings, textSettings);
        resetBottomNavItem(iconCashbooks, textCashbooks);

        // Set the selected button to active state
        int activeColor = getColor(R.color.primary_blue);

        if (selectedButton.getId() == R.id.btnHome) {
            setBottomNavItemActive(iconHome, textHome, activeColor);
        } else if (selectedButton.getId() == R.id.btnTransactions) {
            setBottomNavItemActive(iconTransactions, textTransactions, activeColor);
        } else if (selectedButton.getId() == R.id.btnSettings) {
            setBottomNavItemActive(iconSettings, textSettings, activeColor);
        } else if (selectedButton.getId() == R.id.btnCashbookSwitch) {
            setBottomNavItemActive(iconCashbooks, textCashbooks, activeColor);
        }
    }

    /**
     * Reset bottom navigation item to default state
     */
    private void resetBottomNavItem(ImageView icon, TextView text) {
        if (icon != null) {
            icon.setColorFilter(Color.WHITE);
        }
        if (text != null) {
            text.setTextColor(Color.WHITE);
        }
    }

    /**
     * Set bottom navigation item to active state
     */
    private void setBottomNavItemActive(ImageView icon, TextView text, int color) {
        if (icon != null) {
            icon.setColorFilter(color);
        }
        if (text != null) {
            text.setTextColor(color);
        }
    }

    /**
     * Show Snackbar message
     */
    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove cashbooks listener to prevent memory leaks
        if (cashbooksListener != null) {
            try {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null) {
                    mDatabase.child("users").child(currentUser.getUid())
                            .child("cashbooks")
                            .removeEventListener(cashbooksListener);
                    Log.d(TAG, "Cashbooks listener removed");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing cashbooks listener", e);
            }
        }

        Log.d(TAG, "SettingsActivity destroyed");
    }
}
