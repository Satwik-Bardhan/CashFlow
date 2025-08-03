package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";

    private TextView dateTextView;
    private EditText amountEditText, partyEditText, remarkEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private ImageView attachFileButton;
    private Spinner categorySpinner;
    private TextView selectedCategoryTextView;
    private View categoryColorIndicator;
    private Button saveEntryButton;
    private Button saveAndAddNewButton;

    private Calendar calendar;
    private String selectedTransactionType = "IN";
    private String selectedPaymentMode = "Cash";
    private String currentSelectedCategory = "Select Category";
    private String currentSelectedCategoryColor;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private LinearLayout btnTransactions;
    private LinearLayout btnHome;
    private LinearLayout btnSettings;

    private ImageView backButton;
    private String currentCashbookId; // New: To store the active cashbook ID

    private ActivityResultLauncher<Intent> chooseCategoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cash_in_out);

        currentSelectedCategoryColor = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));


        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root_layout), (v, insets) -> {
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

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Get the current cashbook ID from the Intent
        if (getIntent() != null) {
            currentCashbookId = getIntent().getStringExtra("cashbook_id");
            if (currentCashbookId == null) {
                Log.e(TAG, "No cashbook ID received. This activity may not function correctly.");
                Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
            }
        }


        dateTextView = findViewById(R.id.dateTextView);
        amountEditText = findViewById(R.id.amountEditText);
        partyEditText = findViewById(R.id.partyEditText);
        remarkEditText = findViewById(R.id.remarkEditText);
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        attachFileButton = findViewById(R.id.attachFileButton);
        categorySpinner = findViewById(R.id.categorySpinner);
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categoryColorIndicator = findViewById(R.id.categoryColorIndicator);
        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveAndAddNewButton = findViewById(R.id.saveAndAddNewButton);
        backButton = findViewById(R.id.backButton);

        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);
        btnSettings = findViewById(R.id.btnSettings);


        if (dateTextView == null || amountEditText == null || partyEditText == null || remarkEditText == null ||
                inOutToggle == null || radioIn == null || radioOut == null || cashOnlineToggle == null ||
                radioCash == null || radioOnline == null || attachFileButton == null || categorySpinner == null ||
                selectedCategoryTextView == null || categoryColorIndicator == null || saveEntryButton == null || saveAndAddNewButton == null ||
                backButton == null ||
                btnTransactions == null || btnHome == null || btnSettings == null) {
            Log.e(TAG, "One or more UI components not found in activity_cash_in_out.xml");
            Toast.makeText(this, "Error: Missing UI elements. Check layout IDs.", Toast.LENGTH_LONG).show();
            return;
        }

        chooseCategoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentSelectedCategory = result.getData().getStringExtra("selected_category_name");
                        currentSelectedCategoryColor = result.getData().getStringExtra("selected_category_color_hex");
                        updateSelectedCategoryDisplay();
                        Log.d(TAG, "Category selected from ChooseCategoryActivity: " + currentSelectedCategory + ", Color: " + currentSelectedCategoryColor);
                    } else {
                        Log.d(TAG, "Category selection cancelled or failed.");
                    }
                }
        );

        String initialTransactionType = getIntent().getStringExtra("transaction_type");
        if ("OUT".equals(initialTransactionType)) {
            radioOut.setChecked(true);
            selectedTransactionType = "OUT";
            amountEditText.setHintTextColor(ContextCompat.getColor(this, R.color.cash_out_hint_red));
        } else {
            radioIn.setChecked(true);
            selectedTransactionType = "IN";
            amountEditText.setHintTextColor(ContextCompat.getColor(this, R.color.cash_in_hint_green));
        }

        calendar = Calendar.getInstance();
        updateDateInView();
        dateTextView.setOnClickListener(v -> showDatePickerDialog());

        inOutToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioIn) {
                selectedTransactionType = "IN";
                amountEditText.setHintTextColor(ContextCompat.getColor(this, R.color.cash_in_hint_green));
            } else if (checkedId == R.id.radioOut) {
                selectedTransactionType = "OUT";
                amountEditText.setHintTextColor(ContextCompat.getColor(this, R.color.cash_out_hint_red));
            }
            Log.d(TAG, "Transaction Type: " + selectedTransactionType);
        });

        cashOnlineToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioCash) {
                selectedPaymentMode = "Cash";
            } else if (checkedId == R.id.radioOnline) {
                selectedPaymentMode = "Online";
            }
            Log.d(TAG, "Payment Mode: " + selectedPaymentMode);
        });
        if (radioCash.isChecked()) {
            selectedPaymentMode = "Cash";
        } else if (radioOnline.isChecked()) {
            selectedPaymentMode = "Online";
        }

        categorySpinner.setVisibility(View.GONE);
        updateSelectedCategoryDisplay();

        selectedCategoryTextView.setOnClickListener(v -> openChooseCategoryActivity());
        categoryColorIndicator.setOnClickListener(v -> openChooseCategoryActivity());

        attachFileButton.setOnClickListener(v -> {
            Toast.makeText(this, "Attach File functionality coming soon!", Toast.LENGTH_SHORT).show();
        });

        saveEntryButton.setOnClickListener(v -> saveTransactionEntry(true));
        saveAndAddNewButton.setOnClickListener(v -> saveTransactionEntry(false));

        backButton.setOnClickListener(v -> finish());

        btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(CashInOutActivity.this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            startActivity(intent);
            finish();
        });

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(CashInOutActivity.this, HomePage.class);
            startActivity(intent);
            finish();
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(CashInOutActivity.this, SettingsActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void openChooseCategoryActivity() {
        Intent intent = new Intent(CashInOutActivity.this, ChooseCategoryActivity.class);
        intent.putExtra("selected_category_name", currentSelectedCategory);
        intent.putExtra("selected_category_color_hex", currentSelectedCategoryColor);
        chooseCategoryLauncher.launch(intent);
    }

    @SuppressLint("SetTextI18n")
    private void updateSelectedCategoryDisplay() {
        selectedCategoryTextView.setText(currentSelectedCategory);
        try {
            categoryColorIndicator.setBackgroundColor(Color.parseColor(currentSelectedCategoryColor));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid color hex: " + currentSelectedCategoryColor + ", using default.", e);
            categoryColorIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.category_default));
        }
    }

    private void showDatePickerDialog() {
        new DatePickerDialog(this, (view, year, monthOfYear, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInView() {
        String myFormat = "MMM dd, yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.US);
        dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void saveTransactionEntry(boolean shouldGoHome) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to save transactions.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No active cashbook selected.", Toast.LENGTH_LONG).show();
            return;
        }

        String userId = currentUser.getUid();
        String amountStr = amountEditText.getText().toString().trim();
        String partyName = partyEditText.getText().toString().trim();
        String remark = remarkEditText.getText().toString().trim();
        String transactionCategory = currentSelectedCategory;
        String date = dateTextView.getText().toString();

        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount is required");
            amountEditText.requestFocus();
            return;
        }
        double amount = Double.parseDouble(amountStr);
        if (amount <= 0) {
            amountEditText.setError("Amount must be greater than zero");
            amountEditText.requestFocus();
            return;
        }

        long currentTimestamp = System.currentTimeMillis();

        TransactionModel newTransaction = new TransactionModel(
                null,
                transactionCategory,
                partyName,
                amount,
                date,
                selectedTransactionType,
                selectedPaymentMode,
                remark,
                currentTimestamp
        );

        // Updated path to save to the specific cashbook
        String transactionId = mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").push().getKey();
        if (transactionId != null) {
            newTransaction.setTransactionId(transactionId);
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").child(transactionId).setValue(newTransaction)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(CashInOutActivity.this, "Transaction saved successfully!", Toast.LENGTH_SHORT).show();
                        clearForm();
                        if (shouldGoHome) {
                            Intent intent = new Intent(CashInOutActivity.this, HomePage.class);
                            startActivity(intent);
                            finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error saving transaction: " + e.getMessage());
                        Toast.makeText(CashInOutActivity.this, "Failed to save transaction: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            Toast.makeText(this, "Could not generate transaction ID.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearForm() {
        amountEditText.setText("");
        partyEditText.setText("");
        remarkEditText.setText("");
        currentSelectedCategory = "Select Category";
        currentSelectedCategoryColor = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
        updateSelectedCategoryDisplay();

        radioIn.setChecked(true);
        radioCash.setChecked(true);
        calendar = Calendar.getInstance();
        updateDateInView();
    }
}