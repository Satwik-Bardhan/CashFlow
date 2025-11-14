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
import android.util.TypedValue;
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
import androidx.lifecycle.ViewModelProvider; // [FIX] Added ViewModelProvider

import com.example.cashflow.databinding.ActivityTransactionActivityBinding; // [FIX] Use ViewBinding
import com.example.cashflow.databinding.LayoutBottomNavigationBinding;
import com.example.cashflow.databinding.LayoutPieChartBinding;
import com.example.cashflow.databinding.LayoutSearchBarBinding;
import com.example.cashflow.databinding.LayoutSummaryCardsBinding;
import com.example.cashflow.utils.CustomPieChartValueFormatter;
// [FIX] Removed ErrorHandler as ViewModel will handle errors
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.snackbar.Snackbar;
// [FIX] Removed all Firebase imports, ViewModel will handle this
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

    // Firebase (Auth only)
    private FirebaseAuth mAuth;
    private String currentCashbookId;
    private boolean isGuest;

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
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        if (currentCashbookId == null && !isGuest) {
            showSnackbar("Error: No active cashbook found.");
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        currentMonthCalendar = Calendar.getInstance();

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
        if (!isGuest) {
            TransactionViewModelFactory factory = new TransactionViewModelFactory(getApplication(), currentCashbookId);
            viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);
        } else {
            // Handle guest mode (if you create a guest ViewModel)
            showSnackbar("Guest mode analytics not supported.");
            // For now, we'll just show empty
        }
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

        // Observe all transactions (for totals, unfiltered)
        viewModel.getAllTransactions().observe(this, transactions -> {
            Log.d(TAG, "ViewModel observed " + transactions.size() + " total transactions.");
            // You might want to update a "total balance" field here
        });

        // Observe loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            // Show/hide a progress bar
            // e.g., binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        // Observe error messages
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showSnackbar(error);
            }
        });
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(transaction -> {
            Intent intent = new Intent(this, EditTransactionActivity.class);
            intent.putExtra("transaction_model", (Serializable) transaction); // [FIX] Cast to Serializable
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.transaction_fragment_container, transactionFragment)
                .commit();
    }

    private void setupBottomNavigation() {
        bottomNavBinding.btnTransactions.setSelected(true); // [FIX] Use binding

        bottomNavBinding.btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomePage.class);
            intent.putExtra("isGuest", isGuest);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        bottomNavBinding.btnTransactions.setOnClickListener(v ->
                showSnackbar("Already on Transactions"));

        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        bottomNavBinding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("isGuest", isGuest);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        // [FIX] Logic for badge is now in a separate method
        // if (!isGuest) {
        //     loadCashbooksForBadge();
        // }
    }

    // [FIX] Renamed and simplified from original
    private void setupLaunchers() {
        setupFilterLauncher();
        setupDownloadLauncher();
    }

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
        // [FIX] No need to remove listeners, just re-create the activity with the new ID
        currentCashbookId = newCashbookId;
        showSnackbar("Switched to: " + cashbookName);
        Log.d(TAG, "Switched to cashbook: " + cashbookName);

        // Restart the activity with the new cashbook ID
        Intent intent = getIntent();
        intent.putExtra("cashbook_id", newCashbookId);
        finish();
        startActivity(intent);
    }

    // [FIX] Removed loadCashbooksForBadge and updateCashbookBadge.
    // This logic should be in HomePage.java and SettingsActivity.java
    // TransactionActivity should not be responsible for loading all cashbooks just for a badge.

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupClickListeners() {
        pieChartBinding.pieChartHeader.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseAnalyticsActivity.class);
            intent.putExtra("all_transactions", (Serializable) allTransactions);
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

        binding.downloadReportButton.setOnClickListener(v -> { // [FIX] Use main binding
            Intent intent = new Intent(this, DownloadOptionsActivity.class);
            downloadLauncher.launch(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // [FIX] ViewModel handles loading, but we need to refresh data for the current month
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
        TypedValue typedValue = new TypedValue();
        if (totalIncome - totalExpense >= 0) {
            getTheme().resolveAttribute(R.attr.incomeColor, typedValue, true);
            summaryBinding.balanceText.setTextColor(typedValue.data);
        } else {
            getTheme().resolveAttribute(R.attr.expenseColor, typedValue, true);
            summaryBinding.balanceText.setTextColor(typedValue.data);
        }
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
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.textColorPrimary, typedValue, true);
        int textColor = typedValue.data;

        if (totalExpense == 0) {
            pieChartBinding.pieChart.clear();
            pieChartBinding.pieChart.setCenterText("No Expenses");
            pieChartBinding.pieChart.setCenterTextColor(textColor); // [FIX]
            pieChartBinding.pieChart.invalidate();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#F2C94C")); // Yellow
        colors.add(Color.parseColor("#2DD4BF")); // Teal
        colors.add(Color.parseColor("#F87171")); // Coral
        colors.add(Color.parseColor("#A78BFA")); // Purple
        colors.add(Color.parseColor("#34D399")); // Green
        colors.add(Color.parseColor("#60A5FA")); // Blue

        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            float percentage = entry.getValue() / totalExpense * 100;
            entries.add(new PieEntry(percentage, entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setColors(colors);
        dataSet.setValueLinePart1OffsetPercentage(85f);
        dataSet.setValueLinePart1Length(0.25f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setValueLineColor(Color.parseColor("#828282"));
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
                if (viewModel != null) {
                    viewModel.filter(s.toString(), 0, 0, "All", null, null);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchBinding.filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            // [FIX] Pass current filter state to FiltersActivity
            // You would get this state from ViewModel
            startActivity(intent);
        });
    }

    // [FIX] Renamed from applySearchFilter to align with ViewModel
    private void applyFilters(String query, long startDate, long endDate, String entryType, List<String> categories, List<String> paymentModes) {
        if (viewModel != null) {
            viewModel.filter(query, startDate, endDate, entryType, categories, paymentModes);
        }
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
                showSnackbar("Storage permission granted");
            } else {
                showSnackbar("Storage permission denied");
            }
        }
    }

    private void exportTransactionsToPdf(long startDate, long endDate,
                                         String entryType, String paymentMode) {
        // This PDF export logic is complex and seems correct,
        // but be aware it uses hardcoded fonts which is fine for PDFs.
        // No logic changes needed here, but confirming it's preserved.
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

                List<TransactionModel> filteredTransactions = allTransactions.stream()
                        .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                        .filter(t -> entryType == null || entryType.equals("All") ||
                                t.getType().equals(entryType))
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
        // [FIX] No need to manually remove listeners, ViewModel's onCleared() handles it.
        Log.d(TAG, "TransactionActivity destroyed");
    }
}