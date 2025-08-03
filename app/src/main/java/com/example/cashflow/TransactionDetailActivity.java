package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

public class TransactionDetailActivity extends AppCompatActivity {

    private static final String TAG = "TransDetailActivity";

    private TextView dateTextView;
    private EditText amountEditText, partyEditText, remarkEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private ImageView attachFileButton;
    private Spinner categorySpinner;
    private View categoryColorIndicator;
    private Button saveChangesButton, deleteEntryButton;

    private Calendar calendar;
    private String selectedTransactionType;
    private String selectedPaymentMode;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String transactionId;
    private TransactionModel currentTransaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transaction_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (getIntent() != null && getIntent().getExtras() != null) {
            currentTransaction = (TransactionModel) getIntent().getSerializableExtra("transaction_model");
            transactionId = getIntent().getStringExtra("transaction_id");

            if (currentTransaction == null || transactionId == null) {
                Log.e(TAG, "No transaction data or ID received. Finishing activity.");
                Toast.makeText(this, "Error: No transaction data to display.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } else {
            Log.e(TAG, "Intent is null or has no extras. Finishing activity.");
            Toast.makeText(this, "Error: No transaction data.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "onCreate: Received Transaction - ID: " + transactionId +
                ", Category: " + currentTransaction.getTransactionCategory() +
                ", Party: " + currentTransaction.getPartyName() +
                ", Type: " + currentTransaction.getType() +
                ", PaymentMode: " + currentTransaction.getPaymentMode() +
                ", Amount: " + currentTransaction.getAmount() +
                ", Remark: " + currentTransaction.getRemark() +
                ", Date: " + currentTransaction.getDate());


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
        categoryColorIndicator = findViewById(R.id.categoryColorIndicator);
        saveChangesButton = findViewById(R.id.saveChangesButton);
        deleteEntryButton = findViewById(R.id.deleteEntryButton);

        if (dateTextView == null || amountEditText == null || partyEditText == null || remarkEditText == null ||
                inOutToggle == null || radioIn == null || radioOut == null || cashOnlineToggle == null ||
                radioCash == null || radioOnline == null || attachFileButton == null || categorySpinner == null ||
                categoryColorIndicator == null || saveChangesButton == null || deleteEntryButton == null) {
            Log.e(TAG, "onCreate: One or more UI components not found in activity_transaction_detail.xml");
            Toast.makeText(this, "Error: Missing UI elements. Check layout IDs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.transaction_categories, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);

        populateFields(currentTransaction);

        dateTextView.setOnClickListener(v -> showDatePickerDialog());

        inOutToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioIn) {
                selectedTransactionType = "IN";
            } else if (checkedId == R.id.radioOut) {
                selectedTransactionType = "OUT";
            }
            Log.d(TAG, "Transaction Type changed to: " + selectedTransactionType);
        });

        cashOnlineToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioCash) {
                selectedPaymentMode = "Cash";
            } else if (checkedId == R.id.radioOnline) {
                selectedPaymentMode = "Online";
            }
            Log.d(TAG, "Payment Mode changed to: " + selectedPaymentMode);
        });

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = parent.getItemAtPosition(position).toString();
                int color = CategoryColorUtil.getCategoryColor(TransactionDetailActivity.this, selectedCategory);
                categoryColorIndicator.setBackgroundColor(color);
                Log.d(TAG, "Category selected: " + selectedCategory + ", Color updated.");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                categoryColorIndicator.setBackgroundColor(ContextCompat.getColor(TransactionDetailActivity.this, R.color.category_default));
            }
        });
        categoryColorIndicator.setBackgroundColor(CategoryColorUtil.getCategoryColor(this, categorySpinner.getSelectedItem().toString()));


        attachFileButton.setOnClickListener(v -> {
            Toast.makeText(this, "Attach File functionality coming soon!", Toast.LENGTH_SHORT).show();
        });

        saveChangesButton.setOnClickListener(v -> saveChanges());

        deleteEntryButton.setOnClickListener(v -> deleteTransaction());
    }

    @SuppressLint("SetTextI18n")
    private void populateFields(TransactionModel transaction) {
        Log.d(TAG, "populateFields: Populating UI with data. Transaction: " + transaction.toString());
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        calendar = Calendar.getInstance();
        try {
            calendar.setTime(sdf.parse(transaction.getDate()));
        } catch (java.text.ParseException e) {
            Log.e(TAG, "Error parsing transaction date from model: " + e.getMessage() + ". Using current date.", e);
            calendar = Calendar.getInstance();
        }
        updateDateInView();

        amountEditText.setText(String.valueOf(transaction.getAmount()));
        partyEditText.setText(transaction.getPartyName() != null ? transaction.getPartyName() : "");
        remarkEditText.setText(transaction.getRemark() != null ? transaction.getRemark() : "");

        if (transaction.getType().equalsIgnoreCase("IN")) {
            radioIn.setChecked(true);
        } else {
            radioOut.setChecked(true);
        }
        selectedTransactionType = transaction.getType();

        if (transaction.getPaymentMode() != null) {
            if (transaction.getPaymentMode().equalsIgnoreCase("Cash")) {
                radioCash.setChecked(true);
            } else if (transaction.getPaymentMode().equalsIgnoreCase("Online")) {
                radioOnline.setChecked(true);
            }
        } else {
            radioCash.setChecked(true);
        }
        selectedPaymentMode = (transaction.getPaymentMode() != null) ? transaction.getPaymentMode() : "Cash";


        ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) categorySpinner.getAdapter();
        if (adapter != null) {
            String categoryFromModel = transaction.getTransactionCategory() != null ? transaction.getTransactionCategory() : "";
            int spinnerPosition = adapter.getPosition(categoryFromModel);
            if (spinnerPosition >= 0) {
                categorySpinner.setSelection(spinnerPosition);
            } else {
                Log.w(TAG, "Category '" + categoryFromModel + "' not found in spinner adapter. Setting to default.");
                categorySpinner.setSelection(0);
            }
        }
        categoryColorIndicator.setBackgroundColor(CategoryColorUtil.getCategoryColor(this, categorySpinner.getSelectedItem().toString()));
        Log.d(TAG, "populateFields: UI populated.");
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

    private void saveChanges() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to save changes.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        String amountStr = amountEditText.getText().toString().trim();
        String partyName = partyEditText.getText().toString().trim();
        String remark = remarkEditText.getText().toString().trim();
        String transactionCategory = categorySpinner.getSelectedItem().toString();
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

        if (TextUtils.isEmpty(partyName)) {
            partyEditText.setError("Party/Customer/Supplier is required");
            partyEditText.requestFocus();
            return;
        }

        TransactionModel updatedTransaction = new TransactionModel(
                transactionId,
                transactionCategory,
                partyName,
                amount,
                date,
                selectedTransactionType,
                selectedPaymentMode,
                remark,
                currentTransaction.getTimestamp()
        );

        Log.d(TAG, "saveChanges: Saving updated transaction - ID: " + transactionId +
                ", Category: " + updatedTransaction.getTransactionCategory() +
                ", Party: " + updatedTransaction.getPartyName() +
                ", Type: " + updatedTransaction.getType() +
                ", PaymentMode: " + updatedTransaction.getPaymentMode() +
                ", Amount: " + updatedTransaction.getAmount() +
                ", Remark: " + updatedTransaction.getRemark());

        if (transactionId != null) {
            mDatabase.child("users").child(userId).child("transactions").child(transactionId).setValue(updatedTransaction)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(TransactionDetailActivity.this, "Changes saved successfully!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Changes saved to Firebase successfully.");
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error saving changes to Firebase: " + e.getMessage(), e);
                        Toast.makeText(TransactionDetailActivity.this, "Failed to save changes: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            Toast.makeText(this, "Error: No transaction ID to update.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "saveChanges: transactionId is null.");
        }
    }

    private void deleteTransaction() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to delete transactions.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();

        Log.d(TAG, "deleteTransaction: Attempting to delete transaction with ID: " + transactionId);

        if (transactionId != null) {
            mDatabase.child("users").child(userId).child("transactions").child(transactionId).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(TransactionDetailActivity.this, "Transaction deleted successfully!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Transaction deleted from Firebase successfully.");
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error deleting transaction from Firebase: " + e.getMessage());
                        Toast.makeText(TransactionDetailActivity.this, "Failed to delete transaction: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            Toast.makeText(this, "Error: No transaction ID to delete.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "deleteTransaction: transactionId is null.");
        }
    }
}