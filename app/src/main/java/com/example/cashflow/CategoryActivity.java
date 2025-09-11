package com.example.cashflow;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CategoryActivity extends AppCompatActivity {

    private ChipGroup categoryChipGroup;
    private FloatingActionButton fabAddCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        fabAddCategory = findViewById(R.id.fab_add_category);

        setupClickListeners();
        setupDefaultCategoryChips();
    }

    private void setupClickListeners() {
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void setupDefaultCategoryChips() {
        // Clear any existing chips from the layout
        categoryChipGroup.removeAllViews();

        // Add a default set of categories using the colors from colors.xml
        addCategoryChip("Food", R.color.category_food);
        addCategoryChip("Salary", R.color.category_salary);
        addCategoryChip("Transport", R.color.category_transport);
        addCategoryChip("Shopping", R.color.category_shopping);
        addCategoryChip("Utilities", R.color.category_utilities);
        addCategoryChip("Entertainment", R.color.category_entertainment);
        addCategoryChip("Health", R.color.category_health);
        addCategoryChip("Education", R.color.category_education);
        addCategoryChip("Rent", R.color.category_rent);
        addCategoryChip("Freelance", R.color.category_freelance_income);
        addCategoryChip("Other", R.color.category_other);
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Category");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("e.g., Groceries");
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Add", (dialog, which) -> {
            String categoryName = input.getText().toString().trim();
            if (!categoryName.isEmpty()) {
                // Add the new category with a default color
                addCategoryChip(categoryName, R.color.category_default);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void addCategoryChip(String categoryName, int colorResId) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, categoryChipGroup, false);
        chip.setText(categoryName);
        chip.setChipIconTintResource(colorResId);

        // Set an action to remove the chip when the close icon is clicked
        chip.setOnCloseIconClickListener(v -> {
            categoryChipGroup.removeView(chip);
            // TODO: Add logic here to delete the category from Firebase/database
        });

        // Add the configured chip to the group
        categoryChipGroup.addView(chip);
    }
}
