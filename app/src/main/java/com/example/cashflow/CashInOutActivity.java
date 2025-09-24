package com.example.cashflow;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.cashflow.db.DataRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

/**
 * Enhanced CashInOutActivity with professional visual selection feedback
 * All buttons show filled color when selected, transparent with border when unselected
 */
public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Elements - Header
    private TextView headerTitle, headerSubtitle;
    private ImageView backButton, menuButton;

    // UI Elements - Date & Time
    private TextView dateTextView, timeTextView;
    private LinearLayout dateSelectorLayout, timeSelectorLayout;

    // UI Elements - Transaction Type
    private RadioGroup inOutToggle;
    private RadioButton radioIn, radioOut;
    private ImageView swapButton;

    // UI Elements - Payment Method
    private RadioGroup cashOnlineToggle;
    private RadioButton radioCash, radioOnline;
    private CheckBox taxCheckbox;
    private TextInputLayout taxAmountLayout;
    private TextInputEditText taxAmountEditText;

    // UI Elements - Amount
    private TextInputEditText amountEditText;
    private ImageView calculatorButton;
    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;

    // UI Elements - Remark & Voice
    private TextInputEditText remarkEditText;
    private ImageView voiceInputButton;

    // UI Elements - Category
    private TextView selectedCategoryTextView;

    // UI Elements - Attachments
    private ImageView cameraButton, scanButton, attachFileButton;

    // UI Elements - Party & Reference
    private TextView partyTextView;
    private LinearLayout partySelectorLayout;

    // UI Elements - Tags & Location
    private TextInputEditText tagsEditText;
    private ImageView locationButton;

    // UI Elements - Action Buttons
    private Button saveEntryButton, saveAndAddNewButton, clearButton;

    // Database
    private DataRepository dataRepository;
    private String currentCashbookId;
    private boolean isGuest;

    // State Variables
    private Calendar calendar;
    private String selectedCategory = null;
    private String selectedParty = null;
    private String currentLocation = null;

    // Location Services
    private FusedLocationProviderClient fusedLocationClient;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> voiceInputLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_in_out);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize Repository
        dataRepository = DataRepository.getInstance(getApplication());

        // Get intent extras
        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        isGuest = getIntent().getBooleanExtra("isGuest", false);
        String transactionType = getIntent().getStringExtra("transaction_type");

        if (!isGuest && currentCashbookId == null) {
            Toast.makeText(this, "Error: No Cashbook ID found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize location services
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        } catch (Exception e) {
            Log.w(TAG, "Location services not available", e);
        }

        // Initialize UI and functionality
        initializeUI();
        initializeDateTime();
        setupClickListeners();
        setupActivityLaunchers();
        setupInitialState(transactionType);

        Log.d(TAG, "CashInOutActivity initialized - Guest: " + isGuest);
    }

    private void initializeUI() {
        // Header Elements
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        backButton = findViewById(R.id.back_button);
        menuButton = findViewById(R.id.menu_button);

        // Date & Time
        dateTextView = findViewById(R.id.dateTextView);
        timeTextView = findViewById(R.id.timeTextView);
        dateSelectorLayout = findViewById(R.id.dateSelectorLayout);
        timeSelectorLayout = findViewById(R.id.timeSelectorLayout);

        // Transaction Type
        inOutToggle = findViewById(R.id.inOutToggle);
        radioIn = findViewById(R.id.radioIn);
        radioOut = findViewById(R.id.radioOut);
        swapButton = findViewById(R.id.swap_horiz);

        // Payment Method
        cashOnlineToggle = findViewById(R.id.cashOnlineToggle);
        radioCash = findViewById(R.id.radioCash);
        radioOnline = findViewById(R.id.radioOnline);
        taxCheckbox = findViewById(R.id.taxCheckbox);
        taxAmountLayout = findViewById(R.id.taxAmountLayout);
        taxAmountEditText = findViewById(R.id.taxAmountEditText);

        // Amount
        amountEditText = findViewById(R.id.amountEditText);
        calculatorButton = findViewById(R.id.calculatorButton);
        quickAmount100 = findViewById(R.id.quickAmount100);
        quickAmount500 = findViewById(R.id.quickAmount500);
        quickAmount1000 = findViewById(R.id.quickAmount1000);
        quickAmount5000 = findViewById(R.id.quickAmount5000);

        // Remark & Voice
        remarkEditText = findViewById(R.id.remarkEditText);
        voiceInputButton = findViewById(R.id.voiceInputButton);

        // Category
        selectedCategoryTextView = findViewById(R.id.selectedCategoryTextView);

        // Attachments
        cameraButton = findViewById(R.id.cameraButton);
        scanButton = findViewById(R.id.scanButton);
        attachFileButton = findViewById(R.id.attachFileButton);

        // Party & Reference
        partyTextView = findViewById(R.id.partyTextView);
        partySelectorLayout = findViewById(R.id.partySelectorLayout);

        // Tags & Location
        tagsEditText = findViewById(R.id.tagsEditText);
        locationButton = findViewById(R.id.locationButton);

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
        // Header buttons
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> showMenuOptions());
        }

        // Date and Time Pickers
        if (dateSelectorLayout != null) {
            dateSelectorLayout.setOnClickListener(v -> showDatePicker());
        }
        if (timeSelectorLayout != null) {
            timeSelectorLayout.setOnClickListener(v -> showTimePicker());
        }

        // Transaction Type
        if (swapButton != null) {
            swapButton.setOnClickListener(v -> swapTransactionType());
        }
        if (inOutToggle != null) {
            inOutToggle.setOnCheckedChangeListener(this::onTransactionTypeChanged);
        }

        // Payment Method
        if (cashOnlineToggle != null) {
            cashOnlineToggle.setOnCheckedChangeListener(this::onPaymentMethodChanged);
        }

        // Amount & Calculator
        if (calculatorButton != null) {
            calculatorButton.setOnClickListener(v -> openCalculator());
        }

        // Enhanced Quick Amount Buttons with visual selection
        setupQuickAmountButtons();

        // Tax Checkbox
        if (taxCheckbox != null) {
            taxCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (taxAmountLayout != null) {
                    taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            });
        }

        // Voice Input
        if (voiceInputButton != null) {
            voiceInputButton.setOnClickListener(v -> startVoiceInput());
        }

        // Category Selector
        if (selectedCategoryTextView != null) {
            selectedCategoryTextView.setOnClickListener(v -> openCategorySelector());
        }

        // Attachments
        if (cameraButton != null) {
            cameraButton.setOnClickListener(v -> openCamera());
        }
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> openScanner());
        }
        if (attachFileButton != null) {
            attachFileButton.setOnClickListener(v -> openFilePicker());
        }

        // Party Selector
        if (partySelectorLayout != null) {
            partySelectorLayout.setOnClickListener(v -> openPartySelector());
        }

        // Location
        if (locationButton != null) {
            locationButton.setOnClickListener(v -> getCurrentLocation());
        }

        // Action Buttons
        if (saveEntryButton != null) {
            saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        }
        if (saveAndAddNewButton != null) {
            saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));
        }
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearForm());
        }
    }

    // ======== ENHANCED VISUAL FEEDBACK METHODS ========

    private void setupQuickAmountButtons() {
        // Enhanced quick amount setup with selection feedback
        View.OnClickListener quickAmountClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear all quick amount selections first
                clearQuickAmountSelections();

                // Set the clicked button as selected
                v.setSelected(true);

                // Set the amount
                Button clickedButton = (Button) v;
                String amountText = clickedButton.getText().toString();
                String cleanAmount = amountText.replace("₹", "").replace("K", "000");

                if (amountEditText != null) {
                    amountEditText.setText(cleanAmount);
                }

                // Show visual feedback
                showQuickAmountSelectionFeedback(clickedButton);
            }
        };

        if (quickAmount100 != null) {
            quickAmount100.setOnClickListener(quickAmountClickListener);
        }
        if (quickAmount500 != null) {
            quickAmount500.setOnClickListener(quickAmountClickListener);
        }
        if (quickAmount1000 != null) {
            quickAmount1000.setOnClickListener(quickAmountClickListener);
        }
        if (quickAmount5000 != null) {
            quickAmount5000.setOnClickListener(quickAmountClickListener);
        }
    }

    private void clearQuickAmountSelections() {
        if (quickAmount100 != null) quickAmount100.setSelected(false);
        if (quickAmount500 != null) quickAmount500.setSelected(false);
        if (quickAmount1000 != null) quickAmount1000.setSelected(false);
        if (quickAmount5000 != null) quickAmount5000.setSelected(false);
    }

    private void showQuickAmountSelectionFeedback(Button selectedButton) {
        // Add a subtle animation for the selected button
        selectedButton.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(150)
                .withEndAction(() -> {
                    selectedButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start();
                })
                .start();

        Toast.makeText(this, "Amount set: " + selectedButton.getText(), Toast.LENGTH_SHORT).show();
    }

    // Enhanced transaction type change handler
    private void onTransactionTypeChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.radioIn) {
            updateHeaderForTransactionType("IN");
            // Add visual feedback
            if (radioIn != null) {
                radioIn.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100)
                        .withEndAction(() -> radioIn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start());
            }
        } else if (checkedId == R.id.radioOut) {
            updateHeaderForTransactionType("OUT");
            // Add visual feedback
            if (radioOut != null) {
                radioOut.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100)
                        .withEndAction(() -> radioOut.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start());
            }
        }
    }

    // Enhanced payment method change handler
    private void onPaymentMethodChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.radioCash) {
            // Add visual feedback for cash selection
            if (radioCash != null) {
                radioCash.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100)
                        .withEndAction(() -> radioCash.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start());
            }
            Toast.makeText(this, "Cash payment selected", Toast.LENGTH_SHORT).show();
        } else if (checkedId == R.id.radioOnline) {
            // Add visual feedback for online selection
            if (radioOnline != null) {
                radioOnline.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100)
                        .withEndAction(() -> radioOnline.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start());
            }
            Toast.makeText(this, "Online payment selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupActivityLaunchers() {
        // Voice Input
        voiceInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty() && remarkEditText != null) {
                            String voiceText = results.get(0);
                            remarkEditText.setText(voiceText);
                            Toast.makeText(this, "Voice input captured", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Camera
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Photo captured", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupInitialState(String transactionType) {
        // Set transaction type from intent
        if ("OUT".equals(transactionType)) {
            if (radioOut != null) {
                radioOut.setChecked(true);
            }
            updateHeaderForTransactionType("OUT");
        } else {
            if (radioIn != null) {
                radioIn.setChecked(true);
            }
            updateHeaderForTransactionType("IN");
        }

        // Set initial focus
        if (amountEditText != null) {
            amountEditText.requestFocus();
        }
    }

    // ======== DATE & TIME METHODS ========

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
                false
        );
        timePickerDialog.show();
    }

    private void updateDateText() {
        if (dateTextView != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            dateTextView.setText(dateFormat.format(calendar.getTime()));
        }
    }

    private void updateTimeText() {
        if (timeTextView != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            timeTextView.setText(timeFormat.format(calendar.getTime()));
        }
    }

    // ======== TRANSACTION TYPE METHODS ========

    private void swapTransactionType() {
        if (radioIn != null && radioOut != null) {
            if (radioIn.isChecked()) {
                radioOut.setChecked(true);
            } else {
                radioIn.setChecked(true);
            }
        }
    }

    private void updateHeaderForTransactionType(String type) {
        if (headerTitle != null && headerSubtitle != null) {
            if ("IN".equals(type)) {
                headerTitle.setText("Add Income");
                headerSubtitle.setText("Record money received");
            } else {
                headerTitle.setText("Add Expense");
                headerSubtitle.setText("Record money spent");
            }
        }
    }

    // ======== AMOUNT METHODS ========

    private void setQuickAmount(String amount) {
        if (amountEditText != null) {
            amountEditText.setText(amount);
        }
    }

    private void openCalculator() {
        try {
            Intent calculatorIntent = new Intent();
            calculatorIntent.setAction(Intent.ACTION_MAIN);
            calculatorIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            startActivity(calculatorIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Calculator not found", Toast.LENGTH_SHORT).show();
        }
    }

    // ======== VOICE INPUT METHODS ========

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your remark");

        try {
            voiceInputLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show();
        }
    }

    // ======== CATEGORY METHODS ========

    private void openCategorySelector() {
        String[] categories = getResources().getStringArray(R.array.transaction_categories);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Category")
                .setItems(categories, (dialog, which) -> {
                    if (which > 0) {
                        selectedCategory = categories[which];
                        if (selectedCategoryTextView != null) {
                            selectedCategoryTextView.setText(selectedCategory);
                        }
                    }
                })
                .show();
    }

    // ======== ATTACHMENT METHODS ========

    private void openCamera() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(cameraIntent);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openScanner() {
        Toast.makeText(this, "Scanner feature coming soon", Toast.LENGTH_SHORT).show();
    }

    private void openFilePicker() {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("*/*");
        try {
            startActivity(Intent.createChooser(fileIntent, "Select File"));
        } catch (Exception e) {
            Toast.makeText(this, "File picker not available", Toast.LENGTH_SHORT).show();
        }
    }

    // ======== PARTY METHODS ========

    private void openPartySelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint("Enter party name");

        builder.setTitle("Select Party")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String partyName = input.getText().toString().trim();
                    if (!partyName.isEmpty()) {
                        selectedParty = partyName;
                        if (partyTextView != null) {
                            partyTextView.setText(partyName);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ======== LOCATION METHODS ========

    private void getCurrentLocation() {
        if (fusedLocationClient == null) {
            Toast.makeText(this, "Location services not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = location.getLatitude() + ", " + location.getLongitude();
                        Toast.makeText(this, "Location captured", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(this, e ->
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ======== MENU METHODS ========

    private void showMenuOptions() {
        String[] options = {"Duplicate Entry", "Save as Template"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Menu Options")
                .setItems(options, (dialog, which) -> {
                    Toast.makeText(this, "Feature coming soon", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ======== SAVE METHODS ========

    private void saveTransaction(boolean addNew) {
        if (!validateForm()) {
            return;
        }

        TransactionModel transaction = createTransactionFromForm();

        if (isGuest) {
            saveGuestTransaction(transaction, addNew);
        } else {
            saveFirebaseTransaction(transaction, addNew);
        }
    }

    private boolean validateForm() {
        if (amountEditText == null) return false;

        String amountStr = amountEditText.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            amountEditText.setError("Amount cannot be empty");
            amountEditText.requestFocus();
            return false;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                amountEditText.setError("Amount must be greater than zero");
                amountEditText.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid amount");
            amountEditText.requestFocus();
            return false;
        }

        if (selectedCategory == null || "Select Category".equals(selectedCategory)) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private TransactionModel createTransactionFromForm() {
        TransactionModel transaction = new TransactionModel();

        if (amountEditText != null) {
            transaction.setAmount(Double.parseDouble(amountEditText.getText().toString().trim()));
        }

        transaction.setType((radioIn != null && radioIn.isChecked()) ? "IN" : "OUT");
        transaction.setPaymentMode((radioCash != null && radioCash.isChecked()) ? "Cash" : "Online");
        transaction.setTransactionCategory(selectedCategory);
        transaction.setTimestamp(calendar.getTimeInMillis());

        if (remarkEditText != null) {
            String remark = remarkEditText.getText().toString().trim();
            if (!remark.isEmpty()) {
                transaction.setRemark(remark);
            }
        }

        if (selectedParty != null) {
            transaction.setPartyName(selectedParty);
        }

        if (isGuest) {
            transaction.setTransactionId(UUID.randomUUID().toString());
        }

        return transaction;
    }

    private void saveGuestTransaction(TransactionModel transaction, boolean addNew) {
        dataRepository.addGuestTransaction(transaction, success -> {
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "Entry Saved", Toast.LENGTH_SHORT).show();
                    if (addNew) {
                        clearForm();
                    } else {
                        finish();
                    }
                } else {
                    Toast.makeText(this, "Failed to save entry", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveFirebaseTransaction(TransactionModel transaction, boolean addNew) {
        dataRepository.addFirebaseTransaction(currentCashbookId, transaction, success -> {
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "Entry Saved", Toast.LENGTH_SHORT).show();
                    if (addNew) {
                        clearForm();
                    } else {
                        finish();
                    }
                } else {
                    Toast.makeText(this, "Failed to save entry", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Enhanced clear form to reset selections
    private void clearForm() {
        if (amountEditText != null) {
            amountEditText.setText("");
        }
        if (remarkEditText != null) {
            remarkEditText.setText("");
        }
        if (selectedCategoryTextView != null) {
            selectedCategoryTextView.setText("Select Category");
        }
        if (partyTextView != null) {
            partyTextView.setText("Select Party (Customer/Supplier)");
        }

        selectedCategory = null;
        selectedParty = null;
        currentLocation = null;

        // Clear quick amount selections
        clearQuickAmountSelections();

        // Reset radio buttons to default
        if (radioIn != null) {
            radioIn.setChecked(true);
        }
        if (radioCash != null) {
            radioCash.setChecked(true);
        }
        if (taxCheckbox != null) {
            taxCheckbox.setChecked(false);
        }
        if (taxAmountLayout != null) {
            taxAmountLayout.setVisibility(View.GONE);
        }

        initializeDateTime();

        if (amountEditText != null) {
            amountEditText.requestFocus();
        }

        Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show();
    }
}
