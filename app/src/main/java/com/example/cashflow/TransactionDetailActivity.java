package com.example.cashflow;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cashflow.utils.CategoryColorUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TransactionDetailActivity extends AppCompatActivity {

    private TextView headerSubtitle, dateTextView, timeTextView, categoryTextView, partyTextView;
    private EditText amountEditText, remarkEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private Button deleteButton, duplicateButton, saveButton;
    private ImageView backButton, menuButton;
    private LinearLayout categorySelectorLayout;
    private View categoryColorIndicator;

    private FirebaseAuth mAuth;
    private DatabaseReference transactionRef;
    private TransactionModel currentTransaction;
    private String cashbookId;
    private Calendar calendar;

    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String selectedCategoryName = result.getData().getStringExtra("selected_category");
                    if (selectedCategoryName != null) {
                        currentTransaction.setTransactionCategory(selectedCategoryName);
                        updateCategoryUI();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        Intent intent = getIntent();
        currentTransaction = (TransactionModel) intent.getSerializableExtra("transaction_model");
        cashbookId = intent.getStringExtra("cashbook_id");

        if (currentUser == null || currentTransaction == null || cashbookId == null) {
            Toast.makeText(this, "Error: Missing data.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        transactionRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid()).child("cashbooks").child(cashbookId)
                .child("transactions").child(currentTransaction.getTransactionId());

        initializeUI();
        populateData();
        setupClickListeners();
    }

    private void initializeUI() {
        headerSubtitle = findViewById(R.id.headerSubtitle);
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
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout);
        categoryTextView = findViewById(R.id.categoryTextView);
        categoryColorIndicator = findViewById(R.id.categoryColorIndicator);
        partyTextView = findViewById(R.id.partyTextView);
        deleteButton = findViewById(R.id.deleteTransactionButton);
        duplicateButton = findViewById(R.id.duplicateTransactionButton);
        saveButton = findViewById(R.id.saveChangesButton);
        backButton = findViewById(R.id.backButton);
        menuButton = findViewById(R.id.menuButton);
        calendar = Calendar.getInstance();
    }

    private void populateData() {
        if (currentTransaction == null) return;

        calendar.setTimeInMillis(currentTransaction.getTimestamp());
        updateDateText();
        updateTimeText();

        headerSubtitle.setText("Last modified: " + new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(calendar.getTime()));
        amountEditText.setText(String.valueOf(currentTransaction.getAmount()));
        remarkEditText.setText(currentTransaction.getRemark());
        partyTextView.setText(currentTransaction.getPartyName() != null ? currentTransaction.getPartyName() : "Select Party");

        if ("IN".equalsIgnoreCase(currentTransaction.getType())) {
            radioIn.setChecked(true);
        } else {
            radioOut.setChecked(true);
        }

        if ("Cash".equalsIgnoreCase(currentTransaction.getPaymentMode())) {
            radioCash.setChecked(true);
        } else {
            radioOnline.setChecked(true);
        }

        updateCategoryUI();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        dateTextView.setOnClickListener(v -> showDatePicker());
        timeTextView.setOnClickListener(v -> showTimePicker());
        findViewById(R.id.timePickerIcon).setOnClickListener(v -> showTimePicker());

        categorySelectorLayout.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChooseCategoryActivity.class);
            intent.putExtra("selected_category", currentTransaction.getTransactionCategory());
            categoryLauncher.launch(intent);
        });

        saveButton.setOnClickListener(v -> saveChanges());
        deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog());
        duplicateButton.setOnClickListener(v -> duplicateTransaction());

        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenuInflater().inflate(R.menu.transaction_detail_menu, popup.getMenu()); // Assume you have this menu file
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_share_transaction) {
                    Toast.makeText(this, "Share functionality coming soon!", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void updateCategoryUI() {
        String categoryName = currentTransaction.getTransactionCategory();
        categoryTextView.setText(categoryName);
        int color = CategoryColorUtil.getCategoryColor(this, categoryName);
        if (categoryColorIndicator.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) categoryColorIndicator.getBackground().mutate()).setColor(color);
        }
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateText();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeText();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
        dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        timeTextView.setText(sdf.format(calendar.getTime()));
    }

    private void saveChanges() {
        // Validation
        String amountStr = amountEditText.getText().toString();
        if (amountStr.isEmpty() || Double.parseDouble(amountStr) <= 0) {
            Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Collect updated data
        Map<String, Object> updates = new HashMap<>();
        updates.put("amount", Double.parseDouble(amountStr));
        updates.put("remark", remarkEditText.getText().toString());
        updates.put("timestamp", calendar.getTimeInMillis());
        updates.put("type", radioIn.isChecked() ? "IN" : "OUT");
        updates.put("paymentMode", radioCash.isChecked() ? "Cash" : "Online");
        updates.put("transactionCategory", categoryTextView.getText().toString());
        updates.put("partyName", partyTextView.getText().toString());

        // Update Firebase
        transactionRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Transaction updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update transaction.", Toast.LENGTH_SHORT).show());
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to permanently delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction() {
        transactionRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete transaction.", Toast.LENGTH_SHORT).show());
    }

    private void duplicateTransaction() {
        // Create a new transaction object with the same data but a new timestamp and ID
        TransactionModel duplicatedTransaction = new TransactionModel();
        duplicatedTransaction.setAmount(Double.parseDouble(amountEditText.getText().toString()));
        duplicatedTransaction.setRemark(remarkEditText.getText().toString());
        duplicatedTransaction.setTimestamp(System.currentTimeMillis()); // Use current time for duplicate
        duplicatedTransaction.setType(radioIn.isChecked() ? "IN" : "OUT");
        duplicatedTransaction.setPaymentMode(radioCash.isChecked() ? "Cash" : "Online");
        duplicatedTransaction.setTransactionCategory(categoryTextView.getText().toString());
        duplicatedTransaction.setPartyName(partyTextView.getText().toString());

        // Push new transaction to Firebase
        transactionRef.getParent().push().setValue(duplicatedTransaction)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Transaction duplicated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to duplicate transaction.", Toast.LENGTH_SHORT).show());
    }
}
