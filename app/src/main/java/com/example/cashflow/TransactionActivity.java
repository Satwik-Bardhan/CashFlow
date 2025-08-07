package com.example.cashflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
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

// iText PDF Generation Imports
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";
    private static final int STORAGE_PERMISSION_CODE = 101;

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<TransactionModel> displayedTransactions = new ArrayList<>();
    private TransactionAdapter transactionAdapter;
    private RecyclerView transactionRecyclerView;
    private PieChart pieChart;

    private TextView incomeText, expenseText, balanceText, startDateTextView, endDateTextView;
    private EditText searchEditText;
    private LinearLayout filterOptionsLayout;
    private RadioGroup filterTypeToggle, filterModeToggle;
    private TextView filterCategoryTextView;
    private ImageView sortButton;
    private Button downloadReportButton;
    private LinearLayout btnHome, btnTransactions, btnSettings;

    private String currentFilterType = "All";
    private String currentFilterMode = "All";
    private String currentFilterCategory = "All Categories";
    private long startDateFilter = 0;
    private long endDateFilter = 0;
    private int currentSortOption = 0;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private String currentCashbookId;
    private String currentCashbookName = "Cashbook";

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
        sortButton = findViewById(R.id.sortButton);
        startDateTextView = findViewById(R.id.startDateTextView);
        endDateTextView = findViewById(R.id.endDateTextView);
        downloadReportButton = findViewById(R.id.downloadReportButton);
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
        sortButton.setOnClickListener(v -> showSortDialog());
        startDateTextView.setOnClickListener(v -> showDatePickerDialog(true));
        endDateTextView.setOnClickListener(v -> showDatePickerDialog(false));
        downloadReportButton.setOnClickListener(v -> checkPermissionAndCreatePdf());
        findViewById(R.id.toggleFilterButton).setOnClickListener(v -> toggleFilterOptionsVisibility());
        findViewById(R.id.clearAllFiltersButton).setOnClickListener(v -> clearAllFilters());
        findViewById(R.id.clearCategoryFilterButton).setOnClickListener(v -> clearCategoryFilter());
        filterCategoryTextView.setOnClickListener(v -> openChooseCategoryActivity());

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndSortTransactions();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        filterTypeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterInType) currentFilterType = "IN";
            else if (checkedId == R.id.filterOutType) currentFilterType = "OUT";
            else currentFilterType = "All";
            filterAndSortTransactions();
        });

        filterModeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterCashMode) currentFilterMode = "Cash";
            else if (checkedId == R.id.filterOnlineMode) currentFilterMode = "Online";
            else currentFilterMode = "All";
            filterAndSortTransactions();
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

    private void setupLaunchers() {
        chooseCategoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentFilterCategory = result.getData().getStringExtra("selected_category_name");
                        filterCategoryTextView.setText(currentFilterCategory);
                        filterAndSortTransactions();
                    }
                });
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForTransactions(currentUser.getUid());
            fetchCashbookName(currentUser.getUid());
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

    private void fetchCashbookName(String userId) {
        mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentCashbookName = snapshot.getValue(String.class);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
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
                filterAndSortTransactions();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(TransactionActivity.this, "Failed to load transactions.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionAndCreatePdf() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            } else {
                createPdf();
            }
        } else {
            createPdf();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createPdf();
            } else {
                Toast.makeText(this, "Storage permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createPdf() {
        if (displayedTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = currentCashbookName.replace(" ", "_") + "_" + new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US).format(new Date()) + ".pdf";
        File pdfFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BaseColor.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.GRAY);
            Font incomeFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new BaseColor(76, 175, 80)); // Green
            Font expenseFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new BaseColor(244, 67, 54)); // Red
            Font balanceFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new BaseColor(33, 150, 243)); // Blue

            Drawable d = ContextCompat.getDrawable(this, R.drawable.logo);
            if (d != null) {
                Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
                Image image = Image.getInstance(stream.toByteArray());
                image.scaleToFit(50, 50);
                image.setAlignment(Element.ALIGN_CENTER);
                document.add(image);
            }

            Paragraph title = new Paragraph("Cashbook Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            document.add(new Paragraph(currentCashbookName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            document.add(new Paragraph("Generated On: " + new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date())));
            document.add(new Paragraph(" "));

            PdfPTable summaryTable = new PdfPTable(3);
            summaryTable.setWidthPercentage(100);
            addCellToHeader(summaryTable, "Total Cash In");
            addCellToHeader(summaryTable, "Total Cash Out");
            addCellToHeader(summaryTable, "Final Balance");
            summaryTable.addCell(createCenteredCell(incomeText.getText().toString(), incomeFont));
            summaryTable.addCell(createCenteredCell(expenseText.getText().toString(), expenseFont));
            summaryTable.addCell(createCenteredCell(balanceText.getText().toString(), balanceFont));
            document.add(summaryTable);
            document.add(new Paragraph("Total No. of entries: " + displayedTransactions.size()));
            document.add(new Paragraph(" "));

            PdfPTable transactionTable = new PdfPTable(6);
            transactionTable.setWidthPercentage(100);
            transactionTable.setWidths(new float[]{2, 3, 2, 2, 2, 2});
            addCellToHeader(transactionTable, "Date");
            addCellToHeader(transactionTable, "Remark");
            addCellToHeader(transactionTable, "Mode");
            addCellToHeader(transactionTable, "Cash In");
            addCellToHeader(transactionTable, "Cash Out");
            addCellToHeader(transactionTable, "Balance");

            List<TransactionModel> chronologicalList = new ArrayList<>(displayedTransactions);
            Collections.reverse(chronologicalList);
            double runningBalance = 0;

            for (TransactionModel tx : chronologicalList) {
                if ("IN".equalsIgnoreCase(tx.getType())) {
                    runningBalance += tx.getAmount();
                } else {
                    runningBalance -= tx.getAmount();
                }

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy", Locale.US);
                transactionTable.addCell(createCenteredCell(sdf.format(new Date(tx.getTimestamp()))));
                transactionTable.addCell(new Phrase(tx.getRemark() != null ? tx.getRemark() : ""));
                transactionTable.addCell(createCenteredCell(tx.getPaymentMode()));

                if ("IN".equalsIgnoreCase(tx.getType())) {
                    transactionTable.addCell(createCenteredCell(String.format(Locale.US, "%.2f", tx.getAmount()), incomeFont));
                    transactionTable.addCell("");
                } else {
                    transactionTable.addCell("");
                    transactionTable.addCell(createCenteredCell(String.format(Locale.US, "%.2f", tx.getAmount()), expenseFont));
                }
                transactionTable.addCell(createCenteredCell(String.format(Locale.US, "%.2f", runningBalance)));
            }
            document.add(transactionTable);

            Paragraph footer = new Paragraph("Powered by Cash Flow App", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();
            Toast.makeText(this, "PDF saved to Downloads folder: " + fileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void addCellToHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.WHITE)));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(BaseColor.GRAY);
        cell.setPadding(8);
        table.addCell(cell);
    }

    // Helper method to create a center-aligned cell
    private PdfPCell createCenteredCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    // Overloaded helper for default font
    private PdfPCell createCenteredCell(String text) {
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.BLACK);
        return createCenteredCell(text, bodyFont);
    }

    private void filterAndSortTransactions() {
        List<TransactionModel> filteredList = new ArrayList<>();
        String query = searchEditText.getText().toString().toLowerCase(Locale.getDefault());

        for (TransactionModel transaction : allTransactions) {
            boolean matchesSearch = query.isEmpty() ||
                    (transaction.getTransactionCategory() != null && transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getPartyName() != null && transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(query)) ||
                    (transaction.getRemark() != null && transaction.getRemark().toLowerCase(Locale.getDefault()).contains(query));

            boolean matchesType = "All".equals(currentFilterType) || currentFilterType.equalsIgnoreCase(transaction.getType());
            boolean matchesMode = "All".equals(currentFilterMode) || currentFilterMode.equalsIgnoreCase(transaction.getPaymentMode());
            boolean matchesCategory = "All Categories".equals(currentFilterCategory) || currentFilterCategory.equals(transaction.getTransactionCategory());

            boolean matchesDate = (startDateFilter == 0 || transaction.getTimestamp() >= startDateFilter) &&
                    (endDateFilter == 0 || transaction.getTimestamp() <= endDateFilter);

            if (matchesSearch && matchesType && matchesMode && matchesCategory && matchesDate) {
                filteredList.add(transaction);
            }
        }

        switch (currentSortOption) {
            case 1: Collections.sort(filteredList, Comparator.comparingLong(TransactionModel::getTimestamp)); break;
            case 2: Collections.sort(filteredList, (t1, t2) -> Double.compare(t2.getAmount(), t1.getAmount())); break;
            case 3: Collections.sort(filteredList, Comparator.comparingDouble(TransactionModel::getAmount)); break;
            default: Collections.sort(filteredList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp())); break;
        }

        displayedTransactions.clear();
        displayedTransactions.addAll(filteredList);
        updateUI();
    }

    private void showSortDialog() {
        String[] sortOptions = {"Newest First", "Oldest First", "Amount: High to Low", "Amount: Low to High"};
        new AlertDialog.Builder(this)
                .setTitle("Sort By")
                .setSingleChoiceItems(sortOptions, currentSortOption, (dialog, which) -> {
                    currentSortOption = which;
                    filterAndSortTransactions();
                    dialog.dismiss();
                })
                .show();
    }

    private void showDatePickerDialog(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            if (isStartDate) {
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                startDateFilter = calendar.getTimeInMillis();
                startDateTextView.setText(sdf.format(calendar.getTime()));
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                endDateFilter = calendar.getTimeInMillis();
                endDateTextView.setText(sdf.format(calendar.getTime()));
            }
            filterAndSortTransactions();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
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

    private void clearAllFilters() {
        searchEditText.setText("");
        ((RadioButton)findViewById(R.id.filterAllType)).setChecked(true);
        ((RadioButton)findViewById(R.id.filterAllMode)).setChecked(true);
        currentFilterCategory = "All Categories";
        filterCategoryTextView.setText(currentFilterCategory);
        startDateFilter = 0;
        endDateFilter = 0;
        startDateTextView.setText("Start Date");
        endDateTextView.setText("End Date");
        currentSortOption = 0;
        filterAndSortTransactions();
        Toast.makeText(this, "All filters cleared.", Toast.LENGTH_SHORT).show();
    }

    private void openChooseCategoryActivity() {
        Intent intent = new Intent(this, ChooseCategoryActivity.class);
        intent.putExtra("selected_category_name", currentFilterCategory);
        chooseCategoryLauncher.launch(intent);
    }

    private void clearCategoryFilter() {
        currentFilterCategory = "All Categories";
        filterCategoryTextView.setText(currentFilterCategory);
        filterAndSortTransactions();
    }
}
