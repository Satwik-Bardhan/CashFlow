package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cashflow.adapters.CashbookAdapter;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * CashbookSwitchActivity - Comprehensive cashbook management and switching
 *
 * Features:
 * - Browse all cashbooks
 * - Filter by active/recent/favorites
 * - Search cashbooks by name or description
 * - Sort by various criteria
 * - Create, edit, delete, and duplicate cashbooks
 * - Toggle favorite status
 * - Switch between cashbooks
 * - Manage cashbook details
 * - Google Sign-In integration
 * - Firebase Realtime Database
 *
 * Updated: November 2025 - Complete implementation with Firebase
 */
public class CashbookSwitchActivity extends AppCompatActivity {

    private static final String TAG = "CashbookSwitchActivity";

    // UI Components - RecyclerView
    private RecyclerView cashbookRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private LinearLayout mainCard;

    // UI Components - Buttons
    private Button cancelButton;
    private Button addNewButton;
    private Button emptyStateCreateButton;
    private ImageView closeButton;

    // UI Components - Search & Filter
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private TextView sortButton;

    // UI Components - FAB & Stats
    private FloatingActionButton quickAddFab;
    private TextView totalCashbooksText;
    private TextView activeCashbooksText;

    // Adapter & Data
    private CashbookAdapter cashbookAdapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private String currentFilter = "active";

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener cashbooksListener;

    // State
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashbook_switch);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupSearchListener();
        setupFilterListener();
        loadCashbooks();

        Log.d(TAG, "CashbookSwitchActivity created");
    }

    /**
     * Initialize all UI views
     */
    private void initViews() {
        // RecyclerView & Layout
        cashbookRecyclerView = findViewById(R.id.cashbookRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        mainCard = findViewById(R.id.mainCard);

        // Buttons
        cancelButton = findViewById(R.id.cancelButton);
        addNewButton = findViewById(R.id.addNewButton);
        emptyStateCreateButton = findViewById(R.id.emptyStateCreateButton);
        closeButton = findViewById(R.id.closeButton);

        // Search & Filter
        searchEditText = findViewById(R.id.searchEditText);
        chipGroup = findViewById(R.id.chipGroup);
        sortButton = findViewById(R.id.sortButton);

        // FAB & Stats
        quickAddFab = findViewById(R.id.quickAddFab);
        totalCashbooksText = findViewById(R.id.totalCashbooksText);
        activeCashbooksText = findViewById(R.id.activeCashbooksText);

        // Set content descriptions for accessibility
        try {
            closeButton.setContentDescription(getString(R.string.close_button));
            addNewButton.setContentDescription(getString(R.string.add_cashbook_desc));
            quickAddFab.setContentDescription(getString(R.string.quick_add_cashbook));
            searchEditText.setHint(getString(R.string.search_cashbooks_hint));
        } catch (Exception e) {
            Log.w(TAG, "Error setting content descriptions", e);
        }

        Log.d(TAG, "Views initialized");
    }

    /**
     * Setup RecyclerView with adapter and listener
     */
    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        cashbookRecyclerView.setHasFixedSize(true);
        cashbookRecyclerView.setNestedScrollingEnabled(false);

        // Create adapter with callback listener
        cashbookAdapter = new CashbookAdapter(this, allCashbooks, new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(CashbookModel cashbook) {
                onCashbookSelected(cashbook);
            }

            @Override
            public void onFavoriteClick(CashbookModel cashbook) {
                handleFavoriteToggle(cashbook);
            }

            @Override
            public void onMenuClick(CashbookModel cashbook, View anchorView) {
                showCashbookOptions(cashbook, anchorView);
            }
        });

        cashbookRecyclerView.setAdapter(cashbookAdapter);

        // Swipe refresh listener
        swipeRefreshLayout.setOnRefreshListener(this::loadCashbooks);
        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary_blue,
                R.color.success_color,
                R.color.warning_orange
        );

        Log.d(TAG, "RecyclerView setup complete");
    }

    /**
     * Setup click listeners for all buttons
     */
    private void setupClickListeners() {
        if (closeButton != null) closeButton.setOnClickListener(v -> finish());
        if (cancelButton != null) cancelButton.setOnClickListener(v -> finish());
        if (addNewButton != null) addNewButton.setOnClickListener(v -> handleAddNewCashbook());
        if (emptyStateCreateButton != null) emptyStateCreateButton.setOnClickListener(v -> handleAddNewCashbook());
        if (quickAddFab != null) quickAddFab.setOnClickListener(v -> handleAddNewCashbook());
        if (sortButton != null) sortButton.setOnClickListener(v -> showSortOptions());

        Log.d(TAG, "Click listeners setup complete");
    }

    /**
     * Setup filter chip listener
     */
    private void setupFilterListener() {
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (!checkedIds.isEmpty()) {
                    int checkedId = checkedIds.get(0);
                    if (checkedId == R.id.chipActive) {
                        handleFilterClick("active");
                    } else if (checkedId == R.id.chipRecent) {
                        handleFilterClick("recent");
                    } else if (checkedId == R.id.chipFavorites) {
                        handleFilterClick("favorites");
                    }
                }
            });
        }

        Log.d(TAG, "Filter listener setup complete");
    }

    /**
     * Setup search text listener
     */
    private void setupSearchListener() {
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterCashbooks(s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        Log.d(TAG, "Search listener setup complete");
    }

    /**
     * Load cashbooks from Firebase
     */
    private void loadCashbooks() {
        if (isLoading) return;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showSnackbar("Not authenticated");
            Log.w(TAG, "No authenticated user");
            return;
        }

        showLoading(true);
        Log.d(TAG, "Loading cashbooks for user: " + currentUser.getUid());

        String userId = currentUser.getUid();

        // Remove previous listener if exists
        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                runOnUiThread(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);

                    try {
                        allCashbooks.clear();

                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                            if (cashbook != null) {
                                if (cashbook.getCashbookId() == null) {
                                    cashbook.setCashbookId(snapshot.getKey());
                                }
                                allCashbooks.add(cashbook);
                            }
                        }

                        if (allCashbooks.isEmpty()) {
                            showEmptyState(true);
                            Log.d(TAG, "No cashbooks found");
                        } else {
                            showEmptyState(false);
                            applyFilter(currentFilter);
                            updateStats(allCashbooks.size(), getActiveCashbooksCount(allCashbooks));
                            Log.d(TAG, "Loaded " + allCashbooks.size() + " cashbooks");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing cashbooks", e);
                        showSnackbar("Error loading cashbooks");
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    Log.e(TAG, "Error loading cashbooks", error.toException());
                    showSnackbar("Error: " + error.getMessage());
                });
            }
        };

        mDatabase.child("users").child(userId).child("cashbooks")
                .addValueEventListener(cashbooksListener);
    }

    /**
     * Handle add new cashbook action
     */
    private void handleAddNewCashbook() {
        showCreateCashbookDialog();
        Log.d(TAG, "Add new cashbook clicked");
    }

    /**
     * Show create cashbook dialog
     */
    private void showCreateCashbookDialog() {
        try {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_cashbook, null);
            EditText nameInput = dialogView.findViewById(R.id.cashbookNameInput);
            EditText descInput = dialogView.findViewById(R.id.cashbookDescInput);

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Create New Cashbook")
                    .setView(dialogView)
                    .setPositiveButton("Create", (dialog, which) -> {
                        String name = nameInput.getText().toString().trim();
                        String description = descInput.getText().toString().trim();

                        if (name.isEmpty()) {
                            showSnackbar("Please enter a cashbook name");
                            return;
                        }

                        createNewCashbook(name, description);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing create cashbook dialog", e);
            showSnackbar("Error creating dialog");
        }
    }

    /**
     * Create new cashbook using Firebase
     */
    private void createNewCashbook(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            showSnackbar("Cashbook name cannot be empty");
            return;
        }

        showLoading(true);
        Log.d(TAG, "Creating new cashbook: " + name);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showLoading(false);
            showSnackbar("Not authenticated");
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference cashbooksRef = mDatabase.child("users").child(userId).child("cashbooks");

        String cashbookId = cashbooksRef.push().getKey();
        if (cashbookId == null) {
            showLoading(false);
            showSnackbar("Error generating cashbook ID");
            return;
        }

        CashbookModel newCashbook = new CashbookModel();
        newCashbook.setCashbookId(cashbookId);
        newCashbook.setName(name.trim());
        newCashbook.setDescription(description != null ? description.trim() : "");
        newCashbook.setActive(true);
        newCashbook.setFavorite(false);
        newCashbook.setLastModified(System.currentTimeMillis());
        newCashbook.setBalance(0.0);
        newCashbook.setTransactionCount(0);

        cashbooksRef.child(cashbookId).setValue(newCashbook)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSnackbar("Cashbook created successfully!");
                    loadCashbooks();
                    Log.d(TAG, "Cashbook created: " + name);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showSnackbar("Failed to create cashbook: " + e.getMessage());
                    Log.e(TAG, "Error creating cashbook", e);
                });
    }

    /**
     * Handle favorite toggle
     */
    private void handleFavoriteToggle(CashbookModel cashbook) {
        if (cashbook == null) return;

        cashbook.setFavorite(!cashbook.isFavorite());
        updateCashbookInFirebase(cashbook);
    }

    /**
     * Update cashbook in Firebase
     */
    private void updateCashbookInFirebase(CashbookModel cashbook) {
        if (cashbook == null || cashbook.getCashbookId() == null) {
            showSnackbar("Invalid cashbook");
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showSnackbar("Not authenticated");
            return;
        }

        String userId = currentUser.getUid();
        cashbook.setLastModified(System.currentTimeMillis());

        mDatabase.child("users").child(userId)
                .child("cashbooks").child(cashbook.getCashbookId())
                .setValue(cashbook)
                .addOnSuccessListener(aVoid -> {
                    showSnackbar(cashbook.isFavorite() ? "Added to favorites" : "Removed from favorites");
                    Log.d(TAG, "Cashbook updated: " + cashbook.getName());
                    new Handler(Looper.getMainLooper()).postDelayed(this::loadCashbooks, 300);
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Failed to update cashbook");
                    Log.e(TAG, "Error updating cashbook", e);
                });
    }

    /**
     * Show cashbook options menu
     */
    private void showCashbookOptions(CashbookModel cashbook, View anchorView) {
        if (cashbook == null) return;

        String[] options = {"Edit", "Duplicate", "Delete", "View Details"};

        new MaterialAlertDialogBuilder(this)
                .setTitle(cashbook.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showEditCashbookDialog(cashbook);
                            break;
                        case 1:
                            duplicateCashbook(cashbook);
                            break;
                        case 2:
                            showDeleteConfirmation(cashbook);
                            break;
                        case 3:
                            showCashbookDetails(cashbook);
                            break;
                    }
                })
                .show();
    }

    /**
     * Show edit cashbook dialog
     */
    private void showEditCashbookDialog(CashbookModel cashbook) {
        if (cashbook == null) return;

        try {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_cashbook, null);
            EditText nameInput = dialogView.findViewById(R.id.cashbookNameInput);
            EditText descInput = dialogView.findViewById(R.id.cashbookDescInput);

            nameInput.setText(cashbook.getName());
            descInput.setText(cashbook.getDescription() != null ? cashbook.getDescription() : "");

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Edit Cashbook")
                    .setView(dialogView)
                    .setPositiveButton("Update", (dialog, which) -> {
                        String name = nameInput.getText().toString().trim();
                        String desc = descInput.getText().toString().trim();

                        if (name.isEmpty()) {
                            showSnackbar("Name cannot be empty");
                            return;
                        }

                        cashbook.setName(name);
                        cashbook.setDescription(desc);
                        updateCashbookInFirebase(cashbook);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing edit dialog", e);
            showSnackbar("Error editing cashbook");
        }
    }

    /**
     * Duplicate cashbook
     */
    private void duplicateCashbook(CashbookModel original) {
        if (original == null) return;

        CashbookModel duplicate = new CashbookModel();
        duplicate.setName(original.getName() + " (Copy)");
        duplicate.setDescription(original.getDescription());
        duplicate.setActive(true);
        duplicate.setFavorite(false);

        showLoading(true);
        Log.d(TAG, "Duplicating cashbook: " + original.getName());

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showLoading(false);
            showSnackbar("Not authenticated");
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference cashbooksRef = mDatabase.child("users").child(userId).child("cashbooks");
        String newCashbookId = cashbooksRef.push().getKey();

        if (newCashbookId == null) {
            showLoading(false);
            showSnackbar("Error generating ID");
            return;
        }

        duplicate.setCashbookId(newCashbookId);
        duplicate.setLastModified(System.currentTimeMillis());

        cashbooksRef.child(newCashbookId).setValue(duplicate)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSnackbar("Cashbook duplicated successfully");
                    loadCashbooks();
                    Log.d(TAG, "Cashbook duplicated");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showSnackbar("Failed to duplicate cashbook");
                    Log.e(TAG, "Error duplicating cashbook", e);
                });
    }

    /**
     * Show delete confirmation dialog
     */
    private void showDeleteConfirmation(CashbookModel cashbook) {
        if (cashbook == null) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Cashbook")
                .setMessage("Are you sure you want to delete \"" + cashbook.getName() + "\"?\n\n" +
                        "All associated transactions will also be deleted. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteCashbookFromFirebase(cashbook))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Delete cashbook from Firebase
     */
    private void deleteCashbookFromFirebase(CashbookModel cashbook) {
        if (cashbook == null || cashbook.getCashbookId() == null) {
            showSnackbar("Invalid cashbook");
            return;
        }

        showLoading(true);
        Log.d(TAG, "Deleting cashbook: " + cashbook.getName());

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showLoading(false);
            showSnackbar("Not authenticated");
            return;
        }

        String userId = currentUser.getUid();

        mDatabase.child("users").child(userId)
                .child("cashbooks").child(cashbook.getCashbookId())
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showSnackbar("Cashbook deleted successfully");
                    loadCashbooks();
                    Log.d(TAG, "Cashbook deleted: " + cashbook.getName());
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showSnackbar("Failed to delete cashbook");
                    Log.e(TAG, "Error deleting cashbook", e);
                });
    }

    /**
     * Show cashbook details dialog
     */
    private void showCashbookDetails(CashbookModel cashbook) {
        if (cashbook == null) return;

        String details = "Name: " + cashbook.getName() + "\n" +
                "Description: " + (cashbook.getDescription() != null ? cashbook.getDescription() : "N/A") + "\n" +
                "Status: " + (cashbook.isActive() ? "Active" : "Inactive") + "\n" +
                "Favorite: " + (cashbook.isFavorite() ? "Yes" : "No") + "\n" +
                "Transactions: " + cashbook.getTransactionCount() + "\n" +
                "Balance: ₹" + String.format("%.2f", cashbook.getBalance());

        new MaterialAlertDialogBuilder(this)
                .setTitle(cashbook.getName())
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Handle filter selection
     */
    private void handleFilterClick(String filter) {
        currentFilter = filter;
        applyFilter(filter);
        Log.d(TAG, "Filter changed to: " + filter);
    }

    /**
     * Apply filter to cashbook list
     */
    private void applyFilter(String filter) {
        List<CashbookModel> filteredList = new ArrayList<>();

        switch (filter) {
            case "active":
                for (CashbookModel cashbook : allCashbooks) {
                    if (cashbook.isActive()) {
                        filteredList.add(cashbook);
                    }
                }
                break;

            case "recent":
                filteredList.addAll(allCashbooks);
                filteredList.sort((c1, c2) ->
                        Long.compare(c2.getLastModified(), c1.getLastModified()));
                break;

            case "favorites":
                for (CashbookModel cashbook : allCashbooks) {
                    if (cashbook.isFavorite()) {
                        filteredList.add(cashbook);
                    }
                }
                break;

            default:
                filteredList.addAll(allCashbooks);
                break;
        }

        cashbookAdapter.updateList(filteredList);
        Log.d(TAG, "Filter applied: " + filter + " - " + filteredList.size() + " items");
    }

    /**
     * Filter cashbooks by search query
     */
    private void filterCashbooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            applyFilter(currentFilter);
            return;
        }

        List<CashbookModel> filteredList = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (CashbookModel cashbook : allCashbooks) {
            boolean nameMatches = cashbook.getName() != null &&
                    cashbook.getName().toLowerCase().contains(lowerQuery);
            boolean descMatches = cashbook.getDescription() != null &&
                    cashbook.getDescription().toLowerCase().contains(lowerQuery);

            if (nameMatches || descMatches) {
                filteredList.add(cashbook);
            }
        }

        cashbookAdapter.updateList(filteredList);
        Log.d(TAG, "Search filtered to: " + filteredList.size() + " items");
    }

    /**
     * Show sort options dialog
     */
    private void showSortOptions() {
        try {
            String[] sortOptions = {
                    getString(R.string.sort_name_asc),
                    getString(R.string.sort_name_desc),
                    getString(R.string.sort_recent_first),
                    getString(R.string.sort_oldest_first),
                    getString(R.string.sort_most_transactions)
            };

            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.sort_cashbooks_title))
                    .setItems(sortOptions, (dialog, which) -> applySortOption(which))
                    .show();

            Log.d(TAG, "Sort options shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing sort options", e);
        }
    }

    /**
     * Apply sort option to list
     */
    private void applySortOption(int option) {
        List<CashbookModel> sortedList = new ArrayList<>(allCashbooks);

        switch (option) {
            case 0:
                sortedList.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                break;
            case 1:
                sortedList.sort((c1, c2) -> c2.getName().compareToIgnoreCase(c1.getName()));
                break;
            case 2:
                sortedList.sort((c1, c2) -> Long.compare(c2.getLastModified(), c1.getLastModified()));
                break;
            case 3:
                sortedList.sort((c1, c2) -> Long.compare(c1.getLastModified(), c2.getLastModified()));
                break;
            case 4:
                sortedList.sort((c1, c2) -> Integer.compare(c2.getTransactionCount(), c1.getTransactionCount()));
                break;
        }

        cashbookAdapter.updateList(sortedList);
        Log.d(TAG, "Sort applied: option " + option);
    }

    /**
     * Handle cashbook selection - return to caller
     */
    private void onCashbookSelected(CashbookModel cashbook) {
        if (cashbook == null) {
            Log.e(TAG, "Cashbook is null");
            return;
        }

        Intent result = new Intent();
        result.putExtra("selected_cashbook_id", cashbook.getCashbookId());
        result.putExtra("cashbook_name", cashbook.getName());
        setResult(RESULT_OK, result);

        Log.d(TAG, "Cashbook selected: " + cashbook.getName());
        finish();
    }

    /**
     * Show loading state
     */
    private void showLoading(boolean show) {
        isLoading = show;
        if (loadingLayout != null) {
            loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mainCard != null) {
            mainCard.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Show empty state
     */
    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mainCard != null) {
            mainCard.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Update statistics display
     */
    private void updateStats(int total, int active) {
        if (totalCashbooksText != null) {
            totalCashbooksText.setText(String.valueOf(total));
        }
        if (activeCashbooksText != null) {
            activeCashbooksText.setText(String.valueOf(active));
        }
    }

    /**
     * Get count of active cashbooks
     */
    private int getActiveCashbooksCount(List<CashbookModel> cashbooks) {
        int count = 0;
        for (CashbookModel cashbook : cashbooks) {
            if (cashbook.isActive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Show Snackbar message
     */
    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove Firebase listener to prevent memory leaks
        if (cashbooksListener != null) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                mDatabase.child("users").child(currentUser.getUid())
                        .child("cashbooks")
                        .removeEventListener(cashbooksListener);
                Log.d(TAG, "Firebase listener removed");
            }
        }

        Log.d(TAG, "CashbookSwitchActivity destroyed");
    }
}
