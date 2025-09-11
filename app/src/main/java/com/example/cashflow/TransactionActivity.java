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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    private TransactionItemFragment transactionFragment;
    private PieChart pieChart;

    private TextView incomeText, expenseText, balanceText;
    private EditText searchEditText;
    private ImageView filterButton;
    private Button btnDownload;

    // Bottom Navigation Views
    private LinearLayout btnHome, btnTransactions, btnSettings;
    private ImageView iconHome, iconTransactions, iconSettings;
    private TextView textHome, textTransactions, textSettings;

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
        setContentView(R.layout.activity_transaction);
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
        setupTransactionFragment();
        setupClickListeners();
        setupFilterLauncher();
        setupDownloadLauncher();
        initializePieChart();
        updateBottomNavigationSelection(btnTransactions); // Set initial selected state

        btnDownload.setOnLongClickListener(v -> {
            clearAllFilters();
            debugTransactionData();
            Toast.makeText(this, "All filters cleared", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void initializeUI() {
        incomeText = findViewById(R.id.incomeText);
        expenseText = findViewById(R.id.expenseText);
        balanceText = findViewById(R.id.balanceText);
        pieChart = findViewById(R.id.pieChart);
        searchEditText = findViewById(R.id.searchEditText);
        filterButton = findViewById(R.id.filterButton);
        btnDownload = findViewById(R.id.downloadReportButton);

        // Initialize Bottom Navigation Views
        btnHome = findViewById(R.id.btnHome);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnSettings = findViewById(R.id.btnSettings);
        iconHome = findViewById(R.id.iconHome);
        iconTransactions = findViewById(R.id.iconTransactions);
        iconSettings = findViewById(R.id.iconSettings);
        textHome = findViewById(R.id.textHome);
        textTransactions = findViewById(R.id.textTransactions);
        textSettings = findViewById(R.id.textSettings);

        Log.d(TAG, "UI components initialized successfully");
    }

    private void updateBottomNavigationSelection(View selectedButton) {
        // Reset all buttons to default (unselected) state
        iconHome.setColorFilter(Color.WHITE);
        textHome.setTextColor(Color.WHITE);
        iconTransactions.setColorFilter(Color.WHITE);
        textTransactions.setTextColor(Color.WHITE);
        iconSettings.setColorFilter(Color.WHITE);
        textSettings.setTextColor(Color.WHITE);

        // [FIXED] Use a valid color. This blue is used elsewhere in your app.
        int activeColor = Color.parseColor("#2196F3");

        // Set the selected button to the active state
        if (selectedButton.getId() == R.id.btnHome) {
            iconHome.setColorFilter(activeColor);
            textHome.setTextColor(activeColor);
        } else if (selectedButton.getId() == R.id.btnTransactions) {
            iconTransactions.setColorFilter(activeColor);
            textTransactions.setTextColor(activeColor);
        } else if (selectedButton.getId() == R.id.btnSettings) {
            iconSettings.setColorFilter(activeColor);
            textSettings.setTextColor(activeColor);
        }
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(this);

        // [FIXED] This now correctly finds the FrameLayout added to your XML.
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

        // Bottom Navigation Click Listeners
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
        DatabaseReference transactionsRef = mDatabase.child("users").child(userId)
                .child("cashbooks").child(currentCashbookId).child("transactions");

        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null) {
                        transaction.setTransactionId(snapshot.getKey());
                        allTransactions.add(transaction);
                    }
                }
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                filterTransactions();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase listener cancelled: " + databaseError.getMessage());
                Toast.makeText(TransactionActivity.this, "Failed to load transactions.", Toast.LENGTH_SHORT).show();
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
        filterTransactions();
    }

    private void filterTransactions() {
        displayedTransactions.clear();
        String query = searchEditText.getText().toString().toLowerCase(Locale.getDefault());

        for (TransactionModel transaction : allTransactions) {
            boolean matchesSearch = query.isEmpty() ||
                    (transaction.getTransactionCategory() != null && transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getPartyName() != null && transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getRemark() != null && transaction.getRemark().toLowerCase(Locale.getDefault()).contains(query));
            boolean matchesDate = (startDateFilter == 0 && endDateFilter == 0) || (transaction.getTimestamp() >= startDateFilter && transaction.getTimestamp() <= endDateFilter);
            boolean matchesEntryType = "All".equals(entryTypeFilter) || entryTypeFilter.equalsIgnoreCase(transaction.getType());
            boolean matchesCategory = categoryFilter.isEmpty() || categoryFilter.contains(transaction.getTransactionCategory());
            boolean matchesPaymentMode = paymentModeFilter.isEmpty() || paymentModeFilter.contains(transaction.getPaymentMode());

            if (matchesSearch && matchesDate && matchesEntryType && matchesCategory && matchesPaymentMode) {
                displayedTransactions.add(transaction);
            }
        }
        updateUI();
    }

    private void debugTransactionData() {
        Log.d(TAG, "=== TRANSACTION DEBUG INFO ===");
        Log.d(TAG, "Total allTransactions: " + allTransactions.size());
        Log.d(TAG, "Total displayedTransactions: " + displayedTransactions.size());
    }

    private void updateUI() {
        if (transactionFragment != null) {
            transactionFragment.updateTransactions(displayedTransactions);
        }
        calculateTotals();
        setupPieChart();
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
    }

    private void initializePieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setCenterText("Expenses\n₹0");
        pieChart.setCenterTextSize(16f);
        pieChart.setCenterTextColor(Color.BLACK);
        pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD);
        pieChart.getLegend().setEnabled(false);
    }

    private void setupPieChart() {
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0;

        for (TransactionModel transaction : displayedTransactions) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "Other";
                float amount = (float) transaction.getAmount();
                expenseByCategory.put(category, expenseByCategory.getOrDefault(category, 0f) + amount);
                totalExpense += amount;
            }
        }

        if (totalExpense == 0) {
            pieChart.clear();
            pieChart.setCenterText("No Expenses\nto Display");
            pieChart.invalidate();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        pieChart.setCenterText("Expenses\n₹" + String.format(Locale.US, "%.0f", totalExpense));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setUsingSliceColorAsValueLineColor(true);

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#4CAF50"));
        colors.add(Color.parseColor("#FF5722"));
        colors.add(Color.parseColor("#2196F3"));
        colors.add(Color.parseColor("#FF9800"));
        colors.add(Color.parseColor("#9C27B0"));
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));
        pieChart.setData(data);
        pieChart.animateY(1400, Easing.EaseInOutQuad);
        pieChart.invalidate();
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
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
                .filter(t -> "All".equalsIgnoreCase(entryType) || ("Cash In".equalsIgnoreCase(entryType) && "IN".equalsIgnoreCase(t.getType())) || ("Cash Out".equalsIgnoreCase(entryType) && "OUT".equalsIgnoreCase(t.getType())))
                .filter(t -> "All".equalsIgnoreCase(paymentMode) || paymentMode.equalsIgnoreCase(t.getPaymentMode()))
                .collect(Collectors.toList());

        if (transactionsToExport.isEmpty()) {
            Toast.makeText(this, "No transactions found for the selected filters.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder("Date,Type,Amount,Category,Party/Supplier,Payment Mode,Remark\n");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (TransactionModel t : transactionsToExport) {
            csvData.append("\"").append(dateFormat.format(new Date(t.getTimestamp()))).append("\",")
                    .append("\"").append("IN".equals(t.getType()) ? "Income" : "Expense").append("\",")
                    .append(t.getAmount()).append(",")
                    .append("\"").append(t.getTransactionCategory() != null ? t.getTransactionCategory() : "").append("\",")
                    .append("\"").append(t.getPartyName() != null ? t.getPartyName() : "").append("\",")
                    .append("\"").append(t.getPaymentMode() != null ? t.getPaymentMode() : "").append("\",")
                    .append("\"").append(t.getRemark() != null ? t.getRemark() : "").append("\"\n");
        }
        saveCsvFile(csvData.toString());
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
                    outputStream.write(csvData.getBytes());
                    Toast.makeText(this, "File saved to Downloads folder", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving CSV file", e);
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onItemClick(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }
}