package com.example.cashflow; // Your actual package name

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class CategoryActivity extends AppCompatActivity {

    private ChipGroup categoryChipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category); // Your actual layout name

        // 1. Find the ChipGroup by ID
        categoryChipGroup = findViewById(R.id.categoryChipGroup);

        // 2. Add chips dynamically
        setupCategoryChips();
    }

    private void setupCategoryChips() {
        // Add your category chips here
        addCategoryChip("Food", R.color.red);
        addCategoryChip("Travel", R.color.blue);
        addCategoryChip("Shopping", R.color.green);
        addCategoryChip("Entertainment", R.color.purple);
    }

    private void addCategoryChip(String categoryName, int colorResId) {
        Chip chip = new Chip(this);
        chip.setText(categoryName);
        chip.setChipBackgroundColorResource(R.color.chip_background_selector);
        chip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        chip.setChipIconResource(R.drawable.ic_circle);
        chip.setChipIconTintResource(colorResId);
        chip.setCheckable(true);
        chip.setClickable(true);

        // Optional: Add close icon
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> categoryChipGroup.removeView(chip));

        // Add chip to the group
        categoryChipGroup.addView(chip);
    }
}
