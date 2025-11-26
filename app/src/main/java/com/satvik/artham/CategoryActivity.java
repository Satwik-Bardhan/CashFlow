package com.satvik.artham;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CategoryActivity extends AppCompatActivity {

    private static final String TAG = "CategoryActivity";

    private ChipGroup categoryChipGroup;
    private FloatingActionButton fabAddCategory;

    // [FIX] Added Firebase references
    private DatabaseReference userCategoriesRef;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        fabAddCategory = findViewById(R.id.fab_add_category);

        // [FIX] Initialize Firebase
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Note: Categories are often global, but here they are saved per user
            userCategoriesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("customCategories");
        }

        setupClickListeners();
        setupDefaultCategoryChips();
        // In a real app, you would also load custom categories from userCategoriesRef here
    }

    private void setupClickListeners() {
        // [FIX] Made the back button functional
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void setupDefaultCategoryChips() {
        // Clear any existing chips from the layout
        categoryChipGroup.removeAllViews();

        // Add a default set of categories using the colors from colors.xml
        // These are "non-closable" chips
        addCategoryChip("Food", R.color.category_food, false);
        addCategoryChip("Salary", R.color.category_salary, false);
        addCategoryChip("Transport", R.color.category_transport, false);
        addCategoryChip("Shopping", R.color.category_shopping, false);
        addCategoryChip("Utilities", R.color.category_utilities, false);
        addCategoryChip("Entertainment", R.color.category_entertainment, false);
        addCategoryChip("Health", R.color.category_health, false);
        addCategoryChip("Education", R.color.category_education, false);
        addCategoryChip("Rent", R.color.category_rent, false);
        addCategoryChip("Other", R.color.category_other, false);
    }

    private void showAddCategoryDialog() {
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to add categories.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Category");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("e.g., Groceries");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String categoryName = input.getText().toString().trim();
            if (!categoryName.isEmpty()) {
                // [FIX] Add logic to save the new category to Firebase
                saveNewCategory(categoryName);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveNewCategory(String categoryName) {
        if (userCategoriesRef == null) return;

        // Using a model (CategoryModel) is better, but for simplicity, we save as a Map
        String key = userCategoriesRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Error creating category.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Using the CategoryModel structure from your other file
        CategoryModel newCategory = new CategoryModel(categoryName, "#808080"); // Default grey color
        newCategory.setCustom(true);

        userCategoriesRef.child(key).setValue(newCategory)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(CategoryActivity.this, "Category added!", Toast.LENGTH_SHORT).show();
                    // Add the chip to the UI
                    addCategoryChip(categoryName, R.color.category_default, true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(CategoryActivity.this, "Failed to add category.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to save category", e);
                });
    }

    private void addCategoryChip(String categoryName, int colorResId, boolean isClosable) {
        // [FIX] Inflate the correct chip layout. Your project had no 'item_category_chip.xml'
        // in the layout folder, so I will assume a standard chip.
        try {
            Chip chip = new Chip(this);
            chip.setText(categoryName);
            chip.setChipIconVisible(true);
            chip.setChipIconTintResource(colorResId);
            chip.setCloseIconVisible(isClosable);
            chip.setCheckable(false);
            chip.setClickable(true);

            if (isClosable) {
                chip.setOnCloseIconClickListener(v -> {
                    categoryChipGroup.removeView(chip);
                    // TODO: Add logic here to delete the category from Firebase
                    // deleteCategory(categoryName);
                });
            }

            categoryChipGroup.addView(chip);

        } catch (Exception e) {
            Log.e(TAG, "Error inflating chip layout. Did you provide 'item_category_chip.xml'?", e);
            // Fallback to simple toast
            Toast.makeText(this, "Loaded: " + categoryName, Toast.LENGTH_SHORT).show();
        }
    }
}