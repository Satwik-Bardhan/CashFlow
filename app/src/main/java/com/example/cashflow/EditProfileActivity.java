package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView; // Added import for ImageView
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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import android.net.Uri;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private EditText editUserName;
    private TextView displayEmail;
    private EditText editProfilePictureUrl;
    private Button changePasswordButton;
    private Button saveProfileChangesButton;
    private ImageView backButton; // New: Back button reference

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private ValueEventListener userProfileListener;

    private Users currentUserProfile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View rootLayout = findViewById(R.id.main_root_layout); // Ensure this is the root RelativeLayout
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); // Full padding
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_root_layout) not found. Insets might not be applied.");
            Toast.makeText(this, "Layout error: root view not found.", Toast.LENGTH_LONG).show();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to edit profile.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        editUserName = findViewById(R.id.editUserName);
        displayEmail = findViewById(R.id.displayEmail);
        editProfilePictureUrl = findViewById(R.id.editProfilePictureUrl);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        saveProfileChangesButton = findViewById(R.id.saveProfileChangesButton);
        backButton = findViewById(R.id.backButton); // Initialize back button

        if (editUserName == null || displayEmail == null || editProfilePictureUrl == null ||
                changePasswordButton == null || saveProfileChangesButton == null || backButton == null) { // Added null check for back button
            Log.e(TAG, "onCreate: One or more UI components not found in activity_edit_profile.xml");
            Toast.makeText(this, "Error: Missing UI elements.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        displayEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "N/A");

        changePasswordButton.setOnClickListener(v -> navigateToChangePassword());
        saveProfileChangesButton.setOnClickListener(v -> saveProfileChanges());
        backButton.setOnClickListener(v -> finish()); // Set click listener for back button
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUser != null) {
            startListeningForUserProfile(currentUser.getUid());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (currentUser != null && userProfileListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).removeEventListener(userProfileListener);
            Log.d(TAG, "User profile listener removed in onStop.");
        }
    }

    private void startListeningForUserProfile(String userId) {
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
        }

        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                currentUserProfile = dataSnapshot.getValue(Users.class);
                if (currentUserProfile != null) {
                    Log.d(TAG, "onDataChange: User profile loaded: " + currentUserProfile.getUserName());
                    editUserName.setText(currentUserProfile.getUserName() != null ? currentUserProfile.getUserName() : "");
                    editProfilePictureUrl.setText(currentUserProfile.getProfile() != null ? currentUserProfile.getProfile() : "");
                } else {
                    Log.d(TAG, "onDataChange: User profile not found in database. Initializing with FirebaseUser data.");
                    editUserName.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "");
                    editProfilePictureUrl.setText(currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
                    currentUserProfile = new Users();
                    currentUserProfile.setUserId(currentUser.getUid());
                    currentUserProfile.setMail(currentUser.getEmail());
                    currentUserProfile.setUserName(currentUser.getDisplayName());
                    currentUserProfile.setProfile(currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "onCancelled: Failed to load user profile: " + databaseError.getMessage(), databaseError.toException());
                Toast.makeText(EditProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.child("users").child(userId).addValueEventListener(userProfileListener);
        Log.d(TAG, "Firebase user profile listener attached for user: " + userId);
    }

    private void saveProfileChanges() {
        String newUserName = editUserName.getText().toString().trim();
        String newProfileUrl = editProfilePictureUrl.getText().toString().trim();

        if (TextUtils.isEmpty(newUserName)) {
            editUserName.setError("User Name cannot be empty");
            editUserName.requestFocus();
            return;
        }

        UserProfileChangeRequest.Builder profileUpdatesBuilder = new UserProfileChangeRequest.Builder()
                .setDisplayName(newUserName);

        if (!TextUtils.isEmpty(newProfileUrl)) {
            profileUpdatesBuilder.setPhotoUri(Uri.parse(newProfileUrl));
        } else {
            profileUpdatesBuilder.setPhotoUri(null);
        }
        UserProfileChangeRequest profileUpdates = profileUpdatesBuilder.build();


        currentUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User Auth profile updated.");

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("userName", newUserName);
                        updates.put("profile", newProfileUrl);

                        mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "User profile (partial) saved to database.");
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update user profile in database: " + e.getMessage(), e);
                                    Toast.makeText(EditProfileActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        Log.e(TAG, "Failed to update User Auth profile: " + Objects.requireNonNull(task.getException()).getMessage(), task.getException());
                        Toast.makeText(EditProfileActivity.this, "Failed to update profile: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToChangePassword() {
        Toast.makeText(this, "Navigating to Change Password", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(EditProfileActivity.this, ForgotPassword.class);
        startActivity(intent);
    }
}