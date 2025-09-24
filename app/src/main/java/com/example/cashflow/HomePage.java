package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cashflow.databinding.ActivityHomePageBinding;
import com.example.cashflow.databinding.ComponentBalanceCardBinding;
import com.example.cashflow.databinding.LayoutBottomNavigationBinding;
import com.example.cashflow.dialogs.CashbookSwitchDialog;
import com.example.cashflow.models.Cashbook;
import com.example.cashflow.utils.ErrorHandler;
import com.example.cashflow.utils.ThemeManager;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * HomePage serves as the main dashboard for the Cashflow application.
 * It displays user's financial summary, recent transactions, and provides
 * navigation to other app features. Supports both authenticated and guest modes.
 */
public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int MAX_VISIBLE_TRANSACTIONS = 5;

    // ViewBinding declarations
    private ActivityHomePageBinding binding;
    private ComponentBalanceCardBinding balanceCardBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    // UI Components for dropdown
    private TextView currentCashbookTextView;
    private View cashbookDropdownContainer;
    private ImageView dropdownArrow;

    // Firebase components
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // Firebase listeners
    private ValueEventListener transactionsListener, userProfileListener, cashbooksListener;

    // Data collections
    private ArrayList<TransactionModel> allTransactions = new ArrayList<>();
    private List<CashbookModel> cashbooks = new ArrayList<>();

    // State variables
    private String currentCashbookId;
    private boolean isGuest;
    private boolean isLoading = false;
    private AlertDialog cashbookDialog;

    // Number formatting
    private NumberFormat currencyFormat;

    // Balance tracking for animations
    private double currentBalance = 0.0;
    private double currentIncome = 0.0;
    private double currentExpense = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme is already set by MyApplication.java
        super.onCreate(savedInstanceState);

        // Initialize ViewBinding
        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize child bindings
        balanceCardBinding = binding.balanceCardView;
        bottomNavBinding = binding.bottomNavCard;

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize currency formatter
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Get guest mode status
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        Log.d(TAG, "HomePage started, guest mode: " + isGuest);

        setupUI();
        setupClickListeners();
        initDropdownViews();
        setupDropdownClick();

        // Set active bottom navigation
        bottomNavBinding.btnHome.setSelected(true);
    }

    /**
     * Initialize dropdown UI components
     */
    private void initDropdownViews() {
        // These should match your layout IDs
        currentCashbookTextView = findViewById(R.id.currentCashbookText);
        cashbookDropdownContainer = findViewById(R.id.cashbookDropdownContainer);
        dropdownArrow = findViewById(R.id.dropdownArrow);

        // If these views don't exist in your current layout, use userBox as fallback
        if (currentCashbookTextView == null) {
            currentCashbookTextView = findViewById(R.id.userNameTop);
        }
        if (cashbookDropdownContainer == null) {
            cashbookDropdownContainer = binding.userBox;
        }
    }

    /**
     * Setup dropdown click functionality
     */
    private void setupDropdownClick() {
        if (cashbookDropdownContainer != null) {
            cashbookDropdownContainer.setOnClickListener(v -> showCashbookDropdownMenu(v));
        }

        if (currentCashbookTextView != null) {
            currentCashbookTextView.setOnClickListener(v -> showCashbookDropdownMenu(v));
        }
    }

    /**
     * Shows the main dropdown menu (Switch Cashbook, Add New, Settings, Sign Out)
     */
    private void showCashbookDropdownMenu(View anchorView) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null && isGuest) {
            showGuestOptions();
            return;
        }

        try {
            PopupMenu popupMenu = new PopupMenu(this, anchorView, Gravity.END);
            popupMenu.getMenuInflater().inflate(R.menu.menu_cashbook_dropdown, popupMenu.getMenu());

            // Animate dropdown arrow if it exists
            if (dropdownArrow != null) {
                animateDropdownArrow(true);
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_switch_cashbook) {
                    openSwitchCashbookDialog();
                    return true;
                } else if (itemId == R.id.action_add_new_cashbook) {
                    showCreateNewCashbookDialog();
                    return true;
                } else if (itemId == R.id.action_settings) {
                    navigateToSettings();
                    return true;
                } else if (itemId == R.id.action_sign_out) {
                    signOutUser();
                    return true;
                }
                return false;
            });

            popupMenu.setOnDismissListener(menu -> {
                // Reset dropdown arrow when menu is dismissed
                if (dropdownArrow != null) {
                    animateDropdownArrow(false);
                }
            });

            popupMenu.show();
            Log.d(TAG, "Cashbook dropdown menu shown");

        } catch (Exception e) {
            Log.e(TAG, "Error showing dropdown menu", e);
            showSnackbar("Error showing menu options");
        }
    }

    /**
     * Animates the dropdown arrow
     */
    private void animateDropdownArrow(boolean isOpen) {
        if (dropdownArrow == null) return;

        float rotation = isOpen ? 180f : 0f;
        dropdownArrow.animate()
                .rotation(rotation)
                .setDuration(200)
                .start();
    }

    /**
     * Opens the comprehensive Switch Cashbook dialog
     */
    private void openSwitchCashbookDialog() {
        CashbookSwitchDialog dialog = new CashbookSwitchDialog();
        dialog.setCashbookSwitchListener(new CashbookSwitchDialog.CashbookSwitchListener() {
            @Override
            public void onCashbookSwitched(Cashbook cashbook) {
                // Convert CashbookModel to match your existing code
                CashbookModel cashbookModel = new CashbookModel(cashbook.getId(), cashbook.getName());
                updateCurrentCashbook(cashbookModel);
                switchCashbook(cashbook.getId());
                refreshDashboard();
            }

            @Override
            public void onCashbookAdded(Cashbook cashbook) {
                // Optionally switch to the new cashbook
                CashbookModel cashbookModel = new CashbookModel(cashbook.getId(), cashbook.getName());
                updateCurrentCashbook(cashbookModel);
                switchCashbook(cashbook.getId());
                refreshDashboard();
            }
        });
        dialog.show(getSupportFragmentManager(), "CashbookSwitchDialog");
    }

    /**
     * Updates the current cashbook display
     */
    private void updateCurrentCashbook(CashbookModel cashbook) {
        if (currentCashbookTextView != null) {
            currentCashbookTextView.setText(cashbook.getName());
        }
        if (binding.userNameTop != null) {
            binding.userNameTop.setText(cashbook.getName());
        }
    }

    /**
     * Refreshes the dashboard data
     */
    private void refreshDashboard() {
        // Refresh your dashboard data for the new cashbook
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentCashbookId != null) {
            startListeningForTransactions(currentUser.getUid());
        }
    }

    /**
     * Sets up the user interface based on authentication status
     */
    private void setupUI() {
        setLoadingState(false);

        if (isGuest) {
            handleGuestMode();
        }

        // Add accessibility content descriptions
        binding.cashInButton.setContentDescription("Add cash in transaction");
        binding.cashOutButton.setContentDescription("Add cash out transaction");
        binding.userBox.setContentDescription("User information and cashbook selector");
        binding.viewFullTransactionsButton.setContentDescription("View all transactions");
    }

    /**
     * Sets up click listeners for all interactive elements
     */
    private void setupClickListeners() {
        // Main activity buttons
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        binding.viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());

        // Keep existing userBox functionality as fallback
        binding.userBox.setOnClickListener(v -> showCashbookDropdownMenu(v));

        // Bottom navigation
        bottomNavBinding.btnHome.setOnClickListener(v ->
                showSnackbar("Already on Home"));
        bottomNavBinding.btnTransactions.setOnClickListener(v -> navigateToTransactionList());
        bottomNavBinding.btnSettings.setOnClickListener(v -> navigateToSettings());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && !isGuest) {
            Log.d(TAG, "Authenticated user session started");
            loadActiveCashbookId(currentUser.getUid());
        } else {
            Log.d(TAG, "Guest mode session started");
            handleGuestMode();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
        Log.d(TAG, "Activity stopped");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeFirebaseListeners();

        // Dismiss any open dialogs
        if (cashbookDialog != null && cashbookDialog.isShowing()) {
            cashbookDialog.dismiss();
        }

        // Clean up ViewBinding references
        binding = null;
        balanceCardBinding = null;
        bottomNavBinding = null;

        Log.d(TAG, "Activity destroyed");
    }

    /**
     * Shows options dialog for guest users
     */
    private void showGuestOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Guest Mode")
                .setMessage("Sign up to access more features like multiple cashbooks and cloud sync.")
                .setPositiveButton("Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(this, SignupActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
    }

    /**
     * Signs out the current user with confirmation dialog
     */
    private void signOutUser() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    try {
                        mAuth.signOut();
                        Log.d(TAG, "User signed out successfully");

                        Intent intent = new Intent(this, SigninActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                    } catch (Exception e) {
                        Log.e(TAG, "Error signing out user", e);
                        ErrorHandler.handleAuthError(this, e);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Handles guest mode setup and data initialization
     */
    private void handleGuestMode() {
        allTransactions.clear();
        currentBalance = 0.0;
        currentIncome = 0.0;
        currentExpense = 0.0;

        updateTransactionTableAndSummary();

        // Update balance card for guest mode
        balanceCardBinding.uidText.setText("UID: GUEST");
        balanceCardBinding.userNameBottom.setText("Guest User");
        balanceCardBinding.balanceText.setText(formatCurrency(0.0));
        balanceCardBinding.moneyIn.setText(formatCurrency(0.0));
        balanceCardBinding.moneyOut.setText(formatCurrency(0.0));

        showGuestModeInfo();
    }

    /**
     * Shows informational message about guest mode limitations
     */
    private void showGuestModeInfo() {
        showSnackbar("Guest Mode: Data will not be saved after app restart");
    }

    /**
     * Loads the active cashbook ID for the user
     */
    private void loadActiveCashbookId(String userId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id_" + userId, null);

        Log.d(TAG, "Loading cashbooks for user");
        startListeningForCashbooks(userId);
    }

    /**
     * Starts listening for cashbook changes from Firebase
     */
    private void startListeningForCashbooks(String userId) {
        setLoadingState(true);

        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    cashbooks.clear();
                    boolean activeCashbookFound = false;

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            cashbook.setId(snapshot.getKey());
                            cashbooks.add(cashbook);

                            if (cashbook.getId().equals(currentCashbookId)) {
                                activeCashbookFound = true;
                            }
                        }
                    }

                    // If no active cashbook or it doesn't exist, use first available
                    if (!activeCashbookFound && !cashbooks.isEmpty()) {
                        currentCashbookId = cashbooks.get(0).getId();
                        saveActiveCashbookId(userId, currentCashbookId);
                    } else if (cashbooks.isEmpty()) {
                        setLoadingState(false);
                        showCreateFirstCashbookDialog(userId);
                        return;
                    }

                    Log.d(TAG, "Loaded " + cashbooks.size() + " cashbooks");
                    updateUserUI();
                    startListeningForTransactions(userId);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing cashbooks", e);
                    showSnackbar("Error loading cashbooks");
                    setLoadingState(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                setLoadingState(false);
                Log.e(TAG, "Cashbooks listener cancelled", databaseError.toException());
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };

        mDatabase.child("users").child(userId).child("cashbooks")
                .addValueEventListener(cashbooksListener);
    }

    /**
     * Starts listening for transaction changes from Firebase
     */
    private void startListeningForTransactions(String userId) {
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        if (currentCashbookId == null) {
            setLoadingState(false);
            return;
        }

        DatabaseReference transactionsRef = mDatabase.child("users").child(userId)
                .child("cashbooks").child(currentCashbookId).child("transactions");

        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    allTransactions.clear();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                        if (transaction != null) {
                            transaction.setTransactionId(snapshot.getKey());
                            allTransactions.add(transaction);
                        }
                    }

                    // Sort by timestamp, newest first
                    Collections.sort(allTransactions, (t1, t2) ->
                            Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                    Log.d(TAG, "Loaded " + allTransactions.size() + " transactions");
                    updateTransactionTableAndSummary();
                    setLoadingState(false);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing transactions", e);
                    showSnackbar("Error loading transactions");
                    setLoadingState(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                setLoadingState(false);
                Log.e(TAG, "Transactions listener cancelled", databaseError.toException());
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        });
    }

    /**
     * Updates the user interface with current user and cashbook information
     */
    private void updateUserUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            try {
                String cashbookName = "Default Cashbook";

                // Find current cashbook name
                for (CashbookModel cashbook : cashbooks) {
                    if (cashbook.getId().equals(currentCashbookId)) {
                        cashbookName = cashbook.getName();
                        break;
                    }
                }

                // Update UI with cashbook name
                if (binding.userNameTop != null) {
                    binding.userNameTop.setText(cashbookName);
                }

                if (currentCashbookTextView != null) {
                    currentCashbookTextView.setText(cashbookName);
                }

                // Update balance card
                if (balanceCardBinding != null) {
                    balanceCardBinding.uidText.setText(
                            "UID: " + currentUser.getUid().substring(0, 8));
                    balanceCardBinding.userNameBottom.setText(getDisplayName(currentUser));
                }

                Log.d(TAG, "UI updated with cashbook: " + cashbookName);

            } catch (Exception e) {
                Log.e(TAG, "Error updating UI", e);
                showSnackbar("Error updating interface");
            }
        }
    }

    /**
     * Gets display name for user, with fallbacks
     */
    private String getDisplayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        } else if (user.getEmail() != null) {
            return user.getEmail();
        } else {
            return "Default User";
        }
    }

    /**
     * Updates transaction table and financial summary
     */
    @SuppressLint("SetTextI18n")
    private void updateTransactionTableAndSummary() {
        try {
            // Clear previous transaction rows (keep header)
            if (binding.transactionTable.getChildCount() >= 2) {
                binding.transactionTable.removeViews(2,
                        binding.transactionTable.getChildCount() - 2);
            }

            // Calculate totals
            double totalIncome = 0, totalExpense = 0;
            for (TransactionModel transaction : allTransactions) {
                if ("IN".equalsIgnoreCase(transaction.getType())) {
                    totalIncome += transaction.getAmount();
                } else {
                    totalExpense += transaction.getAmount();
                }
            }

            double balance = totalIncome - totalExpense;

            // Update balance card
            balanceCardBinding.balanceText.setText(formatCurrency(balance));
            balanceCardBinding.moneyIn.setText(formatCurrency(totalIncome));
            balanceCardBinding.moneyOut.setText(formatCurrency(totalExpense));

            // Set balance text color
            if (balance >= 0) {
                balanceCardBinding.balanceText.setTextColor(
                        ContextCompat.getColor(this, R.color.income_green));
            } else {
                balanceCardBinding.balanceText.setTextColor(
                        ContextCompat.getColor(this, R.color.expense_red));
            }

            // Update current values
            currentBalance = balance;
            currentIncome = totalIncome;
            currentExpense = totalExpense;

            // Add transaction rows to table
            if (allTransactions.isEmpty()) {
                addNoTransactionsRow();
            } else {
                int limit = Math.min(allTransactions.size(), MAX_VISIBLE_TRANSACTIONS);
                for (int i = 0; i < limit; i++) {
                    addTransactionRow(allTransactions.get(i));
                }

                if (allTransactions.size() > MAX_VISIBLE_TRANSACTIONS) {
                    addViewMoreRow(allTransactions.size() - MAX_VISIBLE_TRANSACTIONS);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating transaction table", e);
            showSnackbar("Error updating transaction table");
        }
    }

    /**
     * Formats currency amount for display
     */
    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    /**
     * Adds a "no transactions" row to the table
     */
    private void addNoTransactionsRow() {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);
        row.setPadding(0, 16, 0, 16);

        TextView noDataView = new TextView(this);
        noDataView.setText("No transactions yet");
        noDataView.setTextColor(Color.GRAY);
        noDataView.setTextSize(14);
        noDataView.setGravity(Gravity.CENTER);
        noDataView.setPadding(16, 16, 16, 16);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT);
        params.span = 4;
        noDataView.setLayoutParams(params);

        row.addView(noDataView);
        binding.transactionTable.addView(row);
    }

    /**
     * Adds a "view more" row to the table
     */
    private void addViewMoreRow(int remainingCount) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);
        row.setPadding(0, 8, 0, 8);

        TextView viewMoreView = new TextView(this);
        viewMoreView.setText("+" + remainingCount + " more transactions");
        viewMoreView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        viewMoreView.setTextSize(12);
        viewMoreView.setGravity(Gravity.CENTER);
        viewMoreView.setTypeface(null, Typeface.ITALIC);
        viewMoreView.setPadding(8, 8, 8, 8);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT);
        params.span = 4;
        viewMoreView.setLayoutParams(params);

        row.addView(viewMoreView);
        row.setOnClickListener(v -> navigateToTransactionList());
        binding.transactionTable.addView(row);
    }

    /**
     * Adds a transaction row to the table
     */
    private void addTransactionRow(TransactionModel transaction) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_border);

        // Create table cells
        TextView entryView = createTableCell(transaction.getTransactionCategory(), 2f,
                Typeface.NORMAL, Gravity.START);
        TextView modeView = createTableCell(transaction.getPaymentMode(), 1f,
                Typeface.NORMAL, Gravity.CENTER);
        TextView inView = createTableCell(
                "IN".equalsIgnoreCase(transaction.getType()) ?
                        formatCurrency(transaction.getAmount()) : "-", 1f,
                Typeface.NORMAL, Gravity.CENTER);
        TextView outView = createTableCell(
                "OUT".equalsIgnoreCase(transaction.getType()) ?
                        formatCurrency(transaction.getAmount()) : "-", 1f,
                Typeface.NORMAL, Gravity.CENTER);

        // Set colors
        modeView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        inView.setTextColor(ContextCompat.getColor(this, R.color.income_green));
        outView.setTextColor(ContextCompat.getColor(this, R.color.expense_red));

        row.addView(entryView);
        row.addView(modeView);
        row.addView(inView);
        row.addView(outView);

        row.setOnClickListener(v -> openTransactionDetail(transaction));
        binding.transactionTable.addView(row);
    }

    /**
     * Creates a table cell with specified parameters
     */
    private TextView createTableCell(String text, float weight, int style, int gravity) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));
        textView.setBackgroundResource(R.drawable.table_cell_border);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.MATCH_PARENT, weight);
        textView.setLayoutParams(params);

        textView.setTextColor(Color.BLACK);
        textView.setTypeface(null, style);
        textView.setGravity(gravity);
        textView.setTextSize(14);

        return textView;
    }

    /**
     * Converts dp to pixels
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Opens transaction detail activity
     */
    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);
    }

    /**
     * Opens cash in/out activity
     */
    private void openCashInOutActivity(String type) {
        if (isGuest) {
            showGuestLimitationDialog();
            return;
        }

        if (currentCashbookId == null) {
            showSnackbar("Please create a cashbook first");
            return;
        }

        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra("transaction_type", type);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        Log.d(TAG, "Opened CashInOut activity for: " + type);
    }

    /**
     * Navigates to transaction list activity
     */
    private void navigateToTransactionList() {
        Intent intent = new Intent(this, TransactionActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        Log.d(TAG, "Navigated to transactions list");
    }

    /**
     * Navigates to settings activity
     */
    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        Log.d(TAG, "Navigated to settings");
    }

    /**
     * Shows guest mode limitation dialog
     */
    private void showGuestLimitationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Guest Mode Limitation")
                .setMessage("This feature is not available in guest mode. Please sign up to access full functionality.")
                .setPositiveButton("Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(this, SignupActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
    }

    /**
     * Shows create first cashbook dialog
     */
    private void showCreateFirstCashbookDialog(String userId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome to CashFlow");
        builder.setMessage("Let's create your first cashbook to get started!");

        final EditText input = new EditText(this);
        input.setHint("Enter cashbook name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                showSnackbar("Please enter a cashbook name");
                showCreateFirstCashbookDialog(userId);
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Shows create new cashbook dialog
     */
    private void showCreateNewCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Cashbook");

        final EditText input = new EditText(this);
        input.setHint("Enter new cashbook name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                showSnackbar("Please enter a valid cashbook name");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Creates a new cashbook in Firebase
     */
    private void createNewCashbook(String name) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        setLoadingState(true);
        String userId = currentUser.getUid();
        DatabaseReference cashbooksRef = mDatabase.child("users").child(userId).child("cashbooks");
        String cashbookId = cashbooksRef.push().getKey();

        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            cashbooksRef.child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        showSnackbar("Cashbook '" + name + "' created successfully");
                        Log.d(TAG, "Created cashbook: " + name);
                        switchCashbook(cashbookId);
                        setLoadingState(false);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating cashbook", e);
                        showSnackbar("Failed to create cashbook");
                        setLoadingState(false);
                    });

        } else {
            setLoadingState(false);
            showSnackbar("Error generating cashbook ID");
        }
    }

    /**
     * Switches to a different cashbook
     */
    private void switchCashbook(String newCashbookId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        // Remove old listener
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(currentUser.getUid())
                    .child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentUser.getUid(), currentCashbookId);

        Log.d(TAG, "Switched to cashbook: " + newCashbookId);
        updateUserUI();
        startListeningForTransactions(currentUser.getUid());
    }

    /**
     * Saves the active cashbook ID to preferences
     */
    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }

    /**
     * Sets the loading state of the UI
     */
    private void setLoadingState(boolean loading) {
        isLoading = loading;
        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.viewFullTransactionsButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);

        if (cashbookDropdownContainer != null) {
            cashbookDropdownContainer.setEnabled(!loading);
        }
    }

    /**
     * Shows a Snackbar message to the user
     */
    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * Removes all Firebase listeners to prevent memory leaks
     */
    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
            transactionsListener = null;
        }

        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
            cashbooksListener = null;
        }

        if (userProfileListener != null) {
            mDatabase.child("users").child(userId)
                    .removeEventListener(userProfileListener);
            userProfileListener = null;
        }
    }
}
