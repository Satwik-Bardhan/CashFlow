package com.example.cashflow;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView profileImageView, editProfilePictureButton, backButton;
    private EditText editFullName, editPhoneNumber;
    private TextView displayEmail, dateOfBirthText;
    private LinearLayout dateOfBirthLayout, changePasswordLayout;
    private Switch twoFactorSwitch;
    private Button cancelButton, saveProfileButton;

    private FirebaseAuth mAuth;
    private DatabaseReference userDatabaseRef;
    private StorageReference storageReference;
    private FirebaseUser currentUser;

    private Calendar dobCalendar;
    private Uri imageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    imageUri = result.getData().getData();
                    profileImageView.setImageURI(imageUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // No user is signed in, so finish this activity
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        storageReference = FirebaseStorage.getInstance().getReference("profile_pictures");

        initializeUI();
        setupClickListeners();
        loadUserProfile();
    }

    private void initializeUI() {
        profileImageView = findViewById(R.id.profileImageView);
        editProfilePictureButton = findViewById(R.id.editProfilePictureButton);
        backButton = findViewById(R.id.backButton);
        editFullName = findViewById(R.id.editFullName);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);
        displayEmail = findViewById(R.id.displayEmail);
        dateOfBirthText = findViewById(R.id.dateOfBirthText);
        dateOfBirthLayout = findViewById(R.id.dateOfBirthLayout);
        changePasswordLayout = findViewById(R.id.changePasswordLayout);
        twoFactorSwitch = findViewById(R.id.twoFactorSwitch);
        cancelButton = findViewById(R.id.cancelButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);

        dobCalendar = Calendar.getInstance();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());
        editProfilePictureButton.setOnClickListener(v -> openImagePicker());
        dateOfBirthLayout.setOnClickListener(v -> showDatePicker());
        saveProfileButton.setOnClickListener(v -> saveProfileChanges());

        // Placeholder listeners for features to be implemented
        changePasswordLayout.setOnClickListener(v -> Toast.makeText(this, "Change Password clicked", Toast.LENGTH_SHORT).show());
        twoFactorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> Toast.makeText(this, "2FA Toggled: " + isChecked, Toast.LENGTH_SHORT).show());
    }

    private void loadUserProfile() {
        // Set email (it's read-only)
        displayEmail.setText(currentUser.getEmail());

        // Load other data from Realtime Database
        userDatabaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                if (user != null) {
                    editFullName.setText(user.getUserName());
                    // You'll need to add phone and dob to your Users model to load them
                    // editPhoneNumber.setText(user.getPhoneNumber());
                    // updateDobText(user.getDateOfBirthTimestamp());

                    // Load profile image using Glide
                    if (user.getProfile() != null && !user.getProfile().isEmpty()) {
                        Glide.with(EditProfileActivity.this)
                                .load(user.getProfile())
                                .placeholder(R.drawable.ic_person_placeholder)
                                .into(profileImageView);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDatePicker() {
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    dobCalendar.set(year, month, dayOfMonth);
                    updateDobText(dobCalendar.getTimeInMillis());
                },
                dobCalendar.get(Calendar.YEAR),
                dobCalendar.get(Calendar.MONTH),
                dobCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDobText(long timestamp) {
        if (timestamp > 0) {
            dobCalendar.setTimeInMillis(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
            dateOfBirthText.setText(sdf.format(dobCalendar.getTime()));
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void saveProfileChanges() {
        String fullName = editFullName.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            editFullName.setError("Full name is required.");
            editFullName.requestFocus();
            return;
        }

        if (imageUri != null) {
            // If a new image was selected, upload it first
            uploadImageAndSaveData(fullName);
        } else {
            // Otherwise, just save the other data
            saveDataToDatabase(fullName, null);
        }
    }

    private void uploadImageAndSaveData(String fullName) {
        final StorageReference fileReference = storageReference.child(currentUser.getUid() + "/" + UUID.randomUUID().toString());
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    saveDataToDatabase(fullName, imageUrl);
                }))
                .addOnFailureListener(e -> Toast.makeText(EditProfileActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void saveDataToDatabase(String fullName, @Nullable String imageUrl) {
        Map<String, Object> profileUpdates = new HashMap<>();
        profileUpdates.put("userName", fullName);
        // Add other fields to save here
        // profileUpdates.put("phoneNumber", editPhoneNumber.getText().toString());
        // profileUpdates.put("dateOfBirthTimestamp", dobCalendar.getTimeInMillis());

        if (imageUrl != null) {
            profileUpdates.put("profile", imageUrl);
        }

        userDatabaseRef.updateChildren(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(EditProfileActivity.this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
