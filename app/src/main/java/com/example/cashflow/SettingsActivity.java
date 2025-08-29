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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private TextView userNameTextView, uidTextView, dataLocationTextView, createdDateTextView;
    private EditText editCashbookName;
    private TextView btnTransactions, btnHome, btnSettings;

    private ValueEventListener userProfileListener;
    private ValueEventListener cashbookNameListener;
    private String currentCashbookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
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

        btnSettings.setSelected(true);
    }

    private void initializeUI() {
        userNameTextView = findViewById(R.id.userName);
        uidTextView = findViewById(R.id.uidText);
        dataLocationTextView = findViewById(R.id.data_location);
        createdDateTextView = findViewById(R.id.created_date);
        editCashbookName = findViewById(R.id.editCashbookName);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);
        btnSettings = findViewById(R.id.btnSettings);

        setTextWithBoldTitle(findViewById(R.id.helpSupport), "Help & Support", "FAQ, Contact us");
        setTextWithBoldTitle(findViewById(R.id.appSettings), "App Settings", "Language, Theme, Security, Backup");
        setTextWithBoldTitle(findViewById(R.id.yourProfile), "Your Profile", "Name, Mobile Number, Email");
        setTextWithBoldTitle(findViewById(R.id.aboutCashFlow), "About Cash Flow", "Privacy Policy, T&C, About us");
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.editButton).setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        findViewById(R.id.yourProfile).setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        findViewById(R.id.appSettings).setOnClickListener(v -> startActivity(new Intent(this, AppSettingsActivity.class)));
        findViewById(R.id.saveCashbookNameButton).setOnClickListener(v -> saveCashbookName());
        findViewById(R.id.logoutSection).setOnClickListener(v -> logoutUser());
        findViewById(R.id.deleteAccountButton).setOnClickListener(v -> showDeleteAccountConfirmation());

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomePage.class));
            finish();
        });
        btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });
        btnSettings.setOnClickListener(v -> Toast.makeText(this, "Already on Settings", Toast.LENGTH_SHORT).show());
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
                editCashbookName.setText("No Active Cashbook");
                editCashbookName.setEnabled(false);
            }
        } else {
            userNameTextView.setText("Guest User");
            uidTextView.setText("UID: GUEST");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    private void startListeningForUserProfile(String userId) {
        userProfileListener = new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Users userProfile = dataSnapshot.getValue(Users.class);
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (userProfile != null) {
                    userNameTextView.setText(userProfile.getUserName());
                    uidTextView.setText("UID: " + userProfile.getUserId());
                    if (firebaseUser != null && firebaseUser.getMetadata() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        createdDateTextView.setText("Created: " + sdf.format(new Date(firebaseUser.getMetadata().getCreationTimestamp())));
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { /* Handle error */ }
        };
        mDatabase.child("users").child(userId).addValueEventListener(userProfileListener);
    }

    private void startListeningForCashbookName(String userId, String cashbookId) {
        cashbookNameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                CashbookModel cashbook = dataSnapshot.getValue(CashbookModel.class);
                if (cashbook != null) {
                    editCashbookName.setText(cashbook.getName());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { /* Handle error */ }
        };
        mDatabase.child("users").child(userId).child("cashbooks").child(cashbookId).addValueEventListener(cashbookNameListener);
    }

    private void saveCashbookName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentCashbookId == null) return;

        String newName = editCashbookName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            editCashbookName.setError("Name cannot be empty");
            return;
        }

        mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("name").setValue(newName)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Cashbook name saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save name.", Toast.LENGTH_LONG).show());
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, Signin.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showDeleteAccountConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("This will permanently delete your account and all data. This action cannot be undone. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> deleteUserAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        mDatabase.child("users").child(user.getUid()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    user.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show();
                            logoutUser();
                        } else {
                            Toast.makeText(this, "Failed to delete account. Please sign in again.", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete user data.", Toast.LENGTH_LONG).show());
    }

    private void setTextWithBoldTitle(TextView textView, String title, String description) {
        String fullText = title + "\n" + description;
        SpannableString ss = new SpannableString(fullText);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ss);
    }

    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
        }
        if (cashbookNameListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).removeEventListener(cashbookNameListener);
        }
    }
}
