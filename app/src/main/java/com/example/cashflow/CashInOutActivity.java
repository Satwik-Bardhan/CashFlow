package com.example.cashflow;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";

    // UI Elements
    private TextView dateTextView, timeTextView, selectedCategoryTextView;
    private EditText amountEditText, remarkEditText, taxAmountEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private CheckBox taxCheckbox;
    private Button saveEntryButton, saveAndAddNewButton, clearButton;
    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;
    private ImageView backButton;
    private LinearLayout dateSelectorLayout, timeSelectorLayout;
    private TextInputLayout taxAmountLayout;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String currentCashbookId;

    // State
    private Calendar calendar;
    private String selectedCategory = null;

    // Activity Result Launcher for category selection
    private ActivityResultLauncher<Intent> categoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_in_out);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No Cashbook ID found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeUI();
        initializeDateTime();
        setupClickListeners();
        setupCategoryLauncher();
    }

    private void initializeUI() {
        // Header
        backButton = findViewById(R.id.back_button);

        // Date & Time
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        // Transaction Type
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);

        // Amount
        amountEditText = findViewById(R.id.amountEditText);
        quickAmount100 = findViewById(R.id.quickAmount100);
        quickAmount500 = findViewById(R.id.quickAmount500);
        quickAmount1000 = findViewById(R.id.quickAmount1000);
        quickAmount5000 = findViewById(R.id.quickAmount5000);

        // Payment Method
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        // Remark
        remarkEditText = findViewById(R.id.remarkEditText);

        // Category
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);

        // Action Buttons
        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveAndAddNewButton = findViewById(R.id.saveAndAddNewButton);
        clearButton = findViewById(R.id.clearButton);
    }

    private void initializeDateTime() {
        calendar = Calendar.getInstance();
        updateDateText();
        updateTimeText();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        // Date and Time Pickers
        dateSelectorLayout.setOnClickListener(v -> showDatePicker());
        timeSelectorLayout.setOnClickListener(v -> showTimePicker());

        // Quick Amount Buttons
        quickAmount100.setOnClickListener(v -> amountEditText.setText("100"));
        quickAmount500.setOnClickListener(v -> amountEditText.setText("500"));
        quickAmount1000.setOnClickListener(v -> amountEditText.setText("1000"));
        quickAmount5000.setOnClickListener(v -> amountEditText.setText("5000"));

        // Tax Checkbox
        taxCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Category Selector
        selectedCategoryTextView.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChooseCategoryActivity.class);
            categoryLauncher.launch(intent);
        });

        // Save Buttons
        saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));

        // Clear Button
        clearButton.setOnClickListener(v -> clearForm());
    }

    private void setupCategoryLauncher() {
        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedCategory = result.getData().getStringExtra("selected_category");
                        if (selectedCategory != null) {
                            selectedCategoryTextView.setText(selectedCategory);
                        }
                    }
                }
        );
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false // 12-hour format
        );
        timePickerDialog.show();
    }

    private void updateDateText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        dateTextView.setText(dateFormat.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        timeTextView.setText(timeFormat.format(calendar.getTime()));
    }

    private void saveTransaction(boolean addNew) {
        String amountStr = amountEditText.getText().toString().trim();
        String remark = remarkEditText.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount cannot be empty");
            amountEditText.requestFocus();
            return;
        }

        if (selectedCategory == null) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(amountStr);
        if (amount <= 0) {
            amountEditText.setError("Amount must be greater than zero");
            amountEditText.requestFocus();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to save an entry.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gather Data
        String transactionType = radioIn.isChecked() ? "IN" : "OUT";
        String paymentMethod = radioCash.isChecked() ? "Cash" : "Online";
        long timestamp = calendar.getTimeInMillis();

        // Create Transaction Object
        TransactionModel transaction = new TransactionModel();
        transaction.setAmount(amount);
        transaction.setType(transactionType);
        transaction.setPaymentMode(paymentMethod);
        transaction.setTransactionCategory(selectedCategory);
        transaction.setRemark(remark);
        transaction.setTimestamp(timestamp);
        // You can set other fields like partyName, tags, etc. here if you implement them

        // Save to Firebase
        String userId = currentUser.getUid();
        mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions")
                .push() // Creates a unique key for the transaction
                .setValue(transaction)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Entry Saved", Toast.LENGTH_SHORT).show();
                        if (addNew) {
                            clearForm();
                        } else {
                            finish(); // Go back to the previous screen
                        }
                    } else {
                        Toast.makeText(this, "Failed to save entry. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void clearForm() {
        amountEditText.setText("");
        remarkEditText.setText("");
        taxAmountEditText.setText("");
        selectedCategoryTextView.setText("Select Category");
        selectedCategory = null;
        radioIn.setChecked(true);
        radioCash.setChecked(true);
        taxCheckbox.setChecked(false);
        initializeDateTime();
        amountEditText.requestFocus();
    }
}