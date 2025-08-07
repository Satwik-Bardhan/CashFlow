package com.example.cashflow;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private EditText editUserName;
    private TextView displayEmail;
    private EditText editProfilePictureUrl;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private ValueEventListener userProfileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to edit your profile.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeUI();
        setupClickListeners();
    }

    private void initializeUI() {
        editUserName = findViewById(R.id.editUserName);
        displayEmail = findViewById(R.id.displayEmail);
        editProfilePictureUrl = findViewById(R.id.editProfilePictureUrl);
        displayEmail.setText(currentUser.getEmail());
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.changePasswordButton).setOnClickListener(v -> navigateToChangePassword());
        findViewById(R.id.saveProfileChangesButton).setOnClickListener(v -> saveProfileChanges());
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
        }
    }

    private void startListeningForUserProfile(String userId) {
        DatabaseReference userRef = mDatabase.child("users").child(userId);
        userProfileListener = userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Users userProfile = dataSnapshot.getValue(Users.class);
                if (userProfile != null) {
                    editUserName.setText(userProfile.getUserName());
                    editProfilePictureUrl.setText(userProfile.getProfile());
                } else {
                    // Fallback to Firebase Auth data if database profile is missing
                    editUserName.setText(currentUser.getDisplayName());
                    if (currentUser.getPhotoUrl() != null) {
                        editProfilePictureUrl.setText(currentUser.getPhotoUrl().toString());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(EditProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfileChanges() {
        String newUserName = editUserName.getText().toString().trim();
        String newProfileUrl = editProfilePictureUrl.getText().toString().trim();

        if (TextUtils.isEmpty(newUserName)) {
            editUserName.setError("User Name cannot be empty");
            return;
        }

        // Step 1: Update Firebase Authentication profile
        UserProfileChangeRequest.Builder profileUpdatesBuilder = new UserProfileChangeRequest.Builder()
                .setDisplayName(newUserName);

        if (!TextUtils.isEmpty(newProfileUrl)) {
            profileUpdatesBuilder.setPhotoUri(Uri.parse(newProfileUrl));
        }

        currentUser.updateProfile(profileUpdatesBuilder.build())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Step 2: Update Realtime Database
                        updateUserInDatabase(newUserName, newProfileUrl);
                    } else {
                        Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateUserInDatabase(String newUserName, String newProfileUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("userName", newUserName);
        updates.put("profile", newProfileUrl);

        mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save profile to database.", Toast.LENGTH_LONG).show());
    }

    private void navigateToChangePassword() {
        Intent intent = new Intent(this, ForgotPassword.class);
        intent.putExtra("email", currentUser.getEmail());
        startActivity(intent);
    }
}
