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
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private TextView userNameTextView;
    private TextView uidTextView;
    private TextView dataLocationTextView;
    private TextView createdDateTextView;
    private ImageView editProfileButton;
    private ImageView backButton; // New: Back button reference
    private EditText editCashbookName; // New: EditText for cashbook name
    private ImageView saveCashbookNameButton; // New: Button to save cashbook name

    private LinearLayout btnTransactions;
    private LinearLayout btnHome;
    private LinearLayout btnSettingsNav;

    private TextView helpSupportTextView;
    private TextView appSettingsTextView;
    private TextView yourProfileTextView;
    private TextView aboutCashFlowTextView;
    private LinearLayout logoutSection;

    private ValueEventListener userProfileListener;
    private ValueEventListener cashbookNameListener; // New: Listener for cashbook name
    private String currentCashbookId; // The ID of the active cashbook

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View rootLayout = findViewById(R.id.main_root_layout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

                LinearLayout fixedBottomContainer = findViewById(R.id.fixedBottomContainer);
                if (fixedBottomContainer != null) {
                    fixedBottomContainer.setPadding(
                            fixedBottomContainer.getPaddingLeft(),
                            fixedBottomContainer.getPaddingTop(),
                            fixedBottomContainer.getPaddingRight(),
                            systemBars.bottom
                    );
                }
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_root_layout) not found. Insets might not be applied.");
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Get the active cashbook ID from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id", null);

        userNameTextView = findViewById(R.id.userName);
        uidTextView = findViewById(R.id.uidText);
        dataLocationTextView = findViewById(R.id.data_location);
        createdDateTextView = findViewById(R.id.created_date);
        editProfileButton = findViewById(R.id.editButton);
        backButton = findViewById(R.id.backButton);
        editCashbookName = findViewById(R.id.editCashbookName);
        saveCashbookNameButton = findViewById(R.id.saveCashbookNameButton);


        helpSupportTextView = findViewById(R.id.helpSupport);
        appSettingsTextView = findViewById(R.id.appSettings);
        yourProfileTextView = findViewById(R.id.yourProfile);
        aboutCashFlowTextView = findViewById(R.id.aboutCashFlow);
        logoutSection = findViewById(R.id.logoutSection);

        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);
        btnSettingsNav = findViewById(R.id.btnSettings);


        if (userNameTextView == null || uidTextView == null || dataLocationTextView == null ||
                createdDateTextView == null || editProfileButton == null || backButton == null ||
                editCashbookName == null || saveCashbookNameButton == null ||
                helpSupportTextView == null || appSettingsTextView == null || yourProfileTextView == null ||
                aboutCashFlowTextView == null || logoutSection == null ||
                btnTransactions == null || btnHome == null || btnSettingsNav == null) {
            Log.e(TAG, "One or more UI components not found in activity_settings.xml");
            Toast.makeText(this, "Error: Missing UI elements.", Toast.LENGTH_LONG).show();
            return;
        }

        setTextWithBoldTitle(helpSupportTextView, "Help & Support", "FAQ, Contact us");
        setTextWithBoldTitle(appSettingsTextView, "App Settings", "Language, Theme, Security, Backup");
        setTextWithBoldTitle(yourProfileTextView, "Your Profile", "Name, Mobile Number, Email");
        setTextWithBoldTitle(aboutCashFlowTextView, "About Cash Flow", "Privacy Policy, T&C, About us");

        // Set listeners for back and edit buttons
        backButton.setOnClickListener(v -> finish());
        editProfileButton.setOnClickListener(v -> {
            Log.d(TAG, "Edit profile button clicked. Launching EditProfileActivity.");
            Intent intent = new Intent(SettingsActivity.this, EditProfileActivity.class);
            startActivity(intent);
        });

        // Listener for saving cashbook name
        saveCashbookNameButton.setOnClickListener(v -> saveCashbookName());

        helpSupportTextView.setOnClickListener(v -> Toast.makeText(SettingsActivity.this, "Help & Support Clicked", Toast.LENGTH_SHORT).show());
        appSettingsTextView.setOnClickListener(v -> Toast.makeText(SettingsActivity.this, "App Settings Clicked", Toast.LENGTH_SHORT).show());
        yourProfileTextView.setOnClickListener(v -> {
            Log.d(TAG, "Your Profile clicked. Launching EditProfileActivity.");
            Intent intent = new Intent(SettingsActivity.this, EditProfileActivity.class);
            startActivity(intent);
        });
        aboutCashFlowTextView.setOnClickListener(v -> Toast.makeText(SettingsActivity.this, "About Cash Flow Clicked", Toast.LENGTH_SHORT).show());

        logoutSection.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(SettingsActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(SettingsActivity.this, Signin.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });


        btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, HomePage.class);
            startActivity(intent);
            finish();
        });

        btnSettingsNav.setOnClickListener(v -> {
            Toast.makeText(SettingsActivity.this, "Already on Settings", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "onStart: User logged in, starting user profile listener.");
            startListeningForUserProfile(currentUser.getUid());
            startListeningForCashbookName(currentUser.getUid(), currentCashbookId); // Start cashbook name listener
        } else {
            Log.d(TAG, "onStart: No user logged in for settings. Displaying guest/default info.");
            userNameTextView.setText("Guest User");
            uidTextView.setText("UID: GUEST");
            dataLocationTextView.setText("Local (Not Saved)");
            createdDateTextView.setText("Created: N/A");
            editCashbookName.setText(""); // Clear cashbook name for guests
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (userProfileListener != null) {
                mDatabase.child("users").child(currentUser.getUid()).removeEventListener(userProfileListener);
                Log.d(TAG, "User profile listener removed in onStop.");
            }
            if (cashbookNameListener != null && currentCashbookId != null) {
                mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).removeEventListener(cashbookNameListener);
                Log.d(TAG, "Cashbook name listener removed in onStop.");
            }
        }
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
                    Log.d(TAG, "onDataChange: User profile loaded from database.");
                    userNameTextView.setText(userProfile.getUserName() != null ? userProfile.getUserName() : "User Name");
                    uidTextView.setText("UID: " + (userProfile.getUserId() != null ? userProfile.getUserId() : "N/A"));
                    dataLocationTextView.setText("Cloud (Firebase)");
                    if (firebaseUser != null && firebaseUser.getMetadata() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        createdDateTextView.setText("Created: " + sdf.format(new Date(firebaseUser.getMetadata().getCreationTimestamp())));
                    } else {
                        createdDateTextView.setText("Created: N/A");
                    }
                } else {
                    Log.d(TAG, "onDataChange: User profile not found in database. Setting default for Settings.");
                    if (firebaseUser != null) {
                        userNameTextView.setText(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : firebaseUser.getEmail());
                        uidTextView.setText("UID: " + firebaseUser.getUid());
                        dataLocationTextView.setText("Cloud (Firebase)");
                        if (firebaseUser.getMetadata() != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                            createdDateTextView.setText("Created: " + sdf.format(new Date(firebaseUser.getMetadata().getCreationTimestamp())));
                        } else {
                            createdDateTextView.setText("Created: N/A");
                        }
                    } else {
                        userNameTextView.setText("Welcome User");
                        uidTextView.setText("UID: N/A");
                        dataLocationTextView.setText("Not Available");
                        createdDateTextView.setText("Not Available");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "onCancelled: Failed to load user profile: " + databaseError.getMessage(), databaseError.toException());
                Toast.makeText(SettingsActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.child("users").child(userId).addValueEventListener(userProfileListener);
    }

    // New method to listen for cashbook name changes
    private void startListeningForCashbookName(String userId, String cashbookId) {
        if (cashbookNameListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(cashbookId).removeEventListener(cashbookNameListener);
        }

        if (cashbookId == null) {
            editCashbookName.setText("No Active Cashbook");
            return;
        }

        cashbookNameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                CashbookModel cashbook = dataSnapshot.getValue(CashbookModel.class);
                if (cashbook != null && cashbook.getName() != null) {
                    editCashbookName.setText(cashbook.getName());
                } else {
                    editCashbookName.setText("Cashbook Not Found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Cashbook name load onCancelled: " + databaseError.getMessage(), databaseError.toException());
                Toast.makeText(SettingsActivity.this, "Failed to load cashbook name.", Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.child("users").child(userId).child("cashbooks").child(cashbookId).addValueEventListener(cashbookNameListener);
    }

    // New method to save the updated cashbook name
    private void saveCashbookName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentCashbookId == null) {
            Toast.makeText(this, "Cannot save. No user or cashbook selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        String newName = editCashbookName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            editCashbookName.setError("Name cannot be empty");
            return;
        }

        mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("name").setValue(newName)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(SettingsActivity.this, "Cashbook name saved!", Toast.LENGTH_SHORT).show();
                    // The listener will automatically update the UI
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save cashbook name: " + e.getMessage(), e);
                    Toast.makeText(SettingsActivity.this, "Failed to save name.", Toast.LENGTH_LONG).show();
                });
    }

    private void setTextWithBoldTitle(TextView textView, String title, String description) {
        String fullText = title + "\n" + description;
        SpannableString ss = new SpannableString(fullText);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ss);
    }
}