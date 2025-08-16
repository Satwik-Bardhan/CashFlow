package com.example.cashflow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FiltersActivity extends AppCompatActivity implements View.OnClickListener,
        DateFilterFragment.DateFilterListener,
        EntryTypeFilterFragment.EntryTypeListener,
        CategoryFilterFragment.CategoryFilterListener,
        PaymentModeFilterFragment.PaymentModeListener {

    private TextView filterDate, filterEntryType, filterParty, filterCategory, filterPaymentMode;
    private View selectedView;

    // Variables to hold the selected filter values
    private long startDate = 0;
    private long endDate = 0;
    private String entryType = "All";
    private Set<String> selectedCategories = new HashSet<>();
    private Set<String> selectedPaymentModes = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filters);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        initializeUI();
        unpackIntent();
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

    // Get any existing filters passed from TransactionActivity
    private void unpackIntent() {
        Intent intent = getIntent();
        startDate = intent.getLongExtra("startDate", 0);
        endDate = intent.getLongExtra("endDate", 0);
        entryType = intent.getStringExtra("entryType");
        ArrayList<String> categories = intent.getStringArrayListExtra("categories");
        if (categories != null) {
            selectedCategories = new HashSet<>(categories);
        }
        ArrayList<String> modes = intent.getStringArrayListExtra("paymentModes");
        if (modes != null) {
            selectedPaymentModes = new HashSet<>(modes);
        }
    }

    private void setupClickListeners() {
        filterDate.setOnClickListener(this);
        filterEntryType.setOnClickListener(this);
        filterParty.setOnClickListener(this);
        filterCategory.setOnClickListener(this);
        filterPaymentMode.setOnClickListener(this);

        findViewById(R.id.apply_button).setOnClickListener(v -> applyFiltersAndFinish());
        findViewById(R.id.clear_all_button).setOnClickListener(v -> clearAllFilters());
    }

    @Override
    public void onClick(View v) {
        if (selectedView != null) {
            selectedView.setBackgroundResource(android.R.color.transparent);
        }
        selectedView = v;
        v.setBackgroundColor(getResources().getColor(android.R.color.white));

        Fragment fragment = null;
        int viewId = v.getId();

        if (viewId == R.id.filter_date) {
            fragment = DateFilterFragment.newInstance(startDate, endDate);
        } else if (viewId == R.id.filter_entry_type) {
            fragment = EntryTypeFilterFragment.newInstance(entryType);
        } else if (viewId == R.id.filter_party) {
            fragment = new PartyFilterFragment(); // Party fragment needs similar logic
        } else if (viewId == R.id.filter_category) {
            fragment = CategoryFilterFragment.newInstance(new ArrayList<>(selectedCategories));
        } else if (viewId == R.id.filter_payment_mode) {
            fragment = PaymentModeFilterFragment.newInstance(new ArrayList<>(selectedPaymentModes));
        }

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

    private void applyFiltersAndFinish() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("startDate", startDate);
        resultIntent.putExtra("endDate", endDate);
        resultIntent.putExtra("entryType", entryType);
        resultIntent.putStringArrayListExtra("categories", new ArrayList<>(selectedCategories));
        resultIntent.putStringArrayListExtra("paymentModes", new ArrayList<>(selectedPaymentModes));
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void clearAllFilters() {
        // Reset all filter variables
        startDate = 0;
        endDate = 0;
        entryType = "All";
        selectedCategories.clear();
        selectedPaymentModes.clear();

        // Reload the current fragment to reset its UI
        if(selectedView != null) {
            onClick(selectedView);
        }
        Toast.makeText(this, "Filters Cleared", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // --- Listener Implementations ---
    @Override
    public void onDateRangeSelected(long startDate, long endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public void onEntryTypeSelected(String entryType) {
        this.entryType = entryType;
    }

    @Override
    public void onCategoriesSelected(Set<String> categories) {
        this.selectedCategories = categories;
    }

    @Override
    public void onPaymentModesSelected(Set<String> modes) {
        this.selectedPaymentModes = modes;
    }
}
