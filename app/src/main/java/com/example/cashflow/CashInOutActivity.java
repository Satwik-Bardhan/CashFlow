package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";

    private TextView dateTextView, timeTextView, partyTextView, selectedCategoryTextView;
    private EditText amountEditText, remarkEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut;
    private View categoryColorIndicator;

    private Calendar calendar;
    private String currentSelectedCategory = "Select Category";
    private String currentSelectedCategoryColor = "";
    private String currentCashbookId;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ActivityResultLauncher<Intent> chooseCategoryLauncher;
    private ActivityResultLauncher<Intent> pickContactLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_in_out);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeUI();
        setupLaunchers();
        setupClickListeners();
        setupInitialState();
    }

    private void initializeUI() {
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        amountEditText = findViewById(R.id.amountEditText);
        remarkEditText = findViewById(R.id.remarkEditText);
        partyTextView = findViewById(R.id.partyTextView);
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categoryColorIndicator = findViewById(R.id.categoryColorIndicator);
        currentSelectedCategoryColor = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
    }

    private void setupLaunchers() {
        chooseCategoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentSelectedCategory = result.getData().getStringExtra("selected_category_name");
                        currentSelectedCategoryColor = result.getData().getStringExtra("selected_category_color_hex");
                        updateSelectedCategoryDisplay();
                    }
                });

        pickContactLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri contactUri = result.getData().getData();
                        String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
                        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                            if (cursor != null && cursor.moveToFirst()) {
                                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                                partyTextView.setText(cursor.getString(nameIndex));
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Failed to get contact name.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        dateTextView.setOnClickListener(v -> showDatePickerDialog());
        timeTextView.setOnClickListener(v -> showTimePickerDialog());
        findViewById(R.id.partyPickerIcon).setOnClickListener(v -> pickContact());
        partyTextView.setOnClickListener(v -> pickContact());
        selectedCategoryTextView.setOnClickListener(v -> openChooseCategoryActivity());
        categoryColorIndicator.setOnClickListener(v -> openChooseCategoryActivity());
        findViewById(R.id.saveEntryButton).setOnClickListener(v -> saveTransactionEntry(true));
        findViewById(R.id.saveAndAddNewButton).setOnClickListener(v -> saveTransactionEntry(false));
    }

    private void setupInitialState() {
        String initialTransactionType = getIntent().getStringExtra("transaction_type");
        if ("OUT".equals(initialTransactionType)) {
            radioOut.setChecked(true);
        } else {
            radioIn.setChecked(true);
        }

        calendar = Calendar.getInstance();
        updateDateInView();
        updateTimeInView();
        updateSelectedCategoryDisplay();
    }

    private void openChooseCategoryActivity() {
        Intent intent = new Intent(this, ChooseCategoryActivity.class);
        intent.putExtra("selected_category_name", currentSelectedCategory);
        intent.putExtra("selected_category_color_hex", currentSelectedCategoryColor);
        chooseCategoryLauncher.launch(intent);
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        pickContactLauncher.launch(intent);
    }

    private void updateSelectedCategoryDisplay() {
        selectedCategoryTextView.setText(currentSelectedCategory);
        try {
            categoryColorIndicator.setBackgroundColor(Color.parseColor(currentSelectedCategoryColor));
        } catch (Exception e) {
            categoryColorIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.category_default));
        }
    }

    private void showDatePickerDialog() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            updateDateInView();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePickerDialog() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeInView();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        timeTextView.setText(sdf.format(calendar.getTime()));
    }

    private void saveTransactionEntry(boolean shouldFinish) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to save transactions.", Toast.LENGTH_SHORT).show();
            return;
        }

        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr) || Double.parseDouble(amountStr) <= 0) {
            amountEditText.setError("Amount must be greater than zero");
            return;
        }
        if ("Select Category".equals(currentSelectedCategory)) {
            Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference transactionRef = mDatabase.child("users").child(currentUser.getUid())
                .child("cashbooks").child(currentCashbookId).child("transactions");
        String transactionId = transactionRef.push().getKey();

        TransactionModel newTransaction = new TransactionModel();
        newTransaction.setTransactionId(transactionId);
        newTransaction.setAmount(Double.parseDouble(amountStr));
        newTransaction.setTimestamp(calendar.getTimeInMillis());
        newTransaction.setType(inOutToggle.getCheckedRadioButtonId() == R.id.radioIn ? "IN" : "OUT");
        newTransaction.setPaymentMode(cashOnlineToggle.getCheckedRadioButtonId() == R.id.radioCash ? "Cash" : "Online");
        newTransaction.setTransactionCategory(currentSelectedCategory);
        newTransaction.setPartyName(partyTextView.getText().toString());
        newTransaction.setRemark(remarkEditText.getText().toString().trim());

        if (transactionId != null) {
            transactionRef.child(transactionId).setValue(newTransaction)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Transaction saved!", Toast.LENGTH_SHORT).show();
                        if (shouldFinish) {
                            finish();
                        } else {
                            clearForm();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to save transaction.", Toast.LENGTH_LONG).show());
        }
    }

    private void clearForm() {
        amountEditText.setText("");
        partyTextView.setText("Party (Customer/Supplier)");
        remarkEditText.setText("");
        currentSelectedCategory = "Select Category";
        currentSelectedCategoryColor = String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
        updateSelectedCategoryDisplay();
        radioIn.setChecked(true);
        ((RadioGroup)findViewById(R.id.cashOnlineToggle)).check(R.id.radioCash);
        calendar = Calendar.getInstance();
        updateDateInView();
        updateTimeInView();
        amountEditText.requestFocus();
    }
}
