package com.example.cashflow;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditTransactionActivity extends AppCompatActivity {

    private static final String TAG = "EditTransactionActivity";

    // UI Components
    private TextView headerSubtitle, dateTextView, timeTextView, selectedCategoryTextView, partyTextView;
    private EditText amountEditText;
    private TextInputEditText remarkEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private Button saveButton, cancelButton;
    private ImageView backButton, menuButton, timePickerIcon;
    private LinearLayout categorySelectorLayout, dateSelectorLayout, timeSelectorLayout;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference transactionRef;

    // Data
    private TransactionModel currentTransaction;
    private String cashbookId;
    private Calendar calendar;

    // Activity Launcher for category selection
    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String selectedCategoryName = result.getData().getStringExtra("selected_category");
                    if (selectedCategoryName != null) {
                        currentTransaction.setTransactionCategory(selectedCategoryName);
                        updateCategoryUI();
                        Log.d(TAG, "Category updated to: " + selectedCategoryName);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        Intent intent = getIntent();
        currentTransaction = (TransactionModel) intent.getSerializableExtra("transaction_model");
        cashbookId = intent.getStringExtra("cashbook_id");

        if (currentUser == null || currentTransaction == null ||
                currentTransaction.getTransactionId() == null || cashbookId == null) {
            showSnackbar("Error: Invalid transaction data");
            Log.e(TAG, "Invalid transaction data provided");
            finish();
            return;
        }

        transactionRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid())
                .child("cashbooks")
                .child(cashbookId)
                .child("transactions")
                .child(currentTransaction.getTransactionId());

        initializeUI();
        populateData();
        setupClickListeners();

        Log.d(TAG, "EditTransactionActivity created for transaction: " + currentTransaction.getTransactionId());
    }

    private void initializeUI() {
        headerSubtitle = findViewById(R.id.headerSubtitle);

        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);

        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);

        amountEditText = findViewById(R.id.amountEditText);

        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);

        remarkEditText = findViewById(R.id.remarkEditText);

        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);
        partyTextView = findViewById(R.id.partyTextView);

        saveButton = findViewById(R.id.saveChangesButton);
        cancelButton = findViewById(R.id.CancelTransactionButton);
        backButton = findViewById(R.id.backButton);
        menuButton = findViewById(R.id.menuButton);
        timePickerIcon = findViewById(R.id.timePickerIcon);
        calendar = Calendar.getInstance();
    }

    private void populateData() {
        if (currentTransaction == null) return;

        calendar.setTimeInMillis(currentTransaction.getTimestamp());
        updateDateText();
        updateTimeText();

        SimpleDateFormat headerDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (headerSubtitle != null) {
            headerSubtitle.setText("Last modified: " + headerDateFormat.format(calendar.getTime()));
        }

        // [FIX] Added Null Check for amountEditText
        if (amountEditText != null) {
            amountEditText.setText(String.valueOf(currentTransaction.getAmount()));
        } else {
            Log.e(TAG, "amountEditText is null! Check layout IDs.");
        }

        if (remarkEditText != null) {
            remarkEditText.setText(currentTransaction.getRemark() != null ? currentTransaction.getRemark() : "");
        }

        if (partyTextView != null) {
            partyTextView.setText(currentTransaction.getPartyName() != null ?
                    currentTransaction.getPartyName() : "Select Party");
        }

        if ("IN".equalsIgnoreCase(currentTransaction.getType())) {
            if (radioIn != null) radioIn.setChecked(true);
        } else {
            if (radioOut != null) radioOut.setChecked(true);
        }

        if ("Cash".equalsIgnoreCase(currentTransaction.getPaymentMode())) {
            if (radioCash != null) radioCash.setChecked(true);
        } else {
            if (radioOnline != null) radioOnline.setChecked(true);
        }

        updateCategoryUI();
        Log.d(TAG, "Transaction data populated");
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        if (dateSelectorLayout != null) {
            dateSelectorLayout.setOnClickListener(v -> showDatePicker());
        } else if (dateTextView != null) {
            dateTextView.setOnClickListener(v -> showDatePicker());
        }

        if (timeSelectorLayout != null) {
            timeSelectorLayout.setOnClickListener(v -> showTimePicker());
        } else if (timeTextView != null) {
            timeTextView.setOnClickListener(v -> showTimePicker());
        }

        if (timePickerIcon != null) timePickerIcon.setOnClickListener(v -> showTimePicker());

        if (categorySelectorLayout != null) {
            categorySelectorLayout.setOnClickListener(v -> {
                Intent categoryIntent = new Intent(this, ChooseCategoryActivity.class);
                categoryIntent.putExtra("selected_category", currentTransaction.getTransactionCategory());
                categoryIntent.putExtra("cashbook_id", cashbookId);
                categoryLauncher.launch(categoryIntent);
            });
        }

        saveButton.setOnClickListener(v -> saveChanges());
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> showMoreOptionsMenu(v));
        }
    }

    private void showMoreOptionsMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.transaction_detail_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_share_transaction) {
                shareTransaction();
                return true;
            } else if (itemId == R.id.action_duplicate_transaction) {
                duplicateTransaction();
                return true;
            } else if (itemId == R.id.action_delete_transaction) {
                showDeleteConfirmationDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void shareTransaction() {
        try {
            String shareText = "Transaction Details:\n" +
                    "Amount: ₹" + amountEditText.getText().toString() + "\n" +
                    "Type: " + (radioIn.isChecked() ? "Income" : "Expense") + "\n" +
                    "Category: " + selectedCategoryTextView.getText().toString() + "\n" +
                    "Payment Mode: " + (radioCash.isChecked() ? "Cash" : "Online") + "\n" +
                    "Date: " + dateTextView.getText().toString() + "\n" +
                    "Remark: " + remarkEditText.getText().toString();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, "Share Transaction"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing transaction", e);
            showSnackbar("Error sharing transaction");
        }
    }

    private void updateCategoryUI() {
        String categoryName = currentTransaction.getTransactionCategory();
        if (selectedCategoryTextView != null) {
            selectedCategoryTextView.setText(categoryName != null ? categoryName : "Select Category");
            selectedCategoryTextView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary));
        }
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateText();
            Log.d(TAG, "Date updated");
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeText();
            Log.d(TAG, "Time updated");
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
        if (dateTextView != null) {
            dateTextView.setText(sdf.format(calendar.getTime()));
        }
    }

    private void updateTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        if (timeTextView != null) {
            timeTextView.setText(sdf.format(calendar.getTime()));
        }
    }

    private void saveChanges() {
        String amountStr = amountEditText.getText().toString().trim();
        if (amountStr.isEmpty()) {
            showSnackbar("Please enter an amount");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                showSnackbar("Amount must be greater than 0");
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("amount", amount);
            updates.put("remark", remarkEditText.getText().toString());
            updates.put("timestamp", calendar.getTimeInMillis());
            updates.put("type", radioIn.isChecked() ? "IN" : "OUT");
            updates.put("paymentMode", radioCash.isChecked() ? "Cash" : "Online");
            updates.put("transactionCategory", selectedCategoryTextView.getText().toString());
            updates.put("partyName", partyTextView.getText().toString());

            transactionRef.updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Transaction updated successfully");
                        showSnackbar("Transaction updated successfully");
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update transaction", e);
                        showSnackbar("Failed to update transaction");
                    });

        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid amount format", e);
            showSnackbar("Please enter a valid amount");
        }
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to permanently delete this transaction? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction() {
        transactionRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Transaction deleted successfully");
                    showSnackbar("Transaction deleted");
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete transaction", e);
                    showSnackbar("Failed to delete transaction");
                });
    }

    private void duplicateTransaction() {
        try {
            TransactionModel duplicatedTransaction = new TransactionModel();
            duplicatedTransaction.setAmount(Double.parseDouble(amountEditText.getText().toString()));
            duplicatedTransaction.setRemark(remarkEditText.getText().toString());
            duplicatedTransaction.setTimestamp(System.currentTimeMillis());
            duplicatedTransaction.setType(radioIn.isChecked() ? "IN" : "OUT");
            duplicatedTransaction.setPaymentMode(radioCash.isChecked() ? "Cash" : "Online");
            duplicatedTransaction.setTransactionCategory(selectedCategoryTextView.getText().toString());
            duplicatedTransaction.setPartyName(partyTextView.getText().toString());

            transactionRef.getParent().push().setValue(duplicatedTransaction)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Transaction duplicated successfully");
                        showSnackbar("Transaction duplicated successfully");
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to duplicate transaction", e);
                        showSnackbar("Failed to duplicate transaction");
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error duplicating transaction", e);
            showSnackbar("Error duplicating transaction");
        }
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "EditTransactionActivity destroyed");
    }
}