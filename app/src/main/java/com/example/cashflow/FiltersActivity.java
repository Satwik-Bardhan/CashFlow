package com.example.cashflow;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * FiltersActivity - Advanced transaction filtering
 *
 * Features:
 * - Date range filtering (Today, Week, Month, Custom)
 * - Transaction type filtering (Income/Expense)
 * - Payment mode filtering (Cash/Online)
 * - Category filtering
 * - Search by remarks
 * - Tag filtering
 *
 * Updated: November 2025 - Added null safety and improved UX
 */
public class FiltersActivity extends AppCompatActivity {

    private static final String TAG = "FiltersActivity";

    // UI Elements
    private ImageView backButton, resetButton;
    private Button filterTodayButton, filterWeekButton, filterMonthButton, clearAllButton, applyFiltersButton;
    private LinearLayout startDateLayout, endDateLayout;
    private TextView startDateText, endDateText, activeFiltersCount, selectedCategoryTextView;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private EditText searchTransactionInput, filterTagsInput;
    private LinearLayout categorySelectorLayout, partySelectorLayout;

    // Filter State
    private Calendar startCalendar, endCalendar;
    private String entryType = "All"; // "All", "IN", "OUT"
    private String paymentMode = "All"; // "All", "Cash", "Online"
    private Set<String> selectedCategories = new HashSet<>();

    private ActivityResultLauncher<Intent> categoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_filters);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUI();
        receiveInitialFilters();
        setupCategoryLauncher();
        setupClickListeners();
        updateUIWithCurrentFilters();

        Log.d(TAG, "FiltersActivity created");
    }

    /**
     * Initialize all UI components with null safety
     */
    private void initializeUI() {
        // Header
        backButton = findViewById(R.id.backButton);
        resetButton = findViewById(R.id.resetButton);

        // Search
        searchTransactionInput = findViewById(R.id.searchTransactionInput);

        // Date Filters
        filterTodayButton = findViewById(R.id.filterTodayButton);
        filterWeekButton = findViewById(R.id.filterWeekButton);
        filterMonthButton = findViewById(R.id.filterMonthButton);
        startDateLayout = findViewById(R.id.startDateLayout);
        endDateLayout = findViewById(R.id.endDateLayout);
        startDateText = findViewById(R.id.startDateText);
        endDateText = findViewById(R.id.endDateText);

        // Type & Method Filters
        inOutToggle = findViewById(R.id.inOutToggle);
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);

        // Category & Party (with null safety)
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);

        // Tags
        filterTagsInput = findViewById(R.id.filterTagsInput);

        // Bottom Bar
        activeFiltersCount = findViewById(R.id.activeFiltersCount);
        clearAllButton = findViewById(R.id.clearAllButton);
        applyFiltersButton = findViewById(R.id.applyFiltersButton);

        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
    }

    /**
     * Receive initial filter values from intent
     */
    private void receiveInitialFilters() {
        Intent intent = getIntent();
        long initialStartDate = intent.getLongExtra("startDate", 0);
        long initialEndDate = intent.getLongExtra("endDate", 0);

        if (initialStartDate > 0) {
            startCalendar.setTimeInMillis(initialStartDate);
        }
        if (initialEndDate > 0) {
            endCalendar.setTimeInMillis(initialEndDate);
        }

        entryType = intent.getStringExtra("entryType");
        if (entryType == null) {
            entryType = "All";
        }

        ArrayList<String> categories = intent.getStringArrayListExtra("categories");
        if (categories != null) {
            selectedCategories = new HashSet<>(categories);
        }

        Log.d(TAG, "Initial filters received: entryType=" + entryType + ", categories=" + selectedCategories.size());
    }

    /**
     * Setup category launcher for activity result
     */
    private void setupCategoryLauncher() {
        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> returnedCategories = result.getData()
                                .getStringArrayListExtra("selected_categories");
                        if (returnedCategories != null) {
                            selectedCategories = new HashSet<>(returnedCategories);
                            updateUIWithCurrentFilters();
                            Log.d(TAG, "Categories updated: " + selectedCategories.size());
                        }
                    }
                }
        );
    }

    /**
     * Setup all click listeners with null checks
     */
    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        resetButton.setOnClickListener(v -> clearAllFilters());
        clearAllButton.setOnClickListener(v -> clearAllFilters());
        applyFiltersButton.setOnClickListener(v -> applyFiltersAndFinish());

        // Date Listeners
        filterTodayButton.setOnClickListener(v -> setDateRangeToToday());
        filterWeekButton.setOnClickListener(v -> setDateRangeToThisWeek());
        filterMonthButton.setOnClickListener(v -> setDateRangeToThisMonth());
        startDateLayout.setOnClickListener(v -> showDatePicker(true));
        endDateLayout.setOnClickListener(v -> showDatePicker(false));

        // Category Listener - with null check
        if (categorySelectorLayout != null) {
            categorySelectorLayout.setOnClickListener(v -> {
                Intent categoryIntent = new Intent(this, ChooseCategoryActivity.class);
                categoryIntent.putStringArrayListExtra("selected_categories",
                        new ArrayList<>(selectedCategories));
                categoryLauncher.launch(categoryIntent);
                Log.d(TAG, "Category selector opened");
            });
        }

        // Party Listener - with null check
        if (partySelectorLayout != null) {
            partySelectorLayout.setOnClickListener(v ->
                    showSnackbar("Party selection coming soon!"));
        }

        // Radio Group Listeners
        if (inOutToggle != null) {
            inOutToggle.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.radioIn) {
                    entryType = "IN";
                } else if (checkedId == R.id.radioOut) {
                    entryType = "OUT";
                }
                updateActiveFilterCount();
            });
        }

        if (cashOnlineToggle != null) {
            cashOnlineToggle.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.radioCash) {
                    paymentMode = "Cash";
                } else if (checkedId == R.id.radioOnline) {
                    paymentMode = "Online";
                }
                updateActiveFilterCount();
            });
        }
    }

    /**
     * Show date picker dialog
     */
    private void showDatePicker(boolean isStartDate) {
        Calendar calendarToShow = isStartDate ? startCalendar : endCalendar;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (isStartDate) {
                        startCalendar.set(year, month, dayOfMonth, 0, 0, 0);
                        Log.d(TAG, "Start date set to: " + year + "-" + (month + 1) + "-" + dayOfMonth);
                    } else {
                        endCalendar.set(year, month, dayOfMonth, 23, 59, 59);
                        Log.d(TAG, "End date set to: " + year + "-" + (month + 1) + "-" + dayOfMonth);
                    }
                    updateUIWithCurrentFilters();
                },
                calendarToShow.get(Calendar.YEAR),
                calendarToShow.get(Calendar.MONTH),
                calendarToShow.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * Set date range to today
     */
    private void setDateRangeToToday() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        updateUIWithCurrentFilters();
        showSnackbar("Filter set to Today");
        Log.d(TAG, "Date range set to Today");
    }

    /**
     * Set date range to this week
     */
    private void setDateRangeToThisWeek() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_WEEK, startCalendar.getFirstDayOfWeek());
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = (Calendar) startCalendar.clone();
        endCalendar.add(Calendar.DAY_OF_WEEK, 6);
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        updateUIWithCurrentFilters();
        showSnackbar("Filter set to This Week");
        Log.d(TAG, "Date range set to This Week");
    }

    /**
     * Set date range to this month
     */
    private void setDateRangeToThisMonth() {
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        updateUIWithCurrentFilters();
        showSnackbar("Filter set to This Month");
        Log.d(TAG, "Date range set to This Month");
    }

    /**
     * Clear all filters and reset to default state
     */
    private void clearAllFilters() {
        startCalendar.setTimeInMillis(0);
        endCalendar.setTimeInMillis(0);
        entryType = "All";
        paymentMode = "All";
        selectedCategories.clear();

        if (searchTransactionInput != null) {
            searchTransactionInput.setText("");
        }
        if (filterTagsInput != null) {
            filterTagsInput.setText("");
        }
        if (inOutToggle != null) {
            inOutToggle.clearCheck();
        }
        if (cashOnlineToggle != null) {
            cashOnlineToggle.clearCheck();
        }

        updateUIWithCurrentFilters();
        showSnackbar("All filters cleared");
        Log.d(TAG, "All filters cleared");
    }

    /**
     * Update UI to reflect current filter state
     */
    private void updateUIWithCurrentFilters() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        // Update start date display
        if (startCalendar.getTimeInMillis() != 0) {
            startDateText.setText(sdf.format(startCalendar.getTime()));
            startDateText.setTextColor(getColor(R.color.white));
        } else {
            startDateText.setText("Start Date");
            startDateText.setTextColor(getColor(R.color.text_secondary));
        }

        // Update end date display
        if (endCalendar.getTimeInMillis() != 0) {
            endDateText.setText(sdf.format(endCalendar.getTime()));
            endDateText.setTextColor(getColor(R.color.white));
        } else {
            endDateText.setText("End Date");
            endDateText.setTextColor(getColor(R.color.text_secondary));
        }

        // Update category display
        if (selectedCategoryTextView != null) {
            if (selectedCategories.isEmpty()) {
                selectedCategoryTextView.setText("Select Category");
            } else {
                selectedCategoryTextView.setText(selectedCategories.size() + " categories selected");
            }
        }

        updateActiveFilterCount();
    }

    /**
     * Update the active filters count badge
     */
    private void updateActiveFilterCount() {
        int count = 0;

        if (startCalendar.getTimeInMillis() != 0) count++;
        if (endCalendar.getTimeInMillis() != 0) count++;
        if (!"All".equals(entryType) && inOutToggle != null && inOutToggle.getCheckedRadioButtonId() != -1) {
            count++;
        }
        if (!"All".equals(paymentMode) && cashOnlineToggle != null && cashOnlineToggle.getCheckedRadioButtonId() != -1) {
            count++;
        }
        if (!selectedCategories.isEmpty()) count++;
        if (searchTransactionInput != null && !searchTransactionInput.getText().toString().trim().isEmpty()) {
            count++;
        }
        if (filterTagsInput != null && !filterTagsInput.getText().toString().trim().isEmpty()) {
            count++;
        }

        if (count > 0) {
            activeFiltersCount.setText(String.valueOf(count));
            activeFiltersCount.setVisibility(View.VISIBLE);
        } else {
            activeFiltersCount.setVisibility(View.GONE);
        }

        Log.d(TAG, "Active filter count: " + count);
    }

    /**
     * Apply filters and return to calling activity
     */
    private void applyFiltersAndFinish() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("startDate", startCalendar.getTimeInMillis());
        resultIntent.putExtra("endDate", endCalendar.getTimeInMillis());
        resultIntent.putExtra("entryType", entryType);
        resultIntent.putExtra("paymentMode", paymentMode);
        resultIntent.putStringArrayListExtra("categories", new ArrayList<>(selectedCategories));

        String searchQuery = searchTransactionInput != null ?
                searchTransactionInput.getText().toString() : "";
        resultIntent.putExtra("searchQuery", searchQuery);

        String tagsQuery = filterTagsInput != null ?
                filterTagsInput.getText().toString() : "";
        resultIntent.putExtra("tagsQuery", tagsQuery);

        setResult(Activity.RESULT_OK, resultIntent);

        Log.d(TAG, "Filters applied and returning to caller");
        finish();
    }

    /**
     * Show snackbar message
     */
    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "FiltersActivity destroyed");
    }
}
