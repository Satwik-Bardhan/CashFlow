package com.example.cashflow;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class FiltersActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView filterDate, filterEntryType, filterParty, filterCategory, filterPaymentMode;
    private View selectedView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filters);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        initializeUI();
        setupClickListeners();

        // Load the default filter (Date)
        filterDate.performClick();
    }

    private void initializeUI() {
        filterDate = findViewById(R.id.filter_date);
        filterEntryType = findViewById(R.id.filter_entry_type);
        filterParty = findViewById(R.id.filter_party);
        filterCategory = findViewById(R.id.filter_category);
        filterPaymentMode = findViewById(R.id.filter_payment_mode);
    }

    private void setupClickListeners() {
        filterDate.setOnClickListener(this);
        filterEntryType.setOnClickListener(this);
        filterParty.setOnClickListener(this);
        filterCategory.setOnClickListener(this);
        filterPaymentMode.setOnClickListener(this);

        findViewById(R.id.apply_button).setOnClickListener(v -> {
            // Logic to apply filters and return to TransactionActivity will be added here
            finish();
        });

        findViewById(R.id.clear_all_button).setOnClickListener(v -> {
            // Logic to clear all filters will be added here
        });
    }

    @Override
    public void onClick(View v) {
        // Reset the previously selected view's background
        if (selectedView != null) {
            selectedView.setBackgroundResource(android.R.color.transparent);
        }

        // Set the new selected view and highlight it
        selectedView = v;
        v.setBackgroundColor(getResources().getColor(android.R.color.white));

        Fragment fragment = null;
        int viewId = v.getId();

        if (viewId == R.id.filter_date) {
            // fragment = new DateFilterFragment(); // We will create this next
        } else if (viewId == R.id.filter_entry_type) {
            // fragment = new EntryTypeFilterFragment(); // We will create this next
        }
        // ... and so on for other filters

        if (fragment != null) {
            loadFragment(fragment);
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.filter_content_frame, fragment);
        ft.commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
