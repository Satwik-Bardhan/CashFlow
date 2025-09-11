package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cashflow.utils.CategoryColorUtil;
import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChooseCategoryActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    private static final String TAG = "ChooseCategoryActivity";

    private RadioButton radioNoCategory;
    private RecyclerView categoriesRecyclerView;
    private ExtendedFloatingActionButton addNewCategoryButton;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener categoriesListener;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private CategoryAdapter categoryAdapter;

    private String previouslySelectedCategoryName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_category);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupRecyclerView();
        setupListeners();

        // Get the category name passed from the previous activity
        previouslySelectedCategoryName = getIntent().getStringExtra("selected_category");
        if (isNoCategory(previouslySelectedCategoryName)) {
            radioNoCategory.setChecked(true);
        }
    }

    private void initializeUI() {
        radioNoCategory = findViewById(R.id.radioNoCategory);
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        addNewCategoryButton = findViewById(R.id.addNewCategoryButton);
    }

    private void setupRecyclerView() {
        // Use a grid layout with 2 columns
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(allCategories, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        addNewCategoryButton.setOnClickListener(v -> showAddCategoryDialog());

        radioNoCategory.setOnClickListener(v -> {
            if (radioNoCategory.isChecked()) {
                // Return a special "No Category" value
                returnCategory("No Category");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForCategories(currentUser.getUid());
        } else {
            // If user is a guest, only show the predefined categories
            populatePredefinedCategoriesOnly();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && categoriesListener != null) {
            // Remove listener to prevent memory leaks
            mDatabase.child("users").child(currentUser.getUid()).child("customCategories").removeEventListener(categoriesListener);
        }
    }

    private void populatePredefinedCategoriesOnly() {
        allCategories.clear();
        String[] predefinedNames = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefinedNames) {
            if (!"Select Category".equals(name)) { // Ignore placeholder
                int colorInt = CategoryColorUtil.getCategoryColor(this, name);
                String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));
                allCategories.add(new CategoryModel(name, colorHex));
            }
        }
        categoryAdapter.notifyDataSetChanged();
        selectInitialCategory();
    }

    private void startListeningForCategories(String userId) {
        DatabaseReference customCategoriesRef = mDatabase.child("users").child(userId).child("customCategories");
        categoriesListener = customCategoriesRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                populatePredefinedCategoriesOnly(); // Always start with the base list
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CategoryModel customCategory = snapshot.getValue(CategoryModel.class);
                    if (customCategory != null) {
                        allCategories.add(customCategory);
                    }
                }
                categoryAdapter.notifyDataSetChanged();
                selectInitialCategory();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(ChooseCategoryActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectInitialCategory() {
        if (!isNoCategory(previouslySelectedCategoryName)) {
            for (CategoryModel category : allCategories) {
                if (category.getName().equals(previouslySelectedCategoryName)) {
                    categoryAdapter.setSelectedCategory(category);
                    radioNoCategory.setChecked(false);
                    return;
                }
            }
        }
    }

    @Override
    public void onCategoryClick(CategoryModel category) {
        returnCategory(category.getName());
    }

    private void returnCategory(String name) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_category", name);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showAddCategoryDialog() {
        final EditText categoryNameEditText = new EditText(this);
        categoryNameEditText.setHint("Category Name");
        categoryNameEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        new AlertDialog.Builder(this)
                .setTitle("Add New Category")
                .setView(categoryNameEditText)
                .setPositiveButton("Choose Color", (dialog, which) -> {
                    String newCategoryName = categoryNameEditText.getText().toString().trim();
                    if (!newCategoryName.isEmpty()) {
                        showColorPickerDialog(newCategoryName);
                    } else {
                        Toast.makeText(this, "Category name cannot be empty.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showColorPickerDialog(String categoryName) {
        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Choose Category Color")
                .initialColor(ContextCompat.getColor(this, R.color.category_default))
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setPositiveButton("OK", (dialog, chosenColor, allColors) -> {
                    String colorHex = String.format("#%06X", (0xFFFFFF & chosenColor));
                    addNewCategoryToFirebase(categoryName, colorHex);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .build()
                .show();
    }

    private void addNewCategoryToFirebase(String name, String colorHex) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to add categories.", Toast.LENGTH_SHORT).show();
            return;
        }

        CategoryModel newCategory = new CategoryModel(name, colorHex);
        mDatabase.child("users").child(currentUser.getUid()).child("customCategories").push().setValue(newCategory)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Category '" + name + "' added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add category.", Toast.LENGTH_LONG).show());
    }

    private boolean isNoCategory(String name) {
        return name == null || name.isEmpty() || "No Category".equals(name) || "Select Category".equals(name);
    }
}
