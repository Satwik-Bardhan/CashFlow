package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailActivity extends AppCompatActivity {

    private static final String TAG = "TransDetailActivity";

    private TextView dateTextView, timeTextView, partyTextView;
    private EditText amountEditText, remarkEditText;
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private Spinner categorySpinner;
    private View categoryColorIndicator;

    private Calendar calendar;
    private String currentCashbookId;
    private String transactionId;
    private TransactionModel currentTransaction;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ActivityResultLauncher<Intent> pickContactLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (!unpackIntent()) {
            Toast.makeText(this, "Error: No transaction data.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeUI();
        setupLaunchers();
        setupClickListeners();
        populateFields();
    }

    private boolean unpackIntent() {
        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra("transaction_model")) return false;

        // Correction: Use the modern, type-safe method to get Serializable extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentTransaction = intent.getSerializableExtra("transaction_model", TransactionModel.class);
        } else {
            // Suppress the warning for older Android versions where this is the only way.
            currentTransaction = (TransactionModel) intent.getSerializableExtra("transaction_model");
        }

        if (currentTransaction != null) {
            transactionId = currentTransaction.getTransactionId();
        }
        currentCashbookId = intent.getStringExtra("cashbook_id");

        return currentTransaction != null && transactionId != null && currentCashbookId != null;
    }

    private void initializeUI() {
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        amountEditText = findViewById(R.id.amountEditText);
        partyTextView = findViewById(R.id.partyTextView);
        remarkEditText = findViewById(R.id.remarkEditText);
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        categorySpinner = findViewById(R.id.categorySpinner);
        categoryColorIndicator = findViewById(R.id.categoryColorIndicator);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.transaction_categories, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);
    }

    private void setupLaunchers() {
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
        findViewById(R.id.saveChangesButton).setOnClickListener(v -> saveChanges());
        findViewById(R.id.deleteEntryButton).setOnClickListener(v -> showDeleteConfirmationDialog());

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = parent.getItemAtPosition(position).toString();
                int color = CategoryColorUtil.getCategoryColor(TransactionDetailActivity.this, selectedCategory);
                categoryColorIndicator.setBackgroundColor(color);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void populateFields() {
        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTransaction.getTimestamp());
        updateDateInView();
        updateTimeInView();

        amountEditText.setText(String.valueOf(currentTransaction.getAmount()));
        partyTextView.setText(currentTransaction.getPartyName());
        remarkEditText.setText(currentTransaction.getRemark());

        if ("IN".equalsIgnoreCase(currentTransaction.getType())) radioIn.setChecked(true);
        else radioOut.setChecked(true);

        if ("Cash".equalsIgnoreCase(currentTransaction.getPaymentMode())) radioCash.setChecked(true);
        else radioOnline.setChecked(true);

        ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) categorySpinner.getAdapter();
        if (adapter != null) {
            int position = adapter.getPosition(currentTransaction.getTransactionCategory());
            categorySpinner.setSelection(position >= 0 ? position : 0);
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

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        pickContactLauncher.launch(intent);
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        dateTextView.setText(sdf.format(calendar.getTime()));
    }

    private void updateTimeInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        timeTextView.setText(sdf.format(calendar.getTime()));
    }

    private void saveChanges() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String amountStr = amountEditText.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr) || Double.parseDouble(amountStr) <= 0) {
            amountEditText.setError("Amount must be greater than zero");
            return;
        }

        TransactionModel updatedTransaction = new TransactionModel();
        updatedTransaction.setTransactionId(transactionId);
        updatedTransaction.setAmount(Double.parseDouble(amountStr));
        updatedTransaction.setTimestamp(calendar.getTimeInMillis());
        updatedTransaction.setType(inOutToggle.getCheckedRadioButtonId() == R.id.radioIn ? "IN" : "OUT");
        updatedTransaction.setPaymentMode(cashOnlineToggle.getCheckedRadioButtonId() == R.id.radioCash ? "Cash" : "Online");
        updatedTransaction.setTransactionCategory(categorySpinner.getSelectedItem().toString());
        updatedTransaction.setPartyName(partyTextView.getText().toString());
        updatedTransaction.setRemark(remarkEditText.getText().toString().trim());

        mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId)
                .child("transactions").child(transactionId).setValue(updatedTransaction)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save changes.", Toast.LENGTH_LONG).show());
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId)
                .child("transactions").child(transactionId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Transaction deleted!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete transaction.", Toast.LENGTH_LONG).show());
    }
}
