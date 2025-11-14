package com.example.cashflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.cashflow.databinding.ActivityTransactionActivityBinding;
import com.example.cashflow.databinding.LayoutBottomNavigationBinding;
import com.example.cashflow.databinding.LayoutPieChartBinding;
import com.example.cashflow.databinding.LayoutSearchBarBinding;
import com.example.cashflow.databinding.LayoutSummaryCardsBinding;
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

public class TransactionActivity extends AppCompatActivity {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // Data
    private List<TransactionModel> allTransactions = new ArrayList<>(); // For PDF export
    private List<CashbookModel> cashbooks = new ArrayList<>(); // For badge
    private Calendar currentMonthCalendar;

    // [FIX] Use ViewBinding for all UI components
    private ActivityTransactionActivityBinding binding;
    private LayoutSummaryCardsBinding summaryBinding;
    private LayoutPieChartBinding pieChartBinding;
    private LayoutSearchBarBinding searchBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    private TransactionItemFragment transactionFragment;

    // [FIX] Add ViewModel
    private TransactionViewModel viewModel;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener cashbooksListener; // Only for badge
    private String currentCashbookId;
    private FirebaseUser currentUser; // [FIX] Added currentUser

    // Launchers
    private ActivityResultLauncher<Intent> filterLauncher;
    private ActivityResultLauncher<Intent> downloadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // [FIX] Inflate using ViewBinding
        binding = ActivityTransactionActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Get extras from intent
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser(); // [FIX] Get current user
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentMonthCalendar = Calendar.getInstance();

        if (currentCashbookId == null || currentUser == null) {
            showSnackbar("Error: No active cashbook found or user not logged in.");
            finish();
            return;
        }

        initializeUI(); // [FIX] Simplified to just bind child views

        // [FIX] Initialize ViewModel
        initViewModel();

        setupTransactionFragment();
        setupClickListeners();
        setupBottomNavigation();
        setupLaunchers();

        // [FIX] Observe ViewModel LiveData
        observeViewModel();

        Log.d(TAG, "TransactionActivity created for cashbook: " + currentCashbookId);
    }

    private void initializeUI() {
        // [FIX] Bind included layouts
        summaryBinding = binding.summaryCards;
        pieChartBinding = binding.pieChartComponent;
        searchBinding = binding.searchBarContainer;
        bottomNavBinding = binding.bottomNavCard;
    }

    // [FIX] New method to initialize ViewModel
    private void initViewModel() {
        TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), currentCashbookId);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);
    }

    // [FIX] New method to observe LiveData
    private void observeViewModel() {
        if (viewModel == null) return;

        // Observe filtered transactions
        viewModel.getFilteredTransactions().observe(this, transactions -> {
            Log.d(TAG, "ViewModel observed " + transactions.size() + " filtered transactions.");
            this.allTransactions = transactions; // Update local list for PDF export
            displayDataForCurrentMonth();
        });

        // Observe loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (transactionFragment != null) {
                transactionFragment.showLoading(isLoading);
            }
        });

        // Observe error messages
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showSnackbar(error);
                viewModel.clearError(); // Clear error after showing
            }
        });
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(transaction -> {
            Intent intent = new Intent(this, EditTransactionActivity.class);
            intent.putExtra("transaction_model", (Serializable) transaction);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.transaction_fragment_container, transactionFragment)
                .commit();
    }

    private void setupBottomNavigation() {
        bottomNavBinding.btnTransactions.setSelected(true);

        bottomNavBinding.btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });

        bottomNavBinding.btnTransactions.setOnClickListener(v ->
                showSnackbar("Already on Transactions"));

        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        bottomNavBinding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });

        loadCashbooksForBadge();
    }

    // [FIX] Renamed and simplified from original
    private void setupLaunchers() {
        setupFilterLauncher();
        setupDownloadLauncher();
    }

    private void openCashbookSwitcher() {
        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
        Log.d(TAG, "Opened CashbookSwitchActivity");
    }

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

    private void switchCashbook(String newCashbookId, String cashbookName) {
        currentCashbookId = newCashbookId;
        showSnackbar("Switched to: " + cashbookName);
        Log.d(TAG, "Switched to cashbook: " + cashbookName);

        // [FIX] Save the new active cashbook ID
        saveActiveCashbookId(currentCashbookId);

        // Re-initialize ViewModel with the new ID
        initViewModel();
        // Re-observe LiveData
        observeViewModel();
        // Reload badge
        loadCashbooksForBadge();
    }

    // [FIX] Added helper to save active cashbook ID
    private void saveActiveCashbookId(String cashbookId) {
        if (currentUser == null) return;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUser.getUid(), cashbookId).apply();
    }

    private void loadCashbooksForBadge() {
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for cashbook badge");
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference cashbooksRef = mDatabase.child("users").child(userId).child("cashbooks");

        if (cashbooksListener != null) {
            cashbooksRef.removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        cashbook.setCashbookId(snapshot.getKey());
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
        cashbooksRef.addValueEventListener(cashbooksListener);
    }

    private void updateCashbookBadge() {
        if (bottomNavBinding.btnCashbookSwitch == null) return;

        try {
            int cashbookCount = cashbooks.size();
            View existingBadge = bottomNavBinding.btnCashbookSwitch.findViewWithTag("cashbook_badge");
            if (existingBadge != null) {
                bottomNavBinding.btnCashbookSwitch.removeView(existingBadge);
            }

            if (cashbookCount > 1) {
                TextView badge = new TextView(this);
                badge.setTag("cashbook_badge");
                badge.setText(String.valueOf(cashbookCount));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(Gravity.CENTER);
                badge.setTypeface(null, Typeface.BOLD);

                ShapeDrawable drawable = new ShapeDrawable(new android.graphics.drawable.shapes.OvalShape());
                drawable.getPaint().setColor(ThemeUtil.getThemeAttrColor(this, R.attr.balanceColor));
                badge.setBackground(drawable);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        dpToPx(22), dpToPx(22), Gravity.TOP | Gravity.END);
                params.setMargins(0, dpToPx(2), dpToPx(2), 0);
                badge.setLayoutParams(params);

                bottomNavBinding.btnCashbookSwitch.addView(badge);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating cashbook badge", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupClickListeners() {
        pieChartBinding.pieChartHeader.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseAnalyticsActivity.class);
            // [FIX] Pass cashbookId, not the whole list
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
        });

        pieChartBinding.monthBackwardButton.setOnClickListener(v -> {
            currentMonthCalendar.add(Calendar.MONTH, -1);
            displayDataForCurrentMonth();
        });

        pieChartBinding.monthForwardButton.setOnClickListener(v -> {
            currentMonthCalendar.add(Calendar.MONTH, 1);
            displayDataForCurrentMonth();
        });

        pieChartBinding.togglePieChartButton.setOnClickListener(v -> {
            if (pieChartBinding.pieChart.getVisibility() == View.VISIBLE) {
                pieChartBinding.pieChart.setVisibility(View.GONE);
                pieChartBinding.togglePieChartButton.setText(getString(R.string.show_pie_chart));
            } else {
                pieChartBinding.pieChart.setVisibility(View.VISIBLE);
                pieChartBinding.togglePieChartButton.setText(getString(R.string.hide_pie_chart));
            }
        });

        binding.downloadReportButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, DownloadOptionsActivity.class);
            downloadLauncher.launch(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // [FIX] ViewModel handles loading, but we need to refresh data for the current month
        // This will be triggered by the LiveData observer, but we call it once
        // to ensure the month title is set correctly.
        displayDataForCurrentMonth();
    }

    // [FIX] Removed startListeningForTransactions, ViewModel handles this.

    private void displayDataForCurrentMonth() {
        if (allTransactions == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        pieChartBinding.monthTitle.setText(sdf.format(currentMonthCalendar.getTime()));

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

        summaryBinding.incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        summaryBinding.expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        summaryBinding.balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));

        // [FIX] Use theme-aware colors
        int incomeColor = ThemeUtil.getThemeAttrColor(this, R.attr.incomeColor);
        int expenseColor = ThemeUtil.getThemeAttrColor(this, R.attr.expenseColor);

        summaryBinding.balanceText.setTextColor(totalIncome - totalExpense >= 0 ? incomeColor : expenseColor);
    }

    private void setupStyledPieChart(List<TransactionModel> transactionsForMonth) {
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

        pieChartBinding.categoriesCount.setText(String.valueOf(expenseByCategory.size()));
        pieChartBinding.highestCategory.setText(highestCategory);

        // [FIX] Get theme-aware text color
        int textColor = ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary);

        if (totalExpense == 0) {
            pieChartBinding.pieChart.clear();
            pieChartBinding.pieChart.setCenterText("No Expenses");
            pieChartBinding.pieChart.setCenterTextColor(textColor); // [FIX]
            pieChartBinding.pieChart.invalidate();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        int colorIndex = 0;

        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            float percentage = entry.getValue() / totalExpense * 100;
            entries.add(new PieEntry(percentage, entry.getKey()));
            // [FIX] Use CategoryColorUtil to get consistent colors
            colors.add(CategoryColorUtil.getCategoryColor(this, entry.getKey()));
            colorIndex++;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setColors(colors);
        dataSet.setValueLinePart1OffsetPercentage(85f);
        dataSet.setValueLinePart1Length(0.25f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setValueLineColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorSecondary)); // [FIX]
        dataSet.setValueLineWidth(1.5f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setDrawValues(true);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new CustomPieChartValueFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(textColor); // [FIX]

        pieChartBinding.pieChart.setUsePercentValues(true);
        pieChartBinding.pieChart.getDescription().setEnabled(false);
        pieChartBinding.pieChart.getLegend().setEnabled(false);
        pieChartBinding.pieChart.setRotationEnabled(true);
        pieChartBinding.pieChart.setDrawHoleEnabled(false);
        pieChartBinding.pieChart.setDrawEntryLabels(false);
        pieChartBinding.pieChart.setExtraOffsets(25, 25, 25, 25);
        pieChartBinding.pieChart.setBackgroundColor(Color.TRANSPARENT);

        pieChartBinding.pieChart.setData(data);
        pieChartBinding.pieChart.invalidate();
        pieChartBinding.pieChart.animateY(1200);
    }

    private void setupFilterLauncher() {
        searchBinding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // [FIX] Use ViewModel to filter
                if (viewModel != null) {
                    viewModel.filter(s.toString(), 0, 0, "All", null, null);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchBinding.filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            // [FIX] Pass current cashbookId to filters
            intent.putExtra("cashbook_id", currentCashbookId);
            filterLauncher.launch(intent); // [FIX] Use launcher
        });

        // [FIX] Initialize the launcher
        filterLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        // [FIX] Get all filter data back and apply it
                        long startDate = data.getLongExtra("startDate", 0);
                        long endDate = data.getLongExtra("endDate", 0);
                        String entryType = data.getStringExtra("entryType");
                        String paymentMode = data.getStringExtra("paymentMode");
                        ArrayList<String> categories = data.getStringArrayListExtra("categories");
                        String searchQuery = data.getStringExtra("searchQuery");

                        searchBinding.searchEditText.setText(searchQuery);

                        if (viewModel != null) {
                            viewModel.filter(searchQuery, startDate, endDate, entryType, categories,
                                    paymentMode != null ? new ArrayList<>(Collections.singletonList(paymentMode)) : null);
                        }
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
                            exportTransactionsToPdf(startDate, endDate, entryType, paymentMode);
                        } else {
                            requestPermissions();
                        }
                    }
                }
        );
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

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
                showSnackbar("Storage permission granted. Please tap Download again.");
            } else {
                showSnackbar("Storage permission denied. Cannot export file.");
            }
        }
    }

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
                // [FIX] Fallback for older APIs
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                values.put(MediaStore.Images.Media.DATA, path + "/" + "CashFlow_Report_" + System.currentTimeMillis() + ".pdf");
                uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            }

            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                Document document = new Document(PageSize.A4);
                PdfWriter.getInstance(document, outputStream);
                document.open();

                Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
                Paragraph title = new Paragraph("CashFlow Transaction Report", titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                document.add(title);
                document.add(new Paragraph(" "));

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                String dateRange = "Date Range: " + sdf.format(new Date(startDate)) +
                        " to " + sdf.format(new Date(endDate));
                document.add(new Paragraph(dateRange));
                document.add(new Paragraph(" "));

                // [FIX] Use the already filtered 'allTransactions' list
                List<TransactionModel> filteredTransactions = allTransactions.stream()
                        .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                        .filter(t -> entryType == null || entryType.equals("All") ||
                                t.getType().equals(entryType))
                        .filter(t -> paymentMode == null || paymentMode.equals("All") ||
                                t.getPaymentMode().equals(paymentMode))
                        .collect(Collectors.toList());

                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2, 2, 1, 2});

                Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
                table.addCell(new PdfPCell(new Phrase("Date", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Category", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Type", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Amount", headerFont)));

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

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // [FIX] ViewModel handles its own listeners
        // [FIX] But we still need to remove the cashbook badge listener
        if (cashbooksListener != null && currentUser != null) {
            mDatabase.child("users").child(currentUser.getUid())
                    .child("cashbooks")
                    .removeEventListener(cashbooksListener);
            cashbooksListener = null;
            Log.d(TAG, "Cashbooks listener removed");
        }
        Log.d(TAG, "TransactionActivity destroyed");
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK; // Fallback
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}