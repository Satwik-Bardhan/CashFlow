package com.example.cashflow;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
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
import androidx.lifecycle.ViewModelProvider; // [FIX] Added ViewModelProvider

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
// [FIX] Removed Firebase imports, ViewModel will handle this
// import com.google.firebase.auth.FirebaseAuth;
// import com.google.firebase.auth.FirebaseUser;
// import com.google.firebase.database.DatabaseReference;
// import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class CashInOutActivity extends AppCompatActivity {

    private static final String TAG = "CashInOutActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Elements
    private TextView headerTitle, headerSubtitle;
    private ImageView backButton, menuButton;
    private TextView dateTextView, timeTextView, selectedCategoryTextView;
    private LinearLayout dateSelectorLayout, timeSelectorLayout, categorySelectorLayout; // [FIX] Added categorySelectorLayout
    private RadioGroup inOutToggle, cashOnlineToggle;
    private RadioButton radioIn, radioOut, radioCash, radioOnline;
    private ImageView swapButton;
    private CheckBox taxCheckbox;
    private TextInputLayout taxAmountLayout;
    private TextInputEditText taxAmountEditText, remarkEditText, tagsEditText;
    private EditText amountEditText; // [FIX] This is an EditText in your layout, not TextInputEditText
    private ImageView calculatorButton, voiceInputButton, locationButton;
    private Button quickAmount100, quickAmount500, quickAmount1000, quickAmount5000;
    private ImageView cameraButton, scanButton, attachFileButton;
    private TextView partyTextView;
    private LinearLayout partySelectorLayout;
    private Button saveEntryButton, saveAndAddNewButton, clearButton;

    // [FIX] ViewModel
    private CashInOutViewModel viewModel;
    private String currentCashbookId;

    // State Variables
    private Calendar calendar;
    private String selectedCategory = "Other"; // [FIX] Default to "Other"
    private String selectedParty = null;
    private String currentLocation = null;

    // Location Services
    private FusedLocationProviderClient fusedLocationClient;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> voiceInputLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> categoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_in_out);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // [FIX] Initialize ViewModel
        // We removed Guest Mode, so we don't need the factory to pass 'isGuest'
        viewModel = new ViewModelProvider(this, new CashInOutViewModelFactory(getApplication()))
                .get(CashInOutViewModel.class);

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No Cashbook ID found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String transactionType = getIntent().getStringExtra("transaction_type");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializeUI();
        initializeDateTime();
        setupClickListeners();
        setupActivityLaunchers();
        setupInitialState(transactionType);

        Log.d(TAG, "CashInOutActivity initialized for cashbook: " + currentCashbookId);
    }

    private void initializeUI() {
        // Header
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
        categorySelectorLayout = findViewById(R.id.categorySelectorLayout); // [FIX] Find layout

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
        backButton.setOnClickListener(v -> finish());
        menuButton.setOnClickListener(v -> showMenuOptions());
        dateSelectorLayout.setOnClickListener(v -> showDatePicker());
        timeSelectorLayout.setOnClickListener(v -> showTimePicker());
        swapButton.setOnClickListener(v -> swapTransactionType());
        inOutToggle.setOnCheckedChangeListener(this::onTransactionTypeChanged);
        calculatorButton.setOnClickListener(v -> openCalculator());
        taxCheckbox.setOnCheckedChangeListener((bv, isChecked) -> {
            taxAmountLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        voiceInputButton.setOnClickListener(v -> startVoiceInput());
        categorySelectorLayout.setOnClickListener(v -> openCategorySelector()); // [FIX] Use layout
        cameraButton.setOnClickListener(v -> openCamera());
        scanButton.setOnClickListener(v -> openScanner());
        attachFileButton.setOnClickListener(v -> openFilePicker());
        partySelectorLayout.setOnClickListener(v -> openPartySelector());
        locationButton.setOnClickListener(v -> getCurrentLocation());
        saveEntryButton.setOnClickListener(v -> saveTransaction(false));
        saveAndAddNewButton.setOnClickListener(v -> saveTransaction(true));
        clearButton.setOnClickListener(v -> clearForm());

        setupQuickAmountButtons();
    }

    private void setupQuickAmountButtons() {
        View.OnClickListener quickAmountClickListener = v -> {
            clearQuickAmountSelections();
            v.setSelected(true);
            Button clickedButton = (Button) v;
            String amountText = clickedButton.getText().toString();
            String cleanAmount = amountText.replace("₹", "").replace("K", "000");
            amountEditText.setText(cleanAmount);
            showQuickAmountSelectionFeedback(clickedButton);
        };
        quickAmount100.setOnClickListener(quickAmountClickListener);
        quickAmount500.setOnClickListener(quickAmountClickListener);
        quickAmount1000.setOnClickListener(quickAmountClickListener);
        quickAmount5000.setOnClickListener(quickAmountClickListener);
    }

    private void clearQuickAmountSelections() {
        quickAmount100.setSelected(false);
        quickAmount500.setSelected(false);
        quickAmount1000.setSelected(false);
        quickAmount5000.setSelected(false);
    }

    private void showQuickAmountSelectionFeedback(Button selectedButton) {
        selectedButton.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                .withEndAction(() -> selectedButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start())
                .start();
    }

    private void onTransactionTypeChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.radioIn) {
            updateHeaderForTransactionType("IN");
        } else if (checkedId == R.id.radioOut) {
            updateHeaderForTransactionType("OUT");
        }
    }

    private void setupActivityLaunchers() {
        voiceInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty()) {
                            remarkEditText.setText(results.get(0));
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Photo captured", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Launcher for ChooseCategoryActivity
        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedCategory = result.getData().getStringExtra("selected_category");
                        if (selectedCategory != null) {
                            selectedCategoryTextView.setText(selectedCategory);
                            selectedCategoryTextView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary));
                        }
                    }
                }
        );
    }

    private void setupInitialState(String transactionType) {
        if ("OUT".equals(transactionType)) {
            radioOut.setChecked(true);
            updateHeaderForTransactionType("OUT");
        } else {
            radioIn.setChecked(true);
            updateHeaderForTransactionType("IN");
        }
        amountEditText.requestFocus();
        selectedCategoryTextView.setText(selectedCategory); // Set default "Other"
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateText();
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateTimeText();
                },
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);
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

    private void swapTransactionType() {
        if (radioIn.isChecked()) {
            radioOut.setChecked(true);
        } else {
            radioIn.setChecked(true);
        }
    }

    private void updateHeaderForTransactionType(String type) {
        if ("IN".equals(type)) {
            headerTitle.setText("Add Income");
            headerSubtitle.setText("Record money received");
        } else {
            headerTitle.setText("Add Expense");
            headerSubtitle.setText("Record money spent");
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

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your remark");
        try {
            voiceInputLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCategorySelector() {
        Intent intent = new Intent(this, ChooseCategoryActivity.class);
        intent.putExtra("selected_category", selectedCategory);
        categoryLauncher.launch(intent);
    }

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
                        partyTextView.setText(partyName);
                        partyTextView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorPrimary));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void getCurrentLocation() {
        if (fusedLocationClient == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showMenuOptions() {
        String[] options = {"Duplicate Entry", "Save as Template"};
        new AlertDialog.Builder(this)
                .setTitle("Menu Options")
                .setItems(options, (dialog, which) -> {
                    Toast.makeText(this, "Feature coming soon", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveTransaction(boolean addNew) {
        if (!validateForm()) {
            return;
        }

        TransactionModel transaction = createTransactionFromForm();

        // [FIX] Delegate saving to the ViewModel
        viewModel.saveTransaction(currentCashbookId, transaction);

        Toast.makeText(this, "Entry Saved", Toast.LENGTH_SHORT).show();
        if (addNew) {
            clearForm();
        } else {
            finish();
        }
    }

    private boolean validateForm() {
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

        transaction.setAmount(Double.parseDouble(amountEditText.getText().toString().trim()));
        transaction.setType(radioIn.isChecked() ? "IN" : "OUT");
        transaction.setPaymentMode(radioCash.isChecked() ? "Cash" : "Online");
        transaction.setTransactionCategory(selectedCategory);
        transaction.setTimestamp(calendar.getTimeInMillis());
        transaction.setRemark(remarkEditText.getText().toString().trim());
        if (selectedParty != null) transaction.setPartyName(selectedParty);
        // [FIX] Add tags and location if you implement them
        // transaction.setTags(tagsEditText.getText().toString().trim());
        // transaction.setLocation(currentLocation);

        return transaction;
    }

    // [FIX] Removed saveFirebaseTransaction, ViewModel now handles this

    private void clearForm() {
        amountEditText.setText("");
        remarkEditText.setText("");
        tagsEditText.setText("");
        selectedCategoryTextView.setText("Select Category");
        selectedCategoryTextView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorHint));
        partyTextView.setText("Select Party (Customer/Supplier)");
        partyTextView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorHint));

        selectedCategory = "Other";
        selectedParty = null;
        currentLocation = null;

        clearQuickAmountSelections();

        radioIn.setChecked(true);
        radioCash.setChecked(true);
        taxCheckbox.setChecked(false);
        taxAmountLayout.setVisibility(View.GONE);

        initializeDateTime();
        amountEditText.requestFocus();
        Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show();
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}