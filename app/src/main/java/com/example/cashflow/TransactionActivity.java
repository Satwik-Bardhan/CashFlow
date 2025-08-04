package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";

    private ArrayList<TransactionModel> allTransactions;
    private ArrayList<TransactionModel> displayedTransactions;
    private TransactionAdapter transactionAdapter;
    private TextView incomeText, expenseText, balanceText;
    private RecyclerView transactionRecyclerView;
    private Button downloadReportButton;
    private PieChart pieChart;

    private EditText searchEditText;
    private ImageView searchButton;
    private ImageView toggleFilterButton;
    private LinearLayout filterOptionsLayout;

    private RadioGroup filterTypeToggle;
    private RadioButton filterAllType, filterInType, filterOutType;
    private RadioGroup filterModeToggle;
    private RadioButton filterAllMode, filterCashMode, filterOnlineMode;
    private TextView filterCategoryTextView;
    private ImageView clearCategoryFilterButton;
    private ImageView clearAllFiltersButton;

    private String currentFilterType; // Initialized in onCreate
    private String currentFilterMode; // Initialized in onCreate
    private String currentFilterCategory; // Initialized in onCreate
    private String currentFilterCategoryColor; // Initialized in onCreate


    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;

    private LinearLayout btnTransactions;
    private LinearLayout btnHome;
    private LinearLayout btnSettings;

    private String currentCashbookId;

    private ActivityResultLauncher<Intent> chooseCategoryLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transaction_activity);

        // FIX: Initialize context-dependent variables here
        currentFilterType = "All";
        currentFilterMode = "All";
        currentFilterCategory = "All Categories";
        currentFilterCategoryColor = "";


        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View rootLayout = findViewById(R.id.main_root_layout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

                LinearLayout fixedBottomContainer = findViewById(R.id.fixedBottomContainer);
                if (fixedBottomContainer != null) {
                    fixedBottomContainer.setPadding(
                            fixedBottomContainer.getPaddingLeft(),
                            fixedBottomContainer.getPaddingTop(),
                            fixedBottomContainer.getPaddingRight(),
                            systemBars.bottom
                    );
                }
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_root_layout) not found. Insets might not be applied correctly.");
            Toast.makeText(this, "Layout error: root view not found for insets.", Toast.LENGTH_LONG).show();
        }

        if (getIntent() != null) {
            currentCashbookId = getIntent().getStringExtra("cashbook_id");
        }
        if (currentCashbookId == null) {
            Log.e(TAG, "No cashbook ID received. This activity may not function correctly.");
            Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
        }


        incomeText = findViewById(R.id.incomeText);
        expenseText = findViewById(R.id.expenseText);
        balanceText = findViewById(R.id.balanceText);
        transactionRecyclerView = findViewById(R.id.transactionRecyclerView);
        downloadReportButton = findViewById(R.id.downloadReportButton);
        pieChart = findViewById(R.id.pieChart);
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        toggleFilterButton = findViewById(R.id.toggleFilterButton);
        filterOptionsLayout = findViewById(R.id.filterOptionsLayout);


        filterTypeToggle = findViewById(R.id.filterTypeToggle);
        filterAllType = findViewById(R.id.filterAllType);
        filterInType = findViewById(R.id.filterInType);
        filterOutType = findViewById(R.id.filterOutType);
        filterModeToggle = findViewById(R.id.filterModeToggle);
        filterAllMode = findViewById(R.id.filterAllMode);
        filterCashMode = findViewById(R.id.filterCashMode);
        filterOnlineMode = findViewById(R.id.filterOnlineMode);
        filterCategoryTextView = findViewById(R.id.filterCategoryTextView);
        clearCategoryFilterButton = findViewById(R.id.clearCategoryFilterButton);
        clearAllFiltersButton = findViewById(R.id.clearAllFiltersButton);


        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);
        btnSettings = findViewById(R.id.btnSettings);


        if (incomeText == null || expenseText == null || balanceText == null ||
                transactionRecyclerView == null || downloadReportButton == null || pieChart == null ||
                searchEditText == null || searchButton == null || toggleFilterButton == null || filterOptionsLayout == null ||
                filterTypeToggle == null || filterAllType == null || filterInType == null || filterOutType == null ||
                filterModeToggle == null || filterAllMode == null || filterCashMode == null || filterOnlineMode == null ||
                filterCategoryTextView == null || clearCategoryFilterButton == null || clearAllFiltersButton == null ||
                btnTransactions == null || btnHome == null || btnSettings == null) {
            Log.e(TAG, "One or more UI components not found in activity_transaction_activity.xml");
            Toast.makeText(this, "Error: Missing UI elements. Check layout IDs.", Toast.LENGTH_LONG).show();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        allTransactions = new ArrayList<>();
        displayedTransactions = new ArrayList<>();
        transactionAdapter = new TransactionAdapter(displayedTransactions, this);
        transactionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionRecyclerView.setAdapter(transactionAdapter);

        chooseCategoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentFilterCategory = result.getData().getStringExtra("selected_category_name");
                        currentFilterCategoryColor = result.getData().getStringExtra("selected_category_color_hex");
                        updateCategoryFilterDisplay();
                        filterTransactions(searchEditText.getText().toString());
                        Log.d(TAG, "Category filter selected: " + currentFilterCategory + ", Color: " + currentFilterCategoryColor);
                    } else {
                        Log.d(TAG, "Category selection cancelled or failed.");
                    }
                }
        );


        downloadReportButton.setOnClickListener(v -> {
            Toast.makeText(TransactionActivity.this, "Download Report Clicked", Toast.LENGTH_SHORT).show();
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchButton.setOnClickListener(v -> {
            filterTransactions(searchEditText.getText().toString());
        });

        toggleFilterButton.setOnClickListener(v -> toggleFilterOptionsVisibility());

        filterTypeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterAllType) currentFilterType = "All";
            else if (checkedId == R.id.filterInType) currentFilterType = "IN";
            else if (checkedId == R.id.filterOutType) currentFilterType = "OUT";
            filterTransactions(searchEditText.getText().toString());
        });

        filterModeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterAllMode) currentFilterMode = "All";
            else if (checkedId == R.id.filterCashMode) currentFilterMode = "Cash";
            else if (checkedId == R.id.filterOnlineMode) currentFilterMode = "Online";
            filterTransactions(searchEditText.getText().toString());
        });

        filterCategoryTextView.setOnClickListener(v -> {
            Intent intent = new Intent(TransactionActivity.this, ChooseCategoryActivity.class);
            intent.putExtra("selected_category_name", currentFilterCategory);
            intent.putExtra("selected_category_color_hex", currentFilterCategoryColor);
            chooseCategoryLauncher.launch(intent);
        });

        clearCategoryFilterButton.setOnClickListener(v -> {
            currentFilterCategory = "All Categories";
            currentFilterCategoryColor = "";
            updateCategoryFilterDisplay();
            filterTransactions(searchEditText.getText().toString());
        });

        clearAllFiltersButton.setOnClickListener(v -> clearAllFilters());


        updateCategoryFilterDisplay();


        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(TransactionActivity.this, HomePage.class);
            startActivity(intent);
            finish();
        });

        btnTransactions.setOnClickListener(v -> {
            Toast.makeText(TransactionActivity.this, "Already on Transactions", Toast.LENGTH_SHORT).show();
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(TransactionActivity.this, SettingsActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void toggleFilterOptionsVisibility() {
        if (filterOptionsLayout.getVisibility() == View.GONE) {
            filterOptionsLayout.setVisibility(View.VISIBLE);
        } else {
            filterOptionsLayout.setVisibility(View.GONE);
        }
    }

    private void updateCategoryFilterDisplay() {
        if (currentFilterCategory != null && !currentFilterCategory.isEmpty() && !currentFilterCategory.equals("All Categories")) {
            filterCategoryTextView.setText(currentFilterCategory);
            try {
                filterCategoryTextView.setTextColor(Color.parseColor(currentFilterCategoryColor));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid color hex for category filter: " + currentFilterCategoryColor, e);
                filterCategoryTextView.setTextColor(ContextCompat.getColor(this, R.color.black));
            }
        } else {
            filterCategoryTextView.setText("All Categories");
            filterCategoryTextView.setTextColor(ContextCompat.getColor(this, R.color.black));
        }
    }

    private void clearAllFilters() {
        searchEditText.setText("");
        filterAllType.setChecked(true);
        filterAllMode.setChecked(true);
        currentFilterCategory = "All Categories";
        currentFilterCategoryColor = "";
        updateCategoryFilterDisplay();
        filterOptionsLayout.setVisibility(View.GONE);
        filterTransactions("");
        Toast.makeText(this, "All filters cleared.", Toast.LENGTH_SHORT).show();
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "onStart: User logged in, starting transaction listener.");
            startListeningForTransactions(currentUser.getUid());
        } else {
            Log.d(TAG, "onStart: No user, prompting login.");
            Toast.makeText(this, "Please log in to view transactions.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && transactionsListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
            Log.d(TAG, "Firebase listener removed in onStop.");
        }
    }

    private void startListeningForTransactions(String userId) {
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
            Log.d(TAG, "Removed existing listener before attaching new one.");
        }

        if (currentCashbookId == null) {
            Log.w(TAG, "startListeningForTransactions: currentCashbookId is null. Cannot attach listener.");
            Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
            return;
        }

        mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: Data received from Firebase for cashbook: " + currentCashbookId + ". Raw count: " + dataSnapshot.getChildrenCount());
                allTransactions.clear();
                int deserializedCount = 0;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = new TransactionModel();
                    transaction.setTransactionId(snapshot.getKey());

                    transaction.setAmount(snapshot.child("amount").getValue(Double.class) != null ? snapshot.child("amount").getValue(Double.class) : 0.0);
                    transaction.setDate(snapshot.child("date").getValue(String.class) != null ? snapshot.child("date").getValue(String.class) : "Unknown Date");
                    transaction.setType(snapshot.child("type").getValue(String.class) != null ? snapshot.child("type").getValue(String.class) : "OUT");
                    transaction.setPaymentMode(snapshot.child("paymentMode").getValue(String.class) != null ? snapshot.child("paymentMode").getValue(String.class) : "Cash");
                    transaction.setRemark(snapshot.child("remark").getValue(String.class) != null ? snapshot.child("remark").getValue(String.class) : "");
                    transaction.setPartyName(snapshot.child("partyName").getValue(String.class) != null ? snapshot.child("partyName").getValue(String.class) : "");
                    transaction.setTransactionCategory(snapshot.child("transactionCategory").getValue(String.class) != null ? snapshot.child("transactionCategory").getValue(String.class) : "Other");
                    transaction.setTimestamp(snapshot.child("timestamp").getValue(Long.class) != null ? snapshot.child("timestamp").getValue(Long.class) : 0L);


                    allTransactions.add(transaction);
                    deserializedCount++;
                }
                Log.d(TAG, "onDataChange: Successfully deserialized " + deserializedCount + " out of " + dataSnapshot.getChildrenCount() + " raw transactions.");
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                Log.d(TAG, "Transactions sorted. Total: " + allTransactions.size());

                filterTransactions(searchEditText.getText().toString());
                Log.d(TAG, "Transactions loaded and UI updated.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "loadPost:onCancelled", databaseError.toException());
                Toast.makeText(TransactionActivity.this, "Failed to load transactions: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        Log.d(TAG, "Firebase listener attached for user: " + userId + " to cashbook: " + currentCashbookId);
    }

    private void filterTransactions(String query) {
        displayedTransactions.clear();
        String lowerCaseQuery = query.toLowerCase(Locale.getDefault());

        for (TransactionModel transaction : allTransactions) {
            String transactionCategory = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory().toLowerCase(Locale.getDefault()) : "";
            String transactionType = transaction.getType() != null ? transaction.getType().toLowerCase(Locale.getDefault()) : "";
            String transactionRemark = transaction.getRemark() != null ? transaction.getRemark().toLowerCase(Locale.getDefault()) : "";
            String transactionPaymentMode = transaction.getPaymentMode() != null ? transaction.getPaymentMode().toLowerCase(Locale.getDefault()) : "";
            String transactionPartyName = transaction.getPartyName() != null ? transaction.getPartyName().toLowerCase(Locale.getDefault()) : "";


            boolean matchesSearch = TextUtils.isEmpty(query) ||
                    transactionCategory.contains(lowerCaseQuery) ||
                    transactionType.contains(lowerCaseQuery) ||
                    String.valueOf(transaction.getAmount()).contains(lowerCaseQuery) ||
                    transactionRemark.contains(lowerCaseQuery) ||
                    transactionPaymentMode.contains(lowerCaseQuery) ||
                    transactionPartyName.contains(lowerCaseQuery);

            boolean matchesType = currentFilterType.equals("All") ||
                    transactionType.equalsIgnoreCase(currentFilterType);

            boolean matchesMode = currentFilterMode.equals("All") ||
                    transactionPaymentMode.equalsIgnoreCase(currentFilterMode);

            boolean matchesCategory = currentFilterCategory.equals("All Categories") ||
                    transactionCategory.equalsIgnoreCase(currentFilterCategory);

            if (matchesSearch && matchesType && matchesMode && matchesCategory) {
                displayedTransactions.add(transaction);
            }
        }
        Log.d(TAG, "filterTransactions: Query='" + query + "', Type='" + currentFilterType +
                "', Mode='" + currentFilterMode + "', Category='" + currentFilterCategory +
                "'. Displayed: " + displayedTransactions.size());
        updateUIWithTransactions();
    }

    @SuppressLint("SetTextI18n")
    private void updateUIWithTransactions() {
        Log.d(TAG, "updateUIWithTransactions: Notifying adapter and updating summary/chart.");
        transactionAdapter.notifyDataSetChanged();
        calculateTotals(displayedTransactions);
        setupPieChart(displayedTransactions);
    }

    @SuppressLint("SetTextI18n")
    private void calculateTotals(@NonNull ArrayList<TransactionModel> transactionsToCalculate) {
        Log.d(TAG, "calculateTotals: Calculating for " + transactionsToCalculate.size() + " transactions.");
        double totalIncome = 0;
        double totalExpense = 0;

        for (TransactionModel transaction : transactionsToCalculate) {
            if (transaction.getType().equalsIgnoreCase("IN")) {
                totalIncome += transaction.getAmount();
            } else if (transaction.getType().equalsIgnoreCase("OUT")) {
                totalExpense += transaction.getAmount();
            }
        }

        double balance = totalIncome - totalExpense;

        incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", balance));
        Log.d(TAG, "Totals updated: Income=" + totalIncome + ", Expense=" + totalExpense + ", Balance=" + balance);
    }

    private void setupPieChart(@NonNull ArrayList<TransactionModel> transactionsForChart) {
        Log.d(TAG, "setupPieChart: Setting up chart for " + transactionsForChart.size() + " transactions.");
        ArrayList<PieEntry> entries = new ArrayList<>();
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalChartableExpense = 0;

        for (TransactionModel transaction : transactionsForChart) {
            if (transaction.getType().equalsIgnoreCase("OUT")) {
                float currentAmount = (float) transaction.getAmount();
                String category = transaction.getTransactionCategory();
                if (category == null || category.isEmpty()) {
                    category = "Uncategorized";
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        expenseByCategory.put(category, expenseByCategory.getOrDefault(category, 0f) + currentAmount);
                    }
                }
                totalChartableExpense += currentAmount;
            }
        }

        if (totalChartableExpense == 0) {
            pieChart.setCenterText("No Expenses");
            entries.add(new PieEntry(1f, ""));
        } else {
            pieChart.setCenterText("Expenses\n₹" + String.format(Locale.US, "%.2f", totalChartableExpense));
            for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "Expense Categories");
        if (totalChartableExpense == 0) {
            dataSet.setColor(Color.parseColor("#424242"));
        } else {
            dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        }
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);

        PieData data = new PieData(dataSet);

        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setTransparentCircleRadius(55f);
        pieChart.setHoleRadius(40f);
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(16f);
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.getDescription().setEnabled(false);

        Legend legend = pieChart.getLegend();
        if (legend != null) {
            legend.setEnabled(true);
            legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
            legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
            legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
            legend.setDrawInside(false);
            legend.setWordWrapEnabled(true);
            legend.setTextColor(Color.WHITE);
            legend.setTextSize(12f);
            legend.setForm(Legend.LegendForm.CIRCLE);
            legend.setFormSize(8f);
            legend.setFormToTextSpace(4f);
            legend.setXEntrySpace(10f);
            legend.setYEntrySpace(5f);
        }

        pieChart.animateY(1000);
        pieChart.invalidate();
        Log.d(TAG, "Pie chart updated.");
    }

    @Override
    public void onItemClick(TransactionModel transaction, String transactionId) {
        Log.d(TAG, "Transaction clicked: " + transaction.getTransactionCategory() + ", ID: " + transactionId);
        Intent intent = new Intent(TransactionActivity.this, TransactionDetailActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("transaction_id", transactionId);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }
}