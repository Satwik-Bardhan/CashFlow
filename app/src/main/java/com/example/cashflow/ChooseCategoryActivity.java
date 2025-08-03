package com.example.cashflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerClickListener;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import android.app.AlertDialog;
import android.content.DialogInterface; // Ensure this import is present

public class ChooseCategoryActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    private static final String TAG = "ChooseCategoryActivity";

    private ImageView backButton;
    private RadioButton radioNoCategory;
    private RecyclerView categoriesRecyclerView;
    private ExtendedFloatingActionButton addNewCategoryButton;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener categoriesListener;

    private List<CategoryModel> allCategories = new ArrayList<>();
    private CategoryAdapter categoryAdapter;

    private String selectedCategoryName = "";
    private String selectedCategoryColorHex = "";

    private String[] predefinedCategoryNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_category);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        backButton = findViewById(R.id.backButton);
        radioNoCategory = findViewById(R.id.radioNoCategory);
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView);
        addNewCategoryButton = findViewById(R.id.add_new_category_button);

        predefinedCategoryNames = getResources().getStringArray(R.array.transaction_categories);
        List<String> tempPredefined = new ArrayList<>(Arrays.asList(predefinedCategoryNames));
        if (tempPredefined.contains("Select Category")) {
            tempPredefined.remove("Select Category");
        }
        predefinedCategoryNames = tempPredefined.toArray(new String[0]);


        setupRecyclerView();
        setupListeners();

        if (getIntent() != null) {
            selectedCategoryName = getIntent().getStringExtra("selected_category_name");
            selectedCategoryColorHex = getIntent().getStringExtra("selected_category_color_hex");
            Log.d(TAG, "Initial category: " + selectedCategoryName + ", Color: " + selectedCategoryColorHex);
        }

        if (selectedCategoryName == null || selectedCategoryName.isEmpty() || selectedCategoryName.equals("No Category") || selectedCategoryName.equals("Select Category")) {
            radioNoCategory.setChecked(true);
            selectedCategoryName = "No Category";
            selectedCategoryColorHex = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
        } else {
            radioNoCategory.setChecked(false);
        }
    }

    private void setupRecyclerView() {
        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(allCategories, this, this);
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());

        radioNoCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedCategoryName = "No Category";
                selectedCategoryColorHex = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
                categoryAdapter.clearSelection();
                Log.d(TAG, "Selected: No Category");
            }
        });

        addNewCategoryButton.setOnClickListener(v -> showAddCategoryDialog());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForCategories(currentUser.getUid());
        } else {
            populatePredefinedCategories();
            Log.d(TAG, "Guest user: Only predefined categories loaded.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && categoriesListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).child("customCategories").removeEventListener(categoriesListener);
            Log.d(TAG, "Firebase categories listener removed in onStop.");
        }
    }

    private void populatePredefinedCategories() {
        allCategories.clear();
        for (String name : predefinedCategoryNames) {
            int colorInt = CategoryColorUtil.getCategoryColor(this, name);
            String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));
            allCategories.add(new CategoryModel(name, colorHex, false));
        }
        categoryAdapter.notifyDataSetChanged();
        selectInitialCategoryInChips();
    }

    private void startListeningForCategories(String userId) {
        if (categoriesListener != null) {
            mDatabase.child("users").child(userId).child("customCategories").removeEventListener(categoriesListener);
        }

        categoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allCategories.clear();
                populatePredefinedCategories();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CategoryModel customCategory = snapshot.getValue(CategoryModel.class);
                    if (customCategory != null) {
                        allCategories.add(customCategory);
                    }
                }
                categoryAdapter.notifyDataSetChanged();
                selectInitialCategoryInChips();
                Log.d(TAG, "Categories loaded: " + allCategories.size() + " (predefined + custom)");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "onCancelled: Failed to load custom categories: " + databaseError.getMessage(), databaseError.toException());
                Toast.makeText(ChooseCategoryActivity.this, "Failed to load custom categories.", Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.child("users").child(userId).child("customCategories").addValueEventListener(categoriesListener);
    }

    private void selectInitialCategoryInChips() {
        if (selectedCategoryName != null && !selectedCategoryName.isEmpty() && !selectedCategoryName.equals("No Category") && !selectedCategoryName.equals("Select Category")) {
            int positionToSelect = -1;
            for (int i = 0; i < allCategories.size(); i++) {
                if (allCategories.get(i).getName().equals(selectedCategoryName)) {
                    positionToSelect = i;
                    break;
                }
            }
            if (positionToSelect != -1) {
                categoryAdapter.setSelectedPosition(positionToSelect);
                radioNoCategory.setChecked(false);
            } else {
                radioNoCategory.setChecked(true);
            }
        }
    }

    @Override
    public void onCategoryClick(CategoryModel category) {
        radioNoCategory.setChecked(false);
        selectedCategoryName = category.getName();
        selectedCategoryColorHex = category.getColorHex();
        Log.d(TAG, "Category clicked: " + selectedCategoryName + ", Color: " + selectedCategoryColorHex);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_category_name", selectedCategoryName);
        resultIntent.putExtra("selected_category_color_hex", selectedCategoryColorHex);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showAddCategoryDialog() {
        final EditText categoryNameEditText = new EditText(this);
        categoryNameEditText.setHint("New Category Name");
        categoryNameEditText.setTextColor(Color.BLACK);
        categoryNameEditText.setHintTextColor(Color.GRAY);
        categoryNameEditText.setBackgroundResource(R.drawable.rounded_white);
        categoryNameEditText.setPadding(30, 30, 30, 30);

        final int[] selectedColor = {ContextCompat.getColor(this, R.color.category_default)};

        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Choose Category Color")
                .initialColor(selectedColor[0])
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .lightnessSliderOnly()
                .setPositiveButton("OK", new ColorPickerClickListener() {
                    @Override
                    // CORRECTED: Changed 'Object dialog' to 'DialogInterface dialog'
                    public void onClick(DialogInterface dialog, int chosenColor, Integer[] allColors) {
                        selectedColor[0] = chosenColor;
                        showCategoryNameAndColorConfirmationDialog(categoryNameEditText, chosenColor);
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .build()
                .show();
    }

    private void showCategoryNameAndColorConfirmationDialog(EditText categoryNameEditText, int chosenColor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Category");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 0);

        layout.addView(categoryNameEditText);

        TextView colorPreviewLabel = new TextView(this);
        colorPreviewLabel.setText("Selected Color:");
        colorPreviewLabel.setTextColor(Color.BLACK);
        colorPreviewLabel.setPadding(0, 20, 0, 0);
        layout.addView(colorPreviewLabel);

        View colorPreview = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(50, 50);
        params.topMargin = 10;
        colorPreview.setLayoutParams(params);
        colorPreview.setBackgroundColor(chosenColor);
        layout.addView(colorPreview);

        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String newCategoryName = categoryNameEditText.getText().toString().trim();
            if (!newCategoryName.isEmpty()) {
                String colorHex = String.format("#%06X", (0xFFFFFF & chosenColor));
                addNewCategory(newCategoryName, colorHex);
            } else {
                Toast.makeText(ChooseCategoryActivity.this, "Category name cannot be empty.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }


    private void addNewCategory(String name, String colorHex) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to add custom categories.", Toast.LENGTH_SHORT).show();
            return;
        }

        CategoryModel newCustomCategory = new CategoryModel(name, colorHex, true);
        mDatabase.child("users").child(currentUser.getUid()).child("customCategories").push().setValue(newCustomCategory)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ChooseCategoryActivity.this, "Category added!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add custom category: " + e.getMessage(), e);
                    Toast.makeText(ChooseCategoryActivity.this, "Failed to add category: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}