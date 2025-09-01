package com.example.cashflow;

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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.cashflow.databinding.ActivityCashInOutBinding;
import com.example.cashflow.utils.ErrorHandler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    // NEW: ViewBinding declaration
    private ActivityCashInOutBinding binding;

    private Calendar calendar;
    private String currentSelectedCategory = "Select Category";
    private String currentSelectedCategoryColor = "";
    private String currentCashbookId;
    private boolean isGuest;

    private CashInOutViewModel viewModel;
    private ActivityResultLauncher<Intent> chooseCategoryLauncher;
    private ActivityResultLauncher<Intent> pickContactLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NEW: Initialize ViewBinding
        binding = ActivityCashInOutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Get intent data
        isGuest = getIntent().getBooleanExtra("isGuest", true);
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        // Validate cashbook ID for logged-in users
        if (!isGuest && currentCashbookId == null) {
            Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Setup ViewModel
        CashInOutViewModelFactory factory = new CashInOutViewModelFactory(getApplication(), isGuest);
        viewModel = new ViewModelProvider(this, factory).get(CashInOutViewModel.class);

        initializeUI();
        setupLaunchers();
        setupClickListeners();
        setupInitialState();
    }

    private void initializeUI() {
        // Initialize color
        currentSelectedCategoryColor = String.format("#%06X",
                (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
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
                                // NEW: Using binding
                                binding.partyTextView.setText(cursor.getString(nameIndex));
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Failed to get contact name.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void setupClickListeners() {
        // NEW: All findViewById replaced with binding
        binding.backButton.setOnClickListener(v -> finish());
        binding.dateTextView.setOnClickListener(v -> showDatePickerDialog());
        binding.timeTextView.setOnClickListener(v -> showTimePickerDialog());
        binding.partyPickerIcon.setOnClickListener(v -> pickContact());
        binding.partyTextView.setOnClickListener(v -> pickContact());
        binding.selectedCategoryTextView.setOnClickListener(v -> openChooseCategoryActivity());
        binding.categoryColorIndicator.setOnClickListener(v -> openChooseCategoryActivity());
        binding.saveEntryButton.setOnClickListener(v -> saveTransactionEntry(true));
        binding.saveAndAddNewButton.setOnClickListener(v -> saveTransactionEntry(false));
    }

    private void setupInitialState() {
        String initialTransactionType = getIntent().getStringExtra("transaction_type");
        if ("OUT".equals(initialTransactionType)) {
            binding.radioOut.setChecked(true);
        } else {
            binding.radioIn.setChecked(true);
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
        intent.putExtra("isGuest", isGuest);
        chooseCategoryLauncher.launch(intent);
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        pickContactLauncher.launch(intent);
    }

    private void updateSelectedCategoryDisplay() {
        // NEW: Using binding
        binding.selectedCategoryTextView.setText(currentSelectedCategory);
        try {
            binding.categoryColorIndicator.setBackgroundColor(Color.parseColor(currentSelectedCategoryColor));
        } catch (Exception e) {
            binding.categoryColorIndicator.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.category_default));
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
        // NEW: Using binding
        binding.dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        // NEW: Using binding
        binding.timeTextView.setText(sdf.format(calendar.getTime()));
    }

    private void saveTransactionEntry(boolean shouldFinish) {
        // NEW: Enhanced validation using binding
        String amountStr = binding.amountEditText.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            binding.amountEditText.setError("Amount is required");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            binding.amountEditText.setError("Invalid amount format");
            return;
        }

        if (amount <= 0) {
            binding.amountEditText.setError("Amount must be greater than zero");
            return;
        }

        if ("Select Category".equals(currentSelectedCategory)) {
            Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create transaction
        TransactionModel newTransaction = new TransactionModel();
        newTransaction.setAmount(amount);
        newTransaction.setTimestamp(calendar.getTimeInMillis());

        // NEW: Using binding for form data
        newTransaction.setType(binding.inOutToggle.getCheckedRadioButtonId() == R.id.radioIn ? "IN" : "OUT");
        newTransaction.setPaymentMode(binding.cashOnlineToggle.getCheckedRadioButtonId() == R.id.radioCash ? "Cash" : "Online");
        newTransaction.setTransactionCategory(currentSelectedCategory);
        newTransaction.setPartyName(binding.partyTextView.getText().toString());
        newTransaction.setRemark(binding.remarkEditText.getText().toString().trim());

        // Save transaction
        viewModel.saveTransaction(currentCashbookId, newTransaction);
        Toast.makeText(this, "Transaction saved!", Toast.LENGTH_SHORT).show();

        if (shouldFinish) {
            finish();
        } else {
            clearForm();
        }
    }

    private void clearForm() {
        // NEW: Using binding for form clearing
        binding.amountEditText.setText("");
        binding.partyTextView.setText("Party (Customer/Supplier)");
        binding.remarkEditText.setText("");

        currentSelectedCategory = "Select Category";
        currentSelectedCategoryColor = String.format("#%06X",
                (0xFFFFFF & ContextCompat.getColor(this, R.color.category_default)));
        updateSelectedCategoryDisplay();

        binding.radioIn.setChecked(true);
        binding.cashOnlineToggle.check(R.id.radioCash);

        calendar = Calendar.getInstance();
        updateDateInView();
        updateTimeInView();

        binding.amountEditText.requestFocus();
    }

    // NEW: Critical memory leak prevention
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
