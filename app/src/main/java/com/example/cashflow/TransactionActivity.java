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

import com.example.cashflow.utils.CustomPieChartValueFormatter;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
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

    // Data
    private List<TransactionModel> allTransactions = new ArrayList<>();
    private Calendar currentMonthCalendar;

    // UI
    private PieChart pieChart;
    private TextView incomeText, expenseText, balanceText, monthTitleTextView, togglePieChartButton, categoriesCountTextView, highestCategoryTextView;
    private EditText searchEditText;
    private ImageView filterButton;
    private Button btnDownload;
    private LinearLayout pieChartHeader;
    private ImageButton monthBackwardButton, monthForwardButton;
    private TransactionItemFragment transactionFragment;

    // Navigation
    private LinearLayout btnHome, btnTransactions, btnSettings;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private String currentCashbookId;

    // Launchers
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
                        transaction.setTransactionId(snapshot.getKey());
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
        incomeText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        expenseText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
    }

    private void setupStyledPieChart(List<TransactionModel> transactionsForMonth) {
        // 1. Process Data
        Map<String, Float> expenseByCategory = new HashMap<>();
        float totalExpense = 0f;
        String highestCategory = "-";
        float maxExpense = 0f;

        for (TransactionModel transaction : transactionsForMonth) {
            if ("OUT".equalsIgnoreCase(transaction.getType())) {
                String category = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "Other";
                float amount = (float) transaction.getAmount();
                expenseByCategory.put(category, expenseByCategory.getOrDefault(category, 0f) + amount);
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

        // 2. Create entries
        ArrayList<PieEntry> entries = new ArrayList<>();

        // Define exact colors from your image
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#F2C94C")); // Yellow for Leisure
        colors.add(Color.parseColor("#2DD4BF")); // Teal for Health
        colors.add(Color.parseColor("#F87171")); // Coral for Food and Drinks
        colors.add(Color.parseColor("#A78BFA")); // Purple for Transportation
        colors.add(Color.parseColor("#34D399")); // Green
        colors.add(Color.parseColor("#60A5FA")); // Blue
        colors.add(Color.parseColor("#FBBF24")); // Amber
        colors.add(Color.parseColor("#F472B6")); // Pink

        for (Map.Entry<String, Float> entry : expenseByCategory.entrySet()) {
            float percentage = entry.getValue() / totalExpense * 100;
            entries.add(new PieEntry(percentage, entry.getKey()));
        }

        // 3. Create Data Set
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setColors(colors);

        // 4. Configure external labels with connector lines
        dataSet.setValueLinePart1OffsetPercentage(85f);
        dataSet.setValueLinePart1Length(0.25f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setValueLineColor(Color.parseColor("#828282"));
        dataSet.setValueLineWidth(1.5f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setDrawValues(true);

        // 5. Create PieData
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new CustomPieChartValueFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.WHITE);

        // 6. Configure Chart exactly like your image
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setRotationEnabled(true);
        pieChart.setDrawHoleEnabled(false); // Solid pie chart like your image
        pieChart.setDrawEntryLabels(false);
        pieChart.setExtraOffsets(25, 25, 25, 25); // Extra space for external labels
        pieChart.setBackgroundColor(Color.TRANSPARENT);

        // 7. Set data and animate
        pieChart.setData(data);
        pieChart.invalidate();
        pieChart.animateY(1200);
    }

    private void setupFilterLauncher() {
        // Simple search functionality
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
            Toast.makeText(this, "Advanced filters coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void applySearchFilter(String searchQuery) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthTitleTextView.setText(sdf.format(currentMonthCalendar.getTime()));

        List<TransactionModel> filteredTransactions = allTransactions.stream()
                .filter(t -> {
                    Calendar transactionCal = Calendar.getInstance();
                    transactionCal.setTimeInMillis(t.getTimestamp());
                    return transactionCal.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                            transactionCal.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH);
                })
                .filter(t -> searchQuery.isEmpty() ||
                        (t.getTransactionCategory() != null && t.getTransactionCategory().toLowerCase().contains(searchQuery.toLowerCase())))
                .collect(Collectors.toList());

        updateTotals(filteredTransactions);
        setupStyledPieChart(filteredTransactions);

        if (transactionFragment != null) {
            transactionFragment.updateTransactions(filteredTransactions);
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
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transactionsListener != null && mDatabase != null) {
            mDatabase.child("users")
                    .child(mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "")
                    .child("cashbooks")
                    .child(currentCashbookId != null ? currentCashbookId : "")
                    .child("transactions")
                    .removeEventListener(transactionsListener);
        }
    }

    private void exportTransactionsToPdf(long startDate, long endDate, String entryType, String paymentMode) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "CashFlow_Report_" + System.currentTimeMillis() + ".pdf");
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
                String dateRange = "Date Range: " + sdf.format(new Date(startDate)) + " to " + sdf.format(new Date(endDate));
                document.add(new Paragraph(dateRange));
                document.add(new Paragraph(" "));

                // Filter transactions
                List<TransactionModel> filteredTransactions = allTransactions.stream()
                        .filter(t -> t.getTimestamp() >= startDate && t.getTimestamp() <= endDate)
                        .filter(t -> entryType == null || entryType.equals("All") || t.getType().equals(entryType))
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
                    table.addCell(new PdfPCell(new Phrase(sdf.format(new Date(transaction.getTimestamp())), cellFont)));
                    table.addCell(new PdfPCell(new Phrase(transaction.getTransactionCategory(), cellFont)));
                    table.addCell(new PdfPCell(new Phrase(transaction.getType(), cellFont)));
                    table.addCell(new PdfPCell(new Phrase("₹" + String.format("%.2f", transaction.getAmount()), cellFont)));
                }

                document.add(table);
                document.close();
                outputStream.close();

                Toast.makeText(this, "PDF report exported successfully!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error exporting PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
