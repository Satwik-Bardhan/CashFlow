package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide; // [FIX] Added Glide import
import com.example.cashflow.databinding.ActivitySettingsBinding;
import com.example.cashflow.models.Users;
import com.example.cashflow.utils.ErrorHandler;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // ViewBinding
    private ActivitySettingsBinding binding;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private DatabaseReference userRef; // [FIX] Specific ref to user node

    // Listeners
    private ValueEventListener userProfileListener;
    private ValueEventListener cashbookNameListener;
    private ValueEventListener cashbooksListener;

    // Data
    private List<CashbookModel> cashbooks = new ArrayList<>();
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Init Firebase & State
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        // [FIX] Robust check for user
        if (currentUser == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            logoutUser(); // Send back to login
            return;
        }

        userRef = mDatabase.child("users").child(currentUser.getUid());

        setupClickListeners();
        setupBottomNavigation();
    }

    private void setupClickListeners() {
        binding.backButton.setOnClickListener(v -> finish());

        // Primary Settings Listeners
        binding.primarySettingsLayout.profileSection.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));
        binding.primarySettingsLayout.editButton.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));
        binding.primarySettingsLayout.saveCashbookNameButton.setOnClickListener(v -> saveCashbookName());

        // General Settings Listeners
        binding.generalSettingsLayout.helpSupport.setOnClickListener(v ->
                Toast.makeText(this, "Help & Support clicked", Toast.LENGTH_SHORT).show());
        binding.generalSettingsLayout.appSettings.setOnClickListener(v ->
                startActivity(new Intent(this, AppSettingsActivity.class)));
        binding.generalSettingsLayout.yourProfile.setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));
        binding.generalSettingsLayout.aboutCashFlow.setOnClickListener(v ->
                Toast.makeText(this, "About Cash Flow clicked", Toast.LENGTH_SHORT).show());

        // Account Actions
        binding.logoutSection.setOnClickListener(v -> showLogoutConfirmationDialog());
        binding.deleteAccountButton.setOnClickListener(v -> showDeleteAccountConfirmationDialog());
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.btnSettings.setSelected(true); // This enables the ColorStateList

        binding.bottomNavigation.btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });

        binding.bottomNavigation.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });

        binding.bottomNavigation.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        binding.bottomNavigation.btnSettings.setOnClickListener(v ->
                showSnackbar("Already on Settings"));
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");

            if (newCashbookId != null && !newCashbookId.equals(currentCashbookId)) {
                currentCashbookId = newCashbookId;
                // [FIX] Save the new active cashbook ID
                saveActiveCashbookId(currentCashbookId);
                showSnackbar("Switched to: " + cashbookName);
                // Re-load the cashbook name
                startListeningForCashbookName(currentCashbookId);
            }
        }
    }

    private void loadCashbooksForBadge() {
        if (userRef == null) return;

        if (cashbooksListener != null) {
            userRef.child("cashbooks").removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        cashbook.setCashbookId(snapshot.getKey());
                        cashbooks.add(cashbook);
                    }
                }
                updateCashbookBadge();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks for badge", databaseError.toException());
            }
        };
        userRef.child("cashbooks").addValueEventListener(cashbooksListener);
    }

    private void updateCashbookBadge() {
        if (binding.bottomNavigation.btnCashbookSwitch == null) return;

        try {
            int cashbookCount = cashbooks.size();
            View existingBadge = binding.bottomNavigation.btnCashbookSwitch.findViewWithTag("cashbook_badge");
            if (existingBadge != null) {
                binding.bottomNavigation.btnCashbookSwitch.removeView(existingBadge);
            }

            if (cashbookCount > 1) {
                TextView badge = new TextView(this);
                badge.setTag("cashbook_badge");
                badge.setText(String.valueOf(cashbookCount));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(Gravity.CENTER);
                badge.setTypeface(null, Typeface.BOLD);

                ShapeDrawable drawable = new ShapeDrawable(new OvalShape());
                // [FIX] Use theme color for badge
                drawable.getPaint().setColor(ThemeUtil.getThemeAttrColor(this, R.attr.balanceColor));
                badge.setBackground(drawable);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        dpToPx(22), dpToPx(22), Gravity.TOP | Gravity.END);
                params.setMargins(0, dpToPx(2), dpToPx(2), 0);
                badge.setLayoutParams(params);

                binding.bottomNavigation.btnCashbookSwitch.addView(badge);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating cashbook badge", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUser != null) {
            startListeningForUserProfile();
            if (currentCashbookId != null) {
                startListeningForCashbookName(currentCashbookId);
            } else {
                binding.primarySettingsLayout.editCashbookName.setText("No Active Cashbook");
                binding.primarySettingsLayout.editCashbookName.setEnabled(false);
                binding.primarySettingsLayout.saveCashbookNameButton.setEnabled(false);
            }
            loadCashbooksForBadge(); // Refresh badge
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    // [FIX] Removed handleGuestMode()

    private void startListeningForUserProfile() {
        if (userRef == null) return;
        userProfileListener = new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Users userProfile = dataSnapshot.getValue(Users.class);
                if (userProfile != null && !TextUtils.isEmpty(userProfile.getUserName())) {
                    binding.primarySettingsLayout.userName.setText(userProfile.getUserName());
                } else if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                    binding.primarySettingsLayout.userName.setText(currentUser.getDisplayName());
                } else {
                    binding.primarySettingsLayout.userName.setText("CashFlow User");
                }

                // [FIX] Load profile image with Glide
                if (userProfile != null && userProfile.getProfile() != null && !userProfile.getProfile().isEmpty()) {
                    Glide.with(SettingsActivity.this)
                            .load(userProfile.getProfile())
                            .placeholder(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .into(binding.primarySettingsLayout.profileImg);
                }

                binding.primarySettingsLayout.uidText.setText("UID: " + currentUser.getUid().substring(0, 8) + "...");

                if (currentUser.getMetadata() != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
                    String creationDate = sdf.format(new Date(currentUser.getMetadata().getCreationTimestamp()));
                    binding.primarySettingsLayout.createdDate.setText("Created on " + creationDate);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };
        userRef.addValueEventListener(userProfileListener);
    }

    private void startListeningForCashbookName(String cashbookId) {
        if (userRef == null) return;

        if (cashbookNameListener != null) {
            userRef.child("cashbooks").child(cashbookId).removeEventListener(cashbookNameListener);
        }

        cashbookNameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                CashbookModel cashbook = dataSnapshot.getValue(CashbookModel.class);
                if (cashbook != null) {
                    binding.primarySettingsLayout.editCashbookName.setText(cashbook.getName());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                ErrorHandler.handleFirebaseError(SettingsActivity.this, databaseError);
            }
        };
        userRef.child("cashbooks").child(cashbookId).addValueEventListener(cashbookNameListener);
    }

    private void saveCashbookName() {
        if (userRef == null || currentCashbookId == null) {
            showSnackbar("Cannot save, no cashbook selected.");
            return;
        }

        String newName = binding.primarySettingsLayout.editCashbookName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            binding.primarySettingsLayout.editCashbookName.setError("Name cannot be empty");
            return;
        }

        userRef.child("cashbooks").child(currentCashbookId).child("name").setValue(newName)
                .addOnSuccessListener(aVoid -> showSnackbar("Cashbook name saved!"))
                .addOnFailureListener(e -> showSnackbar("Failed to save name."));
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("This will permanently delete your account and all data. This action cannot be undone. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> deleteUserAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUserAccount() {
        if (currentUser == null || userRef == null) return;

        // 1. Delete Realtime Database data
        userRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    // 2. Delete Auth user
                    currentUser.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show();
                            logoutUser(); // Go back to SigninActivity
                        } else {
                            Toast.makeText(this, "Failed to delete account. Please sign in again.", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete user data.", Toast.LENGTH_LONG).show());
    }

    // [FIX] Removed showGuestLimitationDialog()

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    private void removeFirebaseListeners() {
        if (userRef == null) return;

        if (userProfileListener != null) {
            userRef.removeEventListener(userProfileListener);
        }
        if (cashbookNameListener != null && currentCashbookId != null) {
            userRef.child("cashbooks").child(currentCashbookId).removeEventListener(cashbookNameListener);
        }
        if (cashbooksListener != null) {
            userRef.child("cashbooks").removeEventListener(cashbooksListener);
        }
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }

    // [FIX] Added helper to save active cashbook ID
    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUser.getUid(), cashbookId).apply();
    }
}