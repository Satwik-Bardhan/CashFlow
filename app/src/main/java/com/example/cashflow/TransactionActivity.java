package com.example.cashflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<TransactionModel> displayedTransactions = new ArrayList<>();

    // Fragment-based approach - removed RecyclerView variables
    private TransactionItemFragment transactionFragment;
    private PieChart pieChart;

    private TextView incomeText, expenseText, balanceText;
    private EditText searchEditText;
    private ImageView filterButton;
    private Button btnDownload;
    private TextView btnHome, btnTransactions, btnSettings;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private String currentCashbookId;

    // Filter state variables
    private long startDateFilter = 0;
    private long endDateFilter = 0;
    private String entryTypeFilter = "All";
    private Set<String> categoryFilter = new HashSet<>();
    private Set<String> paymentModeFilter = new HashSet<>();

    private ActivityResultLauncher<Intent> filterLauncher;
    private ActivityResultLauncher<Intent> downloadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_activity);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupTransactionFragment(); // Replace setupRecyclerView with fragment setup
        setupClickListeners();
        setupFilterLauncher();
        setupDownloadLauncher();
        initializePieChart();

        // Debug long click listener to clear all filters
        btnDownload.setOnLongClickListener(v -> {
            clearAllFilters();
            debugTransactionData();
            Toast.makeText(this, "All filters cleared - showing all transactions", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnTransactions.setSelected(true);
    }

    private void initializeUI() {
        incomeText = findViewById(R.id.incomeText);
        expenseText = findViewById(R.id.expenseText);
        balanceText = findViewById(R.id.balanceText);
        pieChart = findViewById(R.id.pieChart);
        searchEditText = findViewById(R.id.searchEditText);
        filterButton = findViewById(R.id.filterButton);
        btnDownload = findViewById(R.id.downloadReportButton);
        btnHome = findViewById(R.id.btnHome);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnSettings = findViewById(R.id.btnSettings);

        Log.d(TAG, "UI components initialized successfully");
    }

    private void setupTransactionFragment() {
        // Create fragment with empty transaction list initially
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(this);

        // Add fragment to container
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.transaction_fragment_container, transactionFragment, "TRANSACTION_FRAGMENT")
                .commit();

        Log.d(TAG, "Transaction fragment setup complete");
    }

    private void setupClickListeners() {
        btnDownload.setOnClickListener(v -> {
            Intent intent = new Intent(this, DownloadOptionsActivity.class);
            downloadLauncher.launch(intent);
        });

        filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.putExtra("startDate", startDateFilter);
            intent.putExtra("endDate", endDateFilter);
            intent.putExtra("entryType", entryTypeFilter);
            intent.putStringArrayListExtra("categories", new ArrayList<>(categoryFilter));
            intent.putStringArrayListExtra("paymentModes", new ArrayList<>(paymentModeFilter));
            filterLauncher.launch(intent);
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomePage.class));
            finish();
        });

        btnTransactions.setOnClickListener(v ->
                Toast.makeText(this, "Already on Transactions", Toast.LENGTH_SHORT).show());

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
    }

    private void setupFilterLauncher() {
        filterLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        startDateFilter = data.getLongExtra("startDate", 0);
                        endDateFilter = data.getLongExtra("endDate", 0);
                        entryTypeFilter = data.getStringExtra("entryType");
                        ArrayList<String> selectedCategories = data.getStringArrayListExtra("categories");
                        if (selectedCategories != null) categoryFilter = new HashSet<>(selectedCategories);
                        ArrayList<String> selectedModes = data.getStringArrayListExtra("paymentModes");
                        if (selectedModes != null) paymentModeFilter = new HashSet<>(selectedModes);
                        filterTransactions();
                        Toast.makeText(this, "Filters Applied!", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupDownloadLauncher() {
        downloadLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        long startDate = data.getLongExtra("startDate", 0);
                        long endDate = data.getLongExtra("endDate", 0);
                        String entryType = data.getStringExtra("entryType");
                        String paymentMode = data.getStringExtra("paymentMode");

                        if (checkPermissions()) {
                            exportTransactionsToCSV(startDate, endDate, entryType, paymentMode);
                        } else {
                            requestPermissions();
                        }
                    }
                }
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForTransactions(currentUser.getUid());
        } else {
            Log.w(TAG, "No authenticated user found");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListener();
    }

    private void removeFirebaseListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(currentUser.getUid())
                    .child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
            transactionsListener = null;
            Log.d(TAG, "Firebase listener removed");
        }
    }

    private void startListeningForTransactions(String userId) {
        Log.d(TAG, "Starting to listen for transactions for user: " + userId + ", cashbook: " + currentCashbookId);

        DatabaseReference transactionsRef = mDatabase.child("users").child(userId)
                .child("cashbooks").child(currentCashbookId).child("transactions");

        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Firebase data received. Snapshot count: " + dataSnapshot.getChildrenCount());

                allTransactions.clear();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null) {
                        transaction.setTransactionId(snapshot.getKey());
                        allTransactions.add(transaction);
                    } else {
                        Log.w(TAG, "Null transaction found in Firebase data for key: " + snapshot.getKey());
                    }
                }

                Log.d(TAG, "Successfully loaded " + allTransactions.size() + " transactions from Firebase");

                // Sort by timestamp (newest first)
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                // Apply current filters and update UI
                filterTransactions();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase listener cancelled: " + databaseError.getMessage());
                Toast.makeText(TransactionActivity.this,
                        "Failed to load transactions: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearAllFilters() {
        startDateFilter = 0;
        endDateFilter = 0;
        entryTypeFilter = "All";
        categoryFilter.clear();
        paymentModeFilter.clear();
        searchEditText.setText("");

        Log.d(TAG, "All filters cleared - will show all transactions");
        filterTransactions();
    }

    private void filterTransactions() {
        displayedTransactions.clear();
        String query = searchEditText.getText().toString().toLowerCase(Locale.getDefault());

        if (allTransactions.isEmpty()) {
            Log.w(TAG, "filterTransactions: No transactions to filter from Firebase");
            updateUI();
            return;
        }

        Log.d(TAG, "Filtering " + allTransactions.size() + " transactions with query: '" + query + "'");

        int matchCount = 0;
        for (TransactionModel transaction : allTransactions) {
            if (transaction == null) {
                Log.w(TAG, "Null transaction encountered during filtering");
                continue;
            }

            boolean matchesSearch = query.isEmpty() ||
                    (transaction.getTransactionCategory() != null &&
                            transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getPartyName() != null &&
                            transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getRemark() != null &&
                            transaction.getRemark().toLowerCase(Locale.getDefault()).contains(query));

            boolean matchesDate = (startDateFilter == 0 && endDateFilter == 0) ||
                    (transaction.getTimestamp() >= startDateFilter && transaction.getTimestamp() <= endDateFilter);

            boolean matchesEntryType = "All".equals(entryTypeFilter) ||
                    entryTypeFilter.equalsIgnoreCase(transaction.getType());

            boolean matchesCategory = categoryFilter.isEmpty() ||
                    categoryFilter.contains(transaction.getTransactionCategory());

            boolean matchesPaymentMode = paymentModeFilter.isEmpty() ||
                    paymentModeFilter.contains(transaction.getPaymentMode());

            if (matchesSearch && matchesDate && matchesEntryType && matchesCategory && matchesPaymentMode) {
                displayedTransactions.add(transaction);
                matchCount++;
            }
        }

        Log.d(TAG, "After filtering: " + matchCount + " transactions match criteria");
        debugTransactionData();
        updateUI();
    }

    private void debugTransactionData() {
        Log.d(TAG, "=== TRANSACTION DEBUG INFO ===");
        Log.d(TAG, "Total allTransactions: " + allTransactions.size());
        Log.d(TAG, "Total displayedTransactions: " + displayedTransactions.size());
        Log.d(TAG, "Fragment status: " + (transactionFragment != null ? "initialized" : "null"));
        Log.d(TAG, "Current filters:");
        Log.d(TAG, "  - Search query: '" + searchEditText.getText().toString() + "'");
        Log.d(TAG, "  - Entry type: " + entryTypeFilter);
        Log.d(TAG, "  - Date range: " + startDateFilter + " to " + endDateFilter);
        Log.d(TAG, "  - Category filter size: " + categoryFilter.size());
        Log.d(TAG, "  - Payment mode filter size: " + paymentModeFilter.size());

        // Log first few transactions for verification
        for (int i = 0; i < Math.min(3, allTransactions.size()); i++) {
            TransactionModel t = allTransactions.get(i);
            Log.d(TAG, "Transaction " + i + ": " + t.getTransactionCategory() + " - ₹" + t.getAmount() + " (" + t.getType() + ")");
        }
        Log.d(TAG, "===============================");
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        Log.d(TAG, "updateUI: Updating UI with " + displayedTransactions.size() + " transactions");

        // Update fragment instead of adapter
        if (transactionFragment != null) {
            transactionFragment.updateTransactions(displayedTransactions);
        } else {
            Log.w(TAG, "updateUI: transactionFragment is null!");
        }

        calculateTotals();
        setupPieChart();

        if (displayedTransactions.isEmpty()) {
            Log.i(TAG, "No transactions to display - showing empty state");
        }
    }

    @SuppressLint("SetTextI18n")
    private void calculateTotals() {
        double totalIncome = 0, totalExpense = 0;

        for (TransactionModel transaction : displayedTransactions) {
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else if ("OUT".equalsIgnoreCase(transaction.getType())) {
                totalExpense += transaction.getAmount();
            }
        }

        double balance = totalIncome - totalExpense;

        incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", balance));

        Log.d(TAG, "Totals calculated - Income: ₹" + totalIncome + ", Expense: ₹" + totalExpense + ", Balance: ₹" + balance);
    }

    private void initializePieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);

        // Center hole configuration
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);

        // Center text
        pieChart.setCenterText("Expenses\n₹0");
        pieChart.setCenterTextSize(16f);
        pieChart.setCenterTextColor(Color.BLACK);
        pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD);

        // Entry labels (category names on slices)
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD);

        // Disable legend
        Legend legend = pieChart.getLegend();
        legend.setEnabled(false);

        Log.d(TAG, "Pie chart initialized");
    }

    private void setupPieChart() {
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0;

        // Group expenses by category
        for (TransactionModel transaction : displayedTransactions) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory();
                if (category == null || category.trim().isEmpty()) {
                    category = "Other";
                }

                float amount = (float) transaction.getAmount();
                Float currentAmount = expenseByCategory.get(category);
                if (currentAmount == null) currentAmount = 0.0f;

                expenseByCategory.put(category, currentAmount + amount);
                totalExpense += amount;
            }
        }

        ArrayList<PieEntry> entries = new ArrayList<>();

        // Handle no data case
        if (totalExpense == 0 || expenseByCategory.isEmpty()) {
            pieChart.clear();
            pieChart.setCenterText("No Expenses\nto Display");
            pieChart.invalidate();
            Log.d(TAG, "Pie chart cleared - no expense data");
            return;
        }

        // Create pie entries
        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        pieChart.setCenterText("Expenses\n₹" + String.format(Locale.US, "%.0f", totalExpense));

        // Configure dataset
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);

        // Labels outside slices
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setUsingSliceColorAsValueLineColor(true);

        // Set colors
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#4CAF50")); // Green
        colors.add(Color.parseColor("#FF5722")); // Red-Orange
        colors.add(Color.parseColor("#2196F3")); // Blue
        colors.add(Color.parseColor("#FF9800")); // Orange
        colors.add(Color.parseColor("#9C27B0")); // Purple
        colors.add(Color.parseColor("#607D8B")); // Blue Grey
        colors.add(Color.parseColor("#795548")); // Brown
        colors.add(Color.parseColor("#E91E63")); // Pink
        dataSet.setColors(colors);

        // Value text configuration
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);

        // Create and set pie data
        PieData data = new PieData(dataSet);
        data.setDrawValues(true);
        data.setValueFormatter(new PercentFormatter(pieChart));

        pieChart.setData(data);
        pieChart.highlightValues(null);
        pieChart.animateY(1400, Easing.EaseInOutQuad);
        pieChart.invalidate();

        Log.d(TAG, "Pie chart updated with " + entries.size() + " categories, total expense: ₹" + totalExpense);
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true; // Scoped storage, no permission needed
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. Please try downloading again.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Storage permission is required to download files.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void exportTransactionsToCSV(long startDate, long endDate, String entryType, String paymentMode) {
        List<TransactionModel> transactionsToExport = allTransactions.stream()
                .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                .filter(t -> {
                    if ("Cash In".equalsIgnoreCase(entryType)) return "IN".equalsIgnoreCase(t.getType());
                    if ("Cash Out".equalsIgnoreCase(entryType)) return "OUT".equalsIgnoreCase(t.getType());
                    return true; // "All" case
                })
                .filter(t -> "All".equalsIgnoreCase(paymentMode) || paymentMode.equalsIgnoreCase(t.getPaymentMode()))
                .collect(Collectors.toList());

        if (transactionsToExport.isEmpty()) {
            Toast.makeText(this, "No transactions found for the selected filters.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder();
        csvData.append("Date,Type,Amount,Category,Party/Supplier,Payment Mode,Remark\n");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (TransactionModel transaction : transactionsToExport) {
            String date = dateFormat.format(new Date(transaction.getTimestamp()));
            String type = "IN".equals(transaction.getType()) ? "Income" : "Expense";

            csvData.append("\"").append(date).append("\",");
            csvData.append("\"").append(type).append("\",");
            csvData.append(transaction.getAmount()).append(",");
            csvData.append("\"").append(transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "").append("\",");
            csvData.append("\"").append(transaction.getPartyName() != null ? transaction.getPartyName() : "").append("\",");
            csvData.append("\"").append(transaction.getPaymentMode() != null ? transaction.getPaymentMode() : "").append("\",");
            csvData.append("\"").append(transaction.getRemark() != null ? transaction.getRemark() : "").append("\"\n");
        }

        saveCsvFile(csvData.toString());
        Log.d(TAG, "CSV export initiated for " + transactionsToExport.size() + " transactions");
    }

    private void saveCsvFile(String csvData) {
        String fileName = "Transactions_" + System.currentTimeMillis() + ".csv";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(csvData.getBytes());
                        Toast.makeText(this, "File saved to Downloads folder as " + fileName, Toast.LENGTH_LONG).show();
                        Log.d(TAG, "CSV file saved successfully: " + fileName);
                    }
                }
            } else {
                Toast.makeText(this, "Failed to create file in Downloads folder", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving CSV file", e);
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onItemClick(TransactionModel transaction) {
        if (transaction != null) {
            Intent intent = new Intent(this, TransactionDetailActivity.class);
            intent.putExtra("transaction_model", transaction);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            Log.d(TAG, "Opening transaction detail for: " + transaction.getTransactionCategory());
        } else {
            Log.w(TAG, "Attempted to open detail for null transaction");
        }
    }
}
