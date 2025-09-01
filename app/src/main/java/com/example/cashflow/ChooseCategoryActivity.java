package com.example.cashflow;

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

    private String selectedCategoryName = "";

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

        selectedCategoryName = getIntent().getStringExtra("selected_category_name");
        if (isNoCategory(selectedCategoryName)) {
            radioNoCategory.setChecked(true);
        }
    }

    private void initializeUI() {
        radioNoCategory = findViewById(R.id.radioNoCategory);
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        addNewCategoryButton = findViewById(R.id.add_new_category_button);
    }

    private void setupRecyclerView() {
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(allCategories, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        addNewCategoryButton.setOnClickListener(v -> showAddCategoryDialog());

        radioNoCategory.setOnClickListener(v -> {
            if (radioNoCategory.isChecked()) {
                categoryAdapter.clearSelection();
                returnCategory("No Category", String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default))));
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
            populatePredefinedCategoriesOnly();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && categoriesListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).child("customCategories").removeEventListener(categoriesListener);
        }
    }

    private void populatePredefinedCategoriesOnly() {
        allCategories.clear();
        String[] predefinedNames = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefinedNames) {
            if (!"Select Category".equals(name)) {
                int colorInt = CategoryColorUtil.getCategoryColor(this, name);
                String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));
                allCategories.add(new CategoryModel(name, colorHex, false));
            }
        }
        categoryAdapter.notifyDataSetChanged();
        selectInitialCategory();
    }

    private void startListeningForCategories(String userId) {
        DatabaseReference customCategoriesRef = mDatabase.child("users").child(userId).child("customCategories");
        categoriesListener = customCategoriesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                populatePredefinedCategoriesOnly(); // Start with a fresh list of predefined
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
                Toast.makeText(ChooseCategoryActivity.this, "Failed to load custom categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectInitialCategory() {
        if (!isNoCategory(selectedCategoryName)) {
            for (int i = 0; i < allCategories.size(); i++) {
                if (allCategories.get(i).getName().equals(selectedCategoryName)) {
                    categoryAdapter.setSelectedPosition(i);
                    radioNoCategory.setChecked(false);
                    return;
                }
            }
        }
    }

    @Override
    public void onCategoryClick(CategoryModel category) {
        returnCategory(category.getName(), category.getColorHex());
    }

    private void returnCategory(String name, String colorHex) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_category_name", name);
        resultIntent.putExtra("selected_category_color_hex", colorHex);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showAddCategoryDialog() {
        final EditText categoryNameEditText = new EditText(this);
        categoryNameEditText.setHint("New Category Name");

        final int[] selectedColor = {ContextCompat.getColor(this, R.color.category_default)};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Category");
        builder.setView(categoryNameEditText);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String newCategoryName = categoryNameEditText.getText().toString().trim();
            if (!newCategoryName.isEmpty()) {
                showColorPickerDialog(newCategoryName);
            } else {
                Toast.makeText(this, "Category name cannot be empty.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
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
                    addNewCategory(categoryName, colorHex);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .build()
                .show();
    }

    private void addNewCategory(String name, String colorHex) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to add categories.", Toast.LENGTH_SHORT).show();
            return;
        }

        CategoryModel newCategory = new CategoryModel(name, colorHex, true);
        mDatabase.child("users").child(currentUser.getUid()).child("customCategories").push().setValue(newCategory)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Category added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add category.", Toast.LENGTH_LONG).show());
    }

    private boolean isNoCategory(String name) {
        return name == null || name.isEmpty() || "No Category".equals(name) || "Select Category".equals(name);
    }
}
