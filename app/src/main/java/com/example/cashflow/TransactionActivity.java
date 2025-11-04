package com.example.cashflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.cashflow.utils.CustomPieChartValueFormatter;
import com.example.cashflow.utils.ErrorHandler;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TransactionActivity - Displays transaction history with analytics
 *
 * Features:
 * - Monthly transaction view with pie chart analytics
 * - Search and filter capabilities
 * - PDF export functionality
 * - Integrated bottom navigation with cashbook switcher
 * - Google Sign-In integration
 * - Firebase Realtime Database
 *
 * Updated: November 2025 - Complete refactor with cashbook switching
 */
public class TransactionActivity extends AppCompatActivity {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // Data
    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<CashbookModel> cashbooks = new ArrayList<>();
    private Calendar currentMonthCalendar;

    // UI Components
    private PieChart pieChart;
    private TextView incomeText, expenseText, balanceText;
    private TextView monthTitleTextView, togglePieChartButton;
    private TextView categoriesCountTextView, highestCategoryTextView;
    private EditText searchEditText;
    private ImageView filterButton;
    private Button btnDownload;
    private LinearLayout pieChartHeader;
    private ImageButton monthBackwardButton, monthForwardButton;
    private TransactionItemFragment transactionFragment;

    // Bottom Navigation
    private FrameLayout btnHome, btnTransactions, btnCashbooks, btnSettings;
    private ImageView iconHome, iconTransactions, iconSettings, iconCashbooks;
    private TextView textHome, textTransactions, textSettings, textCashbooks;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private ValueEventListener cashbooksListener;
    private String currentCashbookId;
    private boolean isGuest;

    // Launchers
    private ActivityResultLauncher<Intent> filterLauncher;
    private ActivityResultLauncher<Intent> downloadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Get extras from intent
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        if (currentCashbookId == null && !isGuest) {
            showSnackbar("Error: No active cashbook found.");
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentMonthCalendar = Calendar.getInstance();

        initializeUI();
        setupTransactionFragment();
        setupClickListeners();
        setupBottomNavigation();
        setupFilterLauncher();
        setupDownloadLauncher();

        Log.d(TAG, "TransactionActivity created for cashbook: " + currentCashbookId);
    }

    /**
     * Initialize all UI components
     */
    private void initializeUI() {
        View summaryCardsLayout = findViewById(R.id.summaryCards);
        View pieChartLayout = findViewById(R.id.pieChartComponent);

        incomeText = summaryCardsLayout.findViewById(R.id.incomeText);
        expenseText = summaryCardsLayout.findViewById(R.id.expenseText);
        balanceText = summaryCardsLayout.findViewById(R.id.balanceText);

        pieChart = pieChartLayout.findViewById(R.id.pieChart);
        pieChartHeader = pieChartLayout.findViewById(R.id.pieChartHeader);
        monthTitleTextView = pieChartLayout.findViewById(R.id.monthTitle);
        monthBackwardButton = pieChartLayout.findViewById(R.id.monthBackwardButton);
        monthForwardButton = pieChartLayout.findViewById(R.id.monthForwardButton);
        togglePieChartButton = pieChartLayout.findViewById(R.id.togglePieChartButton);
        categoriesCountTextView = pieChartLayout.findViewById(R.id.categoriesCount);
        highestCategoryTextView = pieChartLayout.findViewById(R.id.highestCategory);

        searchEditText = findViewById(R.id.searchEditText);
        filterButton = findViewById(R.id.filterButton);
        btnDownload = findViewById(R.id.downloadReportButton);

        btnHome = findViewById(R.id.btnHome);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnCashbooks = findViewById(R.id.btnCashbookSwitch);
        btnSettings = findViewById(R.id.btnSettings);

        // Get icons and text views for bottom nav
        try {
            iconHome = findViewById(R.id.iconHome);
            iconTransactions = findViewById(R.id.iconTransactions);
            iconSettings = findViewById(R.id.iconSettings);
            iconCashbooks = findViewById(R.id.iconCashbookSwitch);

            textHome = findViewById(R.id.textHome);
            textTransactions = findViewById(R.id.textTransactions);
            textSettings = findViewById(R.id.textSettings);
            textCashbooks = findViewById(R.id.textCashbookSwitch);
        } catch (Exception e) {
            Log.w(TAG, "Error finding bottom nav icons/text", e);
        }
    }

    /**
     * Setup transaction list fragment
     */
    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(transaction -> {
            Intent intent = new Intent(this, EditTransactionActivity.class);
            intent.putExtra("transaction_model", transaction);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.transaction_fragment_container, transactionFragment)
                .commit();
    }

    /**
     * Setup bottom navigation with cashbook switcher
     */
    private void setupBottomNavigation() {
        updateBottomNavigationSelection(btnTransactions);

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("isGuest", isGuest);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        btnTransactions.setOnClickListener(v ->
                showSnackbar("Already on Transactions"));

        // Cashbook Switcher
        btnCashbooks.setOnClickListener(v -> openCashbookSwitcher());

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("isGuest", isGuest);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        // Load cashbooks for badge
        if (!isGuest) {
            loadCashbooksForBadge();
        }
    }

    /**
     * Update bottom navigation visual state
     */
    private void updateBottomNavigationSelection(View selectedButton) {
        // Reset all buttons
        resetBottomNavItem(iconHome, textHome);
        resetBottomNavItem(iconTransactions, textTransactions);
        resetBottomNavItem(iconSettings, textSettings);
        resetBottomNavItem(iconCashbooks, textCashbooks);

        // Set active color
        int activeColor = getColor(R.color.primary_blue);

        if (selectedButton.getId() == R.id.btnHome) {
            setBottomNavItemActive(iconHome, textHome, activeColor);
        } else if (selectedButton.getId() == R.id.btnTransactions) {
            setBottomNavItemActive(iconTransactions, textTransactions, activeColor);
        } else if (selectedButton.getId() == R.id.btnSettings) {
            setBottomNavItemActive(iconSettings, textSettings, activeColor);
        } else if (selectedButton.getId() == R.id.btnCashbookSwitch) {
            setBottomNavItemActive(iconCashbooks, textCashbooks, activeColor);
        }
    }

    /**
     * Reset bottom nav item to default
     */
    private void resetBottomNavItem(ImageView icon, TextView text) {
        if (icon != null) icon.setColorFilter(Color.WHITE);
        if (text != null) text.setTextColor(Color.WHITE);
    }

    /**
     * Set bottom nav item to active
     */
    private void setBottomNavItemActive(ImageView icon, TextView text, int color) {
        if (icon != null) icon.setColorFilter(color);
        if (text != null) text.setTextColor(color);
    }

    /**
     * Open CashbookSwitchActivity
     */
    private void openCashbookSwitcher() {
        if (isGuest) {
            showGuestLimitationDialog();
            return;
        }

        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);

        Log.d(TAG, "Opened CashbookSwitchActivity");
    }

    /**
     * Handle result from CashbookSwitchActivity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");

            if (newCashbookId != null && !newCashbookId.equals(currentCashbookId)) {
                switchCashbook(newCashbookId, cashbookName);
            }
        }
    }

    /**
     * Switch to a different cashbook
     */
    private void switchCashbook(String newCashbookId, String cashbookName) {
        // Remove old listener
        if (transactionsListener != null && currentCashbookId != null) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                mDatabase.child("users").child(user.getUid())
                        .child("cashbooks").child(currentCashbookId)
                        .child("transactions")
                        .removeEventListener(transactionsListener);
                Log.d(TAG, "Removed listener from previous cashbook");
            }
        }

        currentCashbookId = newCashbookId;
        allTransactions.clear();

        // Reload transactions for new cashbook
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForTransactions(currentUser.getUid());
        }

        showSnackbar("Switched to: " + cashbookName);
        Log.d(TAG, "Switched to cashbook: " + cashbookName);
    }

    /**
     * Load cashbooks for badge display
     */
    private void loadCashbooksForBadge() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for cashbook badge");
            return;
        }

        String userId = currentUser.getUid();

        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        if (cashbook.getCashbookId() == null) {
                            cashbook.setCashbookId(snapshot.getKey());
                        }
                        cashbooks.add(cashbook);
                    }
                }
                updateCashbookBadge();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks for badge", databaseError.toException());
            }
        };

        mDatabase.child("users").child(userId).child("cashbooks")
                .addValueEventListener(cashbooksListener);
    }

    /**
     * Update cashbook count badge on bottom navigation
     */
    private void updateCashbookBadge() {
        if (btnCashbooks == null || isGuest) return;

        try {
            int cashbookCount = cashbooks.size();

            // Remove existing badge if present
            View existingBadge = btnCashbooks.findViewWithTag("cashbook_badge");
            if (existingBadge != null) {
                btnCashbooks.removeView(existingBadge);
            }

            if (cashbookCount > 1) {
                // Create custom badge
                TextView badge = new TextView(this);
                badge.setTag("cashbook_badge");
                badge.setText(String.valueOf(cashbookCount));
                badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(android.view.Gravity.CENTER);
                badge.setTypeface(null, android.graphics.Typeface.BOLD);

                // Set background
                android.graphics.drawable.ShapeDrawable drawable =
                        new android.graphics.drawable.ShapeDrawable(
                                new android.graphics.drawable.shapes.OvalShape());
                drawable.getPaint().setColor(ContextCompat.getColor(this, R.color.primary_blue));
                badge.setBackground(drawable);

                // Set layout params
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        dpToPx(22), dpToPx(22),
                        android.view.Gravity.TOP | android.view.Gravity.END);
                params.setMargins(0, dpToPx(2), dpToPx(2), 0);
                badge.setLayoutParams(params);

                btnCashbooks.addView(badge);
                Log.d(TAG, "Badge updated: " + cashbookCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating cashbook badge", e);
        }
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Setup click listeners for UI elements
     */
    private void setupClickListeners() {
        pieChartHeader.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseAnalyticsActivity.class);
            intent.putExtra("all_transactions", (Serializable) allTransactions);
            startActivity(intent);
        });

        monthBackwardButton.setOnClickListener(v -> {
            currentMonthCalendar.add(Calendar.MONTH, -1);
            displayDataForCurrentMonth();
        });

        monthForwardButton.setOnClickListener(v -> {
            currentMonthCalendar.add(Calendar.MONTH, 1);
            displayDataForCurrentMonth();
        });

        togglePieChartButton.setOnClickListener(v -> {
            if (pieChart.getVisibility() == View.VISIBLE) {
                pieChart.setVisibility(View.GONE);
                togglePieChartButton.setText(getString(R.string.show_pie_chart));
            } else {
                pieChart.setVisibility(View.VISIBLE);
                togglePieChartButton.setText(getString(R.string.hide_pie_chart));
            }
        });

        btnDownload.setOnClickListener(v -> {
            Intent intent = new Intent(this, DownloadOptionsActivity.class);
            downloadLauncher.launch(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !isGuest) {
            startListeningForTransactions(currentUser.getUid());
        } else if (isGuest) {
            displayDataForCurrentMonth();
        }
    }

    /**
     * Start listening for transaction changes from Firebase
     */
    private void startListeningForTransactions(String userId) {
        if (currentCashbookId == null) {
            Log.w(TAG, "Cannot listen for transactions: No cashbook ID");
            return;
        }

        DatabaseReference transactionsRef = mDatabase.child("users")
                .child(userId)
                .child("cashbooks")
                .child(currentCashbookId)
                .child("transactions");

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
                Collections.sort(allTransactions, (t1, t2) ->
                        Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                displayDataForCurrentMonth();
                Log.d(TAG, "Loaded " + allTransactions.size() + " transactions");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showSnackbar("Failed to load transactions");
                ErrorHandler.handleFirebaseError(TransactionActivity.this, databaseError);
            }
        });
    }

    /**
     * Display data for the current month
     */
    private void displayDataForCurrentMonth() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthTitleTextView.setText(sdf.format(currentMonthCalendar.getTime()));

        List<TransactionModel> monthlyTransactions = allTransactions.stream()
                .filter(t -> {
                    Calendar transactionCal = Calendar.getInstance();
                    transactionCal.setTimeInMillis(t.getTimestamp());
                    return transactionCal.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                            transactionCal.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
                }).collect(Collectors.toList());

        updateTotals(monthlyTransactions);
        setupStyledPieChart(monthlyTransactions);

        if (transactionFragment != null) {
            transactionFragment.updateTransactions(monthlyTransactions);
        }
    }

    /**
     * Update financial summary totals
     */
    @SuppressLint("SetTextI18n")
    private void updateTotals(List<TransactionModel> transactions) {
        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel transaction : transactions) {
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else {
                totalExpense += transaction.getAmount();
            }
        }

        incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));

        // Set balance color
        if (totalIncome - totalExpense >= 0) {
            balanceText.setTextColor(getColor(R.color.income_green));
        } else {
            balanceText.setTextColor(getColor(R.color.expense_red));
        }
    }

    /**
     * Setup styled pie chart with expense categories
     */
    private void setupStyledPieChart(List<TransactionModel> transactionsForMonth) {
        // Process expense data
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0f;
        String highestCategory = "-";
        float maxExpense = 0f;

        for (TransactionModel transaction : transactionsForMonth) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory() != null ?
                        transaction.getTransactionCategory() : "Other";
                float amount = (float) transaction.getAmount();
                expenseByCategory.put(category,
                        expenseByCategory.getOrDefault(category, 0f) + amount);

                if (expenseByCategory.get(category) > maxExpense) {
                    maxExpense = expenseByCategory.get(category);
                    highestCategory = category;
                }
                totalExpense += amount;
            }
        }

        categoriesCountTextView.setText(String.valueOf(expenseByCategory.size()));
        highestCategoryTextView.setText(highestCategory);

        if (totalExpense == 0) {
            pieChart.clear();
            pieChart.setCenterText("No Expenses");
            pieChart.invalidate();
            return;
        }

        // Create pie entries
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        // Modern color palette
        colors.add(Color.parseColor("#F2C94C")); // Yellow
        colors.add(Color.parseColor("#2DD4BF")); // Teal
        colors.add(Color.parseColor("#F87171")); // Coral
        colors.add(Color.parseColor("#A78BFA")); // Purple
        colors.add(Color.parseColor("#34D399")); // Green
        colors.add(Color.parseColor("#60A5FA")); // Blue
        colors.add(Color.parseColor("#FBBF24")); // Amber
        colors.add(Color.parseColor("#F472B6")); // Pink

        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            float percentage = entry.getValue() / totalExpense * 100;
            entries.add(new PieEntry(percentage, entry.getKey()));
        }

        // Configure dataset
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setColors(colors);

        // External labels with connector lines
        dataSet.setValueLinePart1OffsetPercentage(85f);
        dataSet.setValueLinePart1Length(0.25f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setValueLineColor(Color.parseColor("#828282"));
        dataSet.setValueLineWidth(1.5f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setDrawValues(true);

        // Create pie data
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new CustomPieChartValueFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.WHITE);

        // Configure chart
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setRotationEnabled(true);
        pieChart.setDrawHoleEnabled(false);
        pieChart.setDrawEntryLabels(false);
        pieChart.setExtraOffsets(25, 25, 25, 25);
        pieChart.setBackgroundColor(Color.TRANSPARENT);

        // Set data and animate
        pieChart.setData(data);
        pieChart.invalidate();
        pieChart.animateY(1200);
    }

    /**
     * Setup search and filter functionality
     */
    private void setupFilterLauncher() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                applySearchFilter(query);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Apply search filter to transactions
     */
    private void applySearchFilter(String searchQuery) {
        List<TransactionModel> filteredTransactions = allTransactions.stream()
                .filter(t -> {
                    Calendar transactionCal = Calendar.getInstance();
                    transactionCal.setTimeInMillis(t.getTimestamp());
                    return transactionCal.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                            transactionCal.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
                })
                .filter(t -> searchQuery.isEmpty() ||
                        (t.getTransactionCategory() != null &&
                                t.getTransactionCategory().toLowerCase().contains(searchQuery.toLowerCase())) ||
                        (t.getDescription() != null &&
                                t.getDescription().toLowerCase().contains(searchQuery.toLowerCase())))
                .collect(Collectors.toList());

        updateTotals(filteredTransactions);
        setupStyledPieChart(filteredTransactions);

        if (transactionFragment != null) {
            transactionFragment.updateTransactions(filteredTransactions);
        }
    }

    /**
     * Setup PDF download launcher
     */
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
                            exportTransactionsToPdf(startDate, endDate, entryType, paymentMode);
                        } else {
                            requestPermissions();
                        }
                    }
                }
        );
    }

    /**
     * Check storage permissions
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Request storage permissions
     */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSnackbar("Storage permission granted");
            } else {
                showSnackbar("Storage permission denied");
            }
        }
    }

    /**
     * Export transactions to PDF
     */
    private void exportTransactionsToPdf(long startDate, long endDate,
                                         String entryType, String paymentMode) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                    "CashFlow_Report_" + System.currentTimeMillis() + ".pdf");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            } else {
                uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            }

            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                Document document = new Document(PageSize.A4);
                PdfWriter.getInstance(document, outputStream);
                document.open();

                // Add title
                Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
                Paragraph title = new Paragraph("CashFlow Transaction Report", titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                document.add(title);
                document.add(new Paragraph(" "));

                // Add date range
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                String dateRange = "Date Range: " + sdf.format(new Date(startDate)) +
                        " to " + sdf.format(new Date(endDate));
                document.add(new Paragraph(dateRange));
                document.add(new Paragraph(" "));

                // Filter transactions
                List<TransactionModel> filteredTransactions = allTransactions.stream()
                        .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                        .filter(t -> entryType == null || entryType.equals("All") ||
                                t.getType().equals(entryType))
                        .collect(Collectors.toList());

                // Create table
                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2, 2, 1, 2});

                // Add headers
                Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
                table.addCell(new PdfPCell(new Phrase("Date", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Category", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Type", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Amount", headerFont)));

                // Add transaction data
                Font cellFont = new Font(Font.FontFamily.HELVETICA, 10);
                for (TransactionModel transaction : filteredTransactions) {
                    table.addCell(new PdfPCell(new Phrase(
                            sdf.format(new Date(transaction.getTimestamp())), cellFont)));
                    table.addCell(new PdfPCell(new Phrase(
                            transaction.getTransactionCategory() != null ?
                                    transaction.getTransactionCategory() : "N/A", cellFont)));
                    table.addCell(new PdfPCell(new Phrase(transaction.getType(), cellFont)));
                    table.addCell(new PdfPCell(new Phrase(
                            "₹" + String.format("%.2f", transaction.getAmount()), cellFont)));
                }

                document.add(table);
                document.close();
                outputStream.close();

                showSnackbar("PDF report exported successfully!");
                Log.d(TAG, "PDF exported successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error exporting PDF", e);
            showSnackbar("Error exporting PDF: " + e.getMessage());
        }
    }

    /**
     * Show guest limitation dialog
     */
    private void showGuestLimitationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Guest Mode Limitation")
                .setMessage("This feature is not available in guest mode. " +
                        "Please sign up to access full functionality.")
                .setPositiveButton("Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(this, SignupActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
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

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();

            // Remove transactions listener
            if (transactionsListener != null && currentCashbookId != null) {
                mDatabase.child("users").child(userId)
                        .child("cashbooks").child(currentCashbookId)
                        .child("transactions")
                        .removeEventListener(transactionsListener);
                transactionsListener = null;
                Log.d(TAG, "Transactions listener removed");
            }

            // Remove cashbooks listener
            if (cashbooksListener != null) {
                mDatabase.child("users").child(userId)
                        .child("cashbooks")
                        .removeEventListener(cashbooksListener);
                cashbooksListener = null;
                Log.d(TAG, "Cashbooks listener removed");
            }
        }

        Log.d(TAG, "TransactionActivity destroyed");
    }
}
