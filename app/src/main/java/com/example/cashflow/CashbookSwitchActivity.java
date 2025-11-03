package com.example.cashflow;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class CashbookSwitchActivity extends AppCompatActivity {

    private RecyclerView cashbookRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private Button cancelButton;
    private Button addNewButton;
    private Button emptyStateCreateButton;
    private ImageView closeButton;
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private TextView sortButton;
    private FloatingActionButton quickAddFab;
    private TextView totalCashbooksText, activeCashbooksText;

    private CashbookAdapter cashbookAdapter;
    private List<CashbookModel> allCashbooks;
    private String currentFilter = "active";
    private DataRepository dataRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashbook_switch);

        dataRepository = DataRepository.getInstance(this);
        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupSearchListener();
        loadCashbooks();
    }

    private void initViews() {
        cashbookRecyclerView = findViewById(R.id.cashbookRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        cancelButton = findViewById(R.id.cancelButton);
        addNewButton = findViewById(R.id.addNewButton);
        emptyStateCreateButton = findViewById(R.id.emptyStateCreateButton);
        closeButton = findViewById(R.id.closeButton);
        searchEditText = findViewById(R.id.searchEditText);
        chipGroup = findViewById(R.id.chipGroup);
        sortButton = findViewById(R.id.sortButton);
        quickAddFab = findViewById(R.id.quickAddFab);
        totalCashbooksText = findViewById(R.id.totalCashbooksText);
        activeCashbooksText = findViewById(R.id.activeCashbooksText);

        // Set accessibility content descriptions
        closeButton.setContentDescription(getString(R.string.close_button));
        addNewButton.setContentDescription(getString(R.string.add_cashbook_desc));
        quickAddFab.setContentDescription(getString(R.string.quick_add_cashbook));
        searchEditText.setHint(getString(R.string.search_cashbooks_hint));
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        cashbookRecyclerView.setHasFixedSize(true);
        cashbookRecyclerView.setNestedScrollingEnabled(false);

        allCashbooks = new ArrayList<>();
        cashbookAdapter = new CashbookAdapter(this, allCashbooks, this::onCashbookSelected);
        cashbookRecyclerView.setAdapter(cashbookAdapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadCashbooks);
        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary_blue,
                R.color.success_color,
                R.color.warning_orange
        );
    }

    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());
        addNewButton.setOnClickListener(v -> handleAddNewCashbook());
        emptyStateCreateButton.setOnClickListener(v -> handleAddNewCashbook());
        quickAddFab.setOnClickListener(v -> handleAddNewCashbook());
        sortButton.setOnClickListener(v -> showSortOptions());

        // Material 3 ChipGroup listener
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

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCashbooks(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCashbooks() {
        showLoading(true);

        dataRepository.getCashbooks(
                cashbooks -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        swipeRefreshLayout.setRefreshing(false);

                        allCashbooks.clear();
                        allCashbooks.addAll(cashbooks);

                        if (cashbooks.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            applyFilter(currentFilter);
                            updateStats(cashbooks.size(), getActiveCashbooksCount(cashbooks));
                        }
                    });
                },
                error -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        swipeRefreshLayout.setRefreshing(false);
                        ErrorHandler.handleError(this, error);
                    });
                }
        );
    }

    private void handleAddNewCashbook() {
        // TODO: Open CreateCashbookActivity
        // Intent intent = new Intent(this, CreateCashbookActivity.class);
        // startActivity(intent);
    }

    private void handleFilterClick(String filter) {
        currentFilter = filter;
        applyFilter(filter);
    }

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
                // Sort by last modified date
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
        }

        cashbookAdapter.updateList(filteredList);
    }

    private void filterCashbooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            applyFilter(currentFilter);
            return;
        }

        List<CashbookModel> filteredList = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (CashbookModel cashbook : allCashbooks) {
            if (cashbook.getName().toLowerCase().contains(lowerQuery) ||
                    cashbook.getDescription().toLowerCase().contains(lowerQuery)) {
                filteredList.add(cashbook);
            }
        }

        cashbookAdapter.updateList(filteredList);
    }

    private void showSortOptions() {
        String[] sortOptions = {
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_recent_first),
                getString(R.string.sort_oldest_first),
                getString(R.string.sort_most_transactions)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sort_cashbooks_title))
                .setItems(sortOptions, (dialog, which) -> {
                    applySortOption(which);
                })
                .show();
    }

    private void applySortOption(int option) {
        List<CashbookModel> sortedList = new ArrayList<>(allCashbooks);

        switch (option) {
            case 0: // Name A-Z
                sortedList.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                break;
            case 1: // Name Z-A
                sortedList.sort((c1, c2) -> c2.getName().compareToIgnoreCase(c1.getName()));
                break;
            case 2: // Recent First
                sortedList.sort((c1, c2) -> Long.compare(c2.getLastModified(), c1.getLastModified()));
                break;
            case 3: // Oldest First
                sortedList.sort((c1, c2) -> Long.compare(c1.getLastModified(), c2.getLastModified()));
                break;
            case 4: // Most Transactions
                sortedList.sort((c1, c2) -> Integer.compare(c2.getTransactionCount(), c1.getTransactionCount()));
                break;
        }

        cashbookAdapter.updateList(sortedList);
    }

    private void onCashbookSelected(CashbookModel cashbook) {
        // TODO: Switch to selected cashbook and return to home
        // Save selected cashbook ID to SharedPreferences
        // Then finish() or navigate back
        finish();
    }

    private void showLoading(boolean show) {
        loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.mainCard).setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.mainCard).setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateStats(int total, int active) {
        totalCashbooksText.setText(String.valueOf(total));
        activeCashbooksText.setText(String.valueOf(active));
    }

    private int getActiveCashbooksCount(List<CashbookModel> cashbooks) {
        int count = 0;
        for (CashbookModel cashbook : cashbooks) {
            if (cashbook.isActive()) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources if needed
    }
}
