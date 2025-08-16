package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<TransactionModel> displayedTransactions = new ArrayList<>();
    private TransactionAdapter transactionAdapter;
    private RecyclerView transactionRecyclerView;
    private PieChart pieChart;

    private TextView incomeText, expenseText, balanceText;
    private EditText searchEditText;
    private ImageView filterButton;
    private LinearLayout btnHome, btnTransactions, btnSettings;

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
        setupRecyclerView();
        setupClickListeners();
        setupFilterLauncher();
    }

    private void initializeUI() {
        incomeText = findViewById(R.id.incomeText);
        expenseText = findViewById(R.id.expenseText);
        balanceText = findViewById(R.id.balanceText);
        pieChart = findViewById(R.id.pieChart);
        searchEditText = findViewById(R.id.searchEditText);
        filterButton = findViewById(R.id.filterButton);
        btnHome = findViewById(R.id.btnHome);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupRecyclerView() {
        transactionRecyclerView = findViewById(R.id.transactionRecyclerView);
        transactionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(displayedTransactions, this);
        transactionRecyclerView.setAdapter(transactionAdapter);
    }

    private void setupClickListeners() {
        filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            // Pass current filters to the FiltersActivity so it can show the current state
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
        btnTransactions.setOnClickListener(v -> Toast.makeText(this, "Already on Transactions", Toast.LENGTH_SHORT).show());
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
                        // Receive the updated filter settings from FiltersActivity
                        Intent data = result.getData();
                        startDateFilter = data.getLongExtra("startDate", 0);
                        endDateFilter = data.getLongExtra("endDate", 0);
                        entryTypeFilter = data.getStringExtra("entryType");

                        ArrayList<String> selectedCategories = data.getStringArrayListExtra("categories");
                        if (selectedCategories != null) {
                            categoryFilter = new HashSet<>(selectedCategories);
                        }
                        ArrayList<String> selectedModes = data.getStringArrayListExtra("paymentModes");
                        if (selectedModes != null) {
                            paymentModeFilter = new HashSet<>(selectedModes);
                        }

                        filterTransactions();
                        Toast.makeText(this, "Filters Applied!", Toast.LENGTH_SHORT).show();
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
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && transactionsListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
        }
    }

    private void startListeningForTransactions(String userId) {
        DatabaseReference transactionsRef = mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions");
        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    allTransactions.add(snapshot.getValue(TransactionModel.class));
                }
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                filterTransactions();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(TransactionActivity.this, "Failed to load transactions.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterTransactions() {
        displayedTransactions.clear();
        String query = searchEditText.getText().toString().toLowerCase(Locale.getDefault());

        for (TransactionModel transaction : allTransactions) {
            // Search Query Check
            boolean matchesSearch = query.isEmpty() ||
                    (transaction.getTransactionCategory() != null && transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getPartyName() != null && transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getRemark() != null && transaction.getRemark().toLowerCase(Locale.getDefault()).contains(query));

            // Date Range Check
            boolean matchesDate = (startDateFilter == 0 && endDateFilter == 0) ||
                    (transaction.getTimestamp() >= startDateFilter && transaction.getTimestamp() <= endDateFilter);

            // Entry Type Check
            boolean matchesEntryType = "All".equals(entryTypeFilter) || entryTypeFilter.equalsIgnoreCase(transaction.getType());

            // Category Check
            boolean matchesCategory = categoryFilter.isEmpty() || categoryFilter.contains(transaction.getTransactionCategory());

            // Payment Mode Check
            boolean matchesPaymentMode = paymentModeFilter.isEmpty() || paymentModeFilter.contains(transaction.getPaymentMode());

            if (matchesSearch && matchesDate && matchesEntryType && matchesCategory && matchesPaymentMode) {
                displayedTransactions.add(transaction);
            }
        }
        updateUI();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        transactionAdapter.notifyDataSetChanged();
        calculateTotals();
        setupPieChart();
    }

    @SuppressLint("SetTextI18n")
    private void calculateTotals() {
        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel transaction : displayedTransactions) {
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else {
                totalExpense += transaction.getAmount();
            }
        }
        incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
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

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (totalExpense == 0) {
            pieChart.clear();
            pieChart.setCenterText("No Expenses");
        } else {
            for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            }
            pieChart.setCenterText("Expenses\n₹" + String.format(Locale.US, "%.2f", totalExpense));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);

        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.2f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(new PercentFormatter(pieChart));

        pieChart.setData(new PieData(dataSet));
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawEntryLabels(true);
        pieChart.invalidate();
    }

    @Override
    public void onItemClick(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }
}
