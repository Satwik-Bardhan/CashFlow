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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;

    // Data Lists
    private List<TransactionModel> allTransactions = new ArrayList<>();
    private Calendar currentMonthCalendar;

    // UI Components
    private PieChart pieChart;
    private TextView incomeText, expenseText, balanceText, monthTitleTextView, togglePieChartButton, categoriesCountTextView, highestCategoryTextView;
    private EditText searchEditText;
    private ImageView filterButton;
    private Button btnDownload;
    private LinearLayout pieChartHeader;
    private ImageButton monthBackwardButton, monthForwardButton;
    private TransactionItemFragment transactionFragment;

    // Bottom Navigation
    private LinearLayout btnHome, btnTransactions, btnSettings;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private String currentCashbookId;

    // Filter State
    private long startDateFilter = 0;
    private long endDateFilter = 0;
    private String entryTypeFilter = "All";
    private Set<String> categoryFilter = new HashSet<>();
    private Set<String> paymentModeFilter = new HashSet<>();


    // Activity Launchers
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
        currentMonthCalendar = Calendar.getInstance();

        initializeUI();
        setupTransactionFragment();
        setupClickListeners();
        setupFilterLauncher();
        setupDownloadLauncher();

        btnTransactions.setSelected(true);
    }

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
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupTransactionFragment() {
        transactionFragment = TransactionItemFragment.newInstance(new ArrayList<>());
        transactionFragment.setOnItemClickListener(this);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.transaction_fragment_container, transactionFragment)
                .commit();
    }

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
                togglePieChartButton.setText("Show Pie Chart");
            } else {
                pieChart.setVisibility(View.VISIBLE);
                togglePieChartButton.setText("Hide Pie Chart");
            }
        });

        btnDownload.setOnClickListener(v -> {
            Intent intent = new Intent(this, DownloadOptionsActivity.class);
            downloadLauncher.launch(intent);
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

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForTransactions(currentUser.getUid());
        }
    }

    private void startListeningForTransactions(String userId) {
        DatabaseReference transactionsRef = mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions");
        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null) {
                        allTransactions.add(transaction);
                    }
                }
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                displayDataForCurrentMonth();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(TransactionActivity.this, "Failed to load transactions.", Toast.LENGTH_SHORT).show();
            }
        });
    }

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
        setupPieChart(monthlyTransactions);

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
        incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
    }

    private void setupPieChart(List<TransactionModel> transactionsForMonth) {
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0;
        String highestCategory = "-";
        float maxExpense = 0;

        for (TransactionModel transaction : transactionsForMonth) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "Other";
                float amount = (float) transaction.getAmount();

                float currentCategoryTotal = expenseByCategory.getOrDefault(category, 0f) + amount;
                expenseByCategory.put(category, currentCategoryTotal);

                if (currentCategoryTotal > maxExpense) {
                    maxExpense = currentCategoryTotal;
                    highestCategory = category;
                }
                totalExpense += amount;
            }
        }

        categoriesCountTextView.setText(String.valueOf(expenseByCategory.size()));
        highestCategoryTextView.setText(highestCategory);

        if (totalExpense == 0) {
            pieChart.clear();
            pieChart.setCenterText("No Expenses\nThis Month");
            pieChart.setCenterTextColor(Color.WHITE);
            pieChart.invalidate();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        pieChart.setCenterText("Expenses\n" + String.format(Locale.US, "₹%.0f", totalExpense));
        pieChart.setCenterTextColor(Color.WHITE);

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setSliceSpace(3f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setValueLineColor(Color.GRAY);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));
        pieChart.setData(data);

        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.animateY(1000);
        pieChart.invalidate();
    }

    @Override
    public void onItemClick(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    // --- LAUNCHERS, PERMISSIONS, AND EXPORT LOGIC ---

    private void setupFilterLauncher() {
        filterLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Filter logic is now handled by the monthly navigator, but this can be used for a more advanced filter screen
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
            return true; // No specific write permission needed for MediaStore API on Q+
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
                Toast.makeText(this, "Permission granted. Please click Download again.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Storage permission is required to download files.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- NEW PDF EXPORT LOGIC ---

    private void exportTransactionsToPdf(long startDate, long endDate, String entryType, String paymentMode) {
        List<TransactionModel> transactionsToExport = allTransactions.stream()
                .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                .filter(t -> "All".equalsIgnoreCase(entryType) || ("Cash In".equalsIgnoreCase(entryType) && "IN".equalsIgnoreCase(t.getType())) || ("Cash Out".equalsIgnoreCase(entryType) && "OUT".equalsIgnoreCase(t.getType())))
                .filter(t -> "All".equalsIgnoreCase(paymentMode) || paymentMode.equalsIgnoreCase(t.getPaymentMode()))
                .collect(Collectors.toList());

        if (transactionsToExport.isEmpty()) {
            Toast.makeText(this, "No transactions found for the selected filters.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            savePdfFile(transactionsToExport);
        } catch (Exception e) {
            Toast.makeText(this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error creating PDF", e);
        }
    }

    private void savePdfFile(List<TransactionModel> transactions) throws Exception {
        String fileName = "Transactions_" + System.currentTimeMillis() + ".pdf";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

        if (uri == null) {
            throw new Exception("Failed to create new MediaStore record.");
        }

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Add Title
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("Transaction Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Create Table
            PdfPTable table = new PdfPTable(5); // 5 columns
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 2, 2, 3, 3});

            // Table Header
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
            String[] headers = {"Date", "Type", "Amount", "Category", "Party"};
            for(String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(8);
                table.addCell(cell);
            }

            // Table Body
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 9);

            for (TransactionModel t : transactions) {
                table.addCell(new PdfPCell(new Phrase(dateFormat.format(new Date(t.getTimestamp())), bodyFont)));
                table.addCell(new PdfPCell(new Phrase("IN".equals(t.getType()) ? "Income" : "Expense", bodyFont)));
                table.addCell(new PdfPCell(new Phrase(String.format(Locale.US, "%.2f", t.getAmount()), bodyFont)));
                table.addCell(new PdfPCell(new Phrase(t.getTransactionCategory() != null ? t.getTransactionCategory() : "", bodyFont)));
                table.addCell(new PdfPCell(new Phrase(t.getPartyName() != null ? t.getPartyName() : "", bodyFont)));
            }

            document.add(table);
            document.close();

            Toast.makeText(this, "PDF saved to Downloads folder.", Toast.LENGTH_LONG).show();
        }
    }
}
