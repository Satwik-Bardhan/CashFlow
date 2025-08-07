package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<TransactionModel> displayedTransactions = new ArrayList<>();
    private TransactionAdapter transactionAdapter;
    private RecyclerView transactionRecyclerView;
    private PieChart pieChart;

    private TextView incomeText, expenseText, balanceText;
    private EditText searchEditText;
    private LinearLayout filterOptionsLayout;
    private RadioGroup filterTypeToggle, filterModeToggle;
    private TextView filterCategoryTextView;

    private String currentFilterType = "All";
    private String currentFilterMode = "All";
    private String currentFilterCategory = "All Categories";

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private String currentCashbookId;

    private ActivityResultLauncher<Intent> chooseCategoryLauncher;

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
        setupLaunchers();
    }

    private void initializeUI() {
        incomeText = findViewById(R.id.incomeText);
        expenseText = findViewById(R.id.expenseText);
        balanceText = findViewById(R.id.balanceText);
        pieChart = findViewById(R.id.pieChart);
        searchEditText = findViewById(R.id.searchEditText);
        filterOptionsLayout = findViewById(R.id.filterOptionsLayout);
        filterTypeToggle = findViewById(R.id.filterTypeToggle);
        filterModeToggle = findViewById(R.id.filterModeToggle);
        filterCategoryTextView = findViewById(R.id.filterCategoryTextView);
    }

    private void setupRecyclerView() {
        transactionRecyclerView = findViewById(R.id.transactionRecyclerView);
        transactionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Correction: The constructor for TransactionAdapter takes two arguments.
        transactionAdapter = new TransactionAdapter(displayedTransactions, this);
        transactionRecyclerView.setAdapter(transactionAdapter);
    }

    private void setupClickListeners() {
        findViewById(R.id.toggleFilterButton).setOnClickListener(v -> toggleFilterOptionsVisibility());
        findViewById(R.id.clearAllFiltersButton).setOnClickListener(v -> clearAllFilters());
        findViewById(R.id.clearCategoryFilterButton).setOnClickListener(v -> clearCategoryFilter());
        filterCategoryTextView.setOnClickListener(v -> openChooseCategoryActivity());

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

        filterTypeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterInType) currentFilterType = "IN";
            else if (checkedId == R.id.filterOutType) currentFilterType = "OUT";
            else currentFilterType = "All";
            filterTransactions();
        });

        filterModeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterCashMode) currentFilterMode = "Cash";
            else if (checkedId == R.id.filterOnlineMode) currentFilterMode = "Online";
            else currentFilterMode = "All";
            filterTransactions();
        });

        // Using your original LinearLayout buttons for navigation
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomePage.class));
            finish();
        });
        findViewById(R.id.btnTransactions).setOnClickListener(v -> Toast.makeText(this, "Already on Transactions", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
    }

    private void setupLaunchers() {
        chooseCategoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentFilterCategory = result.getData().getStringExtra("selected_category_name");
                        filterCategoryTextView.setText(currentFilterCategory);
                        filterTransactions();
                    }
                });
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
            boolean matchesSearch = query.isEmpty() ||
                    (transaction.getTransactionCategory() != null && transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getPartyName() != null && transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getRemark() != null && transaction.getRemark().toLowerCase(Locale.getDefault()).contains(query));

            boolean matchesType = "All".equals(currentFilterType) || currentFilterType.equalsIgnoreCase(transaction.getType());
            boolean matchesMode = "All".equals(currentFilterMode) || currentFilterMode.equalsIgnoreCase(transaction.getPaymentMode());
            boolean matchesCategory = "All Categories".equals(currentFilterCategory) || currentFilterCategory.equals(transaction.getTransactionCategory());

            if (matchesSearch && matchesType && matchesMode && matchesCategory) {
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
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        pieChart.setData(new PieData(dataSet));
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setTextColor(Color.WHITE);
        pieChart.invalidate();
    }

    @Override
    public void onItemClick(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void toggleFilterOptionsVisibility() {
        filterOptionsLayout.setVisibility(filterOptionsLayout.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
    }

    private void clearCategoryFilter() {
        currentFilterCategory = "All Categories";
        filterCategoryTextView.setText(currentFilterCategory);
        filterTransactions();
    }

    private void clearAllFilters() {
        searchEditText.setText("");
        ((RadioButton)findViewById(R.id.filterAllType)).setChecked(true);
        ((RadioButton)findViewById(R.id.filterAllMode)).setChecked(true);
        clearCategoryFilter();
        toggleFilterOptionsVisibility();
    }

    private void openChooseCategoryActivity() {
        Intent intent = new Intent(this, ChooseCategoryActivity.class);
        intent.putExtra("selected_category_name", currentFilterCategory);
        chooseCategoryLauncher.launch(intent);
    }
}
