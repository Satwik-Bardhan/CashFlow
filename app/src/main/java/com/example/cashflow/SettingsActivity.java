package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

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
    // Removed: private View logoutButton; // This variable is no longer needed
    private ImageView editProfileButton;

    private LinearLayout btnTransactions;
    private LinearLayout btnHome;
    private LinearLayout btnSettingsNav;

    private TextView helpSupportTextView;
    private TextView appSettingsTextView;
    private TextView yourProfileTextView;
    private TextView aboutCashFlowTextView;
    private LinearLayout logoutSection;


    private ValueEventListener userProfileListener;


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
                // Apply top, left, right insets to the root layout
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0); // Remove bottom padding here

                // Apply bottom inset to the fixedBottomContainer
                LinearLayout fixedBottomContainer = findViewById(R.id.fixedBottomContainer);
                if (fixedBottomContainer != null) {
                    fixedBottomContainer.setPadding(
                            fixedBottomContainer.getPaddingLeft(),
                            fixedBottomContainer.getPaddingTop(),
                            fixedBottomContainer.getPaddingRight(),
                            systemBars.bottom // Apply system nav bar height as padding
                    );
                }
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_root_layout) not found. Insets might not be applied.");
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        userNameTextView = findViewById(R.id.userName);
        uidTextView = findViewById(R.id.uidText);
        dataLocationTextView = findViewById(R.id.data_location);
        createdDateTextView = findViewById(R.id.created_date);
        // Removed initialization of the old logoutButton
        // logoutButton = findViewById(R.id.logout_button);
        editProfileButton = findViewById(R.id.editButton);

        helpSupportTextView = findViewById(R.id.helpSupport);
        appSettingsTextView = findViewById(R.id.appSettings);
        yourProfileTextView = findViewById(R.id.yourProfile);
        aboutCashFlowTextView = findViewById(R.id.aboutCashFlow);
        logoutSection = findViewById(R.id.logoutSection);

        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);
        btnSettingsNav = findViewById(R.id.btnSettings);


        // Comprehensive null check for all UI elements
        if (userNameTextView == null || uidTextView == null || dataLocationTextView == null ||
                createdDateTextView == null || editProfileButton == null ||
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


        // Removed old logoutButton listener
        // logoutButton.setOnClickListener(v -> { ... });

        editProfileButton.setOnClickListener(v -> {
            Log.d(TAG, "Edit profile button clicked. Launching EditProfileActivity.");
            Intent intent = new Intent(SettingsActivity.this, EditProfileActivity.class);
            startActivity(intent);
        });

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
        } else {
            Log.d(TAG, "onStart: No user logged in for settings. Displaying guest/default info.");
            userNameTextView.setText("Guest User");
            uidTextView.setText("UID: GUEST");
            dataLocationTextView.setText("Local (Not Saved)");
            createdDateTextView.setText("Created: N/A");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && userProfileListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).removeEventListener(userProfileListener);
            Log.d(TAG, "User profile listener removed in onStop.");
        }
    }

    @SuppressLint("SetTextI18n")
    private void startListeningForUserProfile(String userId) {
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
        }

        userProfileListener = new ValueEventListener() {
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
                    Log.d(TAG, "onDataChange: User profile not found in database. Setting default for HomePage.");
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

    private void setTextWithBoldTitle(TextView textView, String title, String description) {
        String fullText = title + "\n" + description;
        SpannableString ss = new SpannableString(fullText);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ss);
    }
}