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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cashflow.databinding.ActivityHomePageBinding;
import com.example.cashflow.databinding.LayoutBottomNavigationBinding;
import com.example.cashflow.utils.ErrorHandler;
import com.example.cashflow.utils.ThemeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int MAX_VISIBLE_TRANSACTIONS = 5;

    // ViewBinding declarations
    private ActivityHomePageBinding binding;
    private LayoutBottomNavigationBinding bottomNavBinding;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this);

        super.onCreate(savedInstanceState);

        // Initialize ViewBinding
        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize bottom navigation binding
        bottomNavBinding = LayoutBottomNavigationBinding.bind(binding.bottomNavBar.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize currency formatter
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Get guest mode status
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        // Log activity start
        logToAnalytics("HomePage: Activity started, guest=" + isGuest);

        setupUI();
        setupClickListeners();

        // Set active bottom navigation
        bottomNavBinding.btnHome.setSelected(true);
    }

    private void setupUI() {
        // Set initial loading state
        setLoadingState(false);

        // Handle different user types
        if (isGuest) {
            handleGuestMode();
        }

        // Add accessibility content descriptions
        binding.cashInButton.setContentDescription("Add money in transaction");
        binding.cashOutButton.setContentDescription("Add money out transaction");
        binding.userBox.setContentDescription("Switch cashbook or view user menu");
        binding.viewFullTransactionsButton.setContentDescription("View all transactions");
    }

    private void setupClickListeners() {
        // Main activity click listeners
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        binding.viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());
        binding.userBox.setOnClickListener(v -> showUserDropdown());

        // Bottom navigation click listeners
        bottomNavBinding.btnHome.setOnClickListener(v ->
                Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show());
        bottomNavBinding.btnTransactions.setOnClickListener(v -> navigateToTransactionList());
        bottomNavBinding.btnSettings.setOnClickListener(v -> navigateToSettings());
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && !isGuest) {
            logToAnalytics("HomePage: Authenticated user session started");
            loadActiveCashbookId(currentUser.getUid());
        } else {
            logToAnalytics("HomePage: Guest mode session started");
            handleGuestMode();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
        logToAnalytics("HomePage: Activity stopped");
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
        bottomNavBinding = null;

        logToAnalytics("HomePage: Activity destroyed");
    }

    // ===== USER DROPDOWN METHOD (SOLUTION TO THE ERROR) =====
    private void showUserDropdown() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || isGuest) {
            showGuestOptions();
            return;
        }

        try {
            // Create PopupMenu
            PopupMenu popupMenu = new PopupMenu(this, binding.userBox);
            popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

            // Set click listener
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int itemId = item.getItemId();

                    if (itemId == R.id.action_switch) {
                        showCashbookSwitcher();
                        return true;
                    } else if (itemId == R.id.action_add) {
                        showCreateNewCashbookDialog();
                        return true;
                    } else if (itemId == R.id.action_settings) {
                        navigateToSettings();
                        return true;
                    } else if (itemId == R.id.action_signout) {
                        signOutUser();
                        return true;
                    }
                    return false;
                }
            });

            // Show the popup menu
            popupMenu.show();

            logToAnalytics("HomePage: User dropdown shown");

        } catch (Exception e) {
            Log.e(TAG, "Error showing user dropdown", e);
            recordException(e);
            showError("Unable to show user options");
        }
    }

    private void showGuestOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Guest Mode")
                .setMessage("Sign up to access more features like multiple cashbooks and cloud sync.")
                .setPositiveButton("Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(this, Signup.class);
                    startActivity(intent);
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
    }

    private void showCashbookSwitcher() {
        if (cashbooks.isEmpty()) {
            showError("No cashbooks available");
            return;
        }

        String[] cashbookNames = new String[cashbooks.size()];
        for (int i = 0; i < cashbooks.size(); i++) {
            String name = cashbooks.get(i).getName();
            if (cashbooks.get(i).getId().equals(currentCashbookId)) {
                name += " (Current)";
            }
            cashbookNames[i] = name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Switch Cashbook")
                .setItems(cashbookNames, (dialog, which) -> {
                    CashbookModel selectedCashbook = cashbooks.get(which);
                    if (!selectedCashbook.getId().equals(currentCashbookId)) {
                        switchCashbook(selectedCashbook.getId());
                        Toast.makeText(this, "Switched to: " + selectedCashbook.getName(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    private void signOutUser() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    try {
                        // Sign out from Firebase
                        mAuth.signOut();

                        logToAnalytics("HomePage: User signed out");

                        // Navigate to signin
                        Intent intent = new Intent(this, Signin.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                    } catch (Exception e) {
                        Log.e(TAG, "Error signing out", e);
                        recordException(e);
                        showError("Error signing out");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ===== GUEST MODE HANDLING =====
    private void handleGuestMode() {
        allTransactions.clear();
        updateTransactionTableAndSummary();

        // Update UI for guest mode
        binding.userNameTop.setText("Guest User");
        binding.uidText.setText("UID: GUEST");
        binding.userNameBottom.setText("Guest User");
        binding.dateToday.setText("Guest Session - Data stored locally only");

        // Show guest mode info
        showGuestModeInfo();
    }

    private void showGuestModeInfo() {
        Toast.makeText(this, "Guest Mode: Data will not be saved after app restart",
                Toast.LENGTH_LONG).show();
    }

    // ===== CASHBOOK MANAGEMENT =====
    private void loadActiveCashbookId(String userId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id_" + userId, null);

        logToAnalytics("HomePage: Loading cashbooks for user");
        startListeningForCashbooks(userId);
    }

    private void startListeningForCashbooks(String userId) {
        setLoadingState(true);

        // Remove existing listener
        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks").removeEventListener(cashbooksListener);
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

                    // Handle cashbook selection logic
                    if (!activeCashbookFound && !cashbooks.isEmpty()) {
                        currentCashbookId = cashbooks.get(0).getId();
                        saveActiveCashbookId(userId, currentCashbookId);
                    } else if (cashbooks.isEmpty()) {
                        setLoadingState(false);
                        showCreateFirstCashbookDialog(userId);
                        return;
                    }

                    logToAnalytics("HomePage: Loaded " + cashbooks.size() + " cashbooks");
                    updateUserUI();
                    startListeningForTransactions(userId);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing cashbooks data", e);
                    recordException(e);
                    setLoadingState(false);
                    showError("Error loading cashbooks");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                setLoadingState(false);
                Log.e(TAG, "Cashbooks listener cancelled", databaseError.toException());
                recordException(databaseError.toException());
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };

        mDatabase.child("users").child(userId).child("cashbooks")
                .addValueEventListener(cashbooksListener);
    }

    private void startListeningForTransactions(String userId) {
        // Remove existing listener
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

                    // Sort transactions by timestamp (newest first)
                    Collections.sort(allTransactions,
                            (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                    logToAnalytics("HomePage: Loaded " + allTransactions.size() + " transactions");
                    updateTransactionTableAndSummary();
                    setLoadingState(false);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing transactions data", e);
                    recordException(e);
                    setLoadingState(false);
                    showError("Error loading transactions");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                setLoadingState(false);
                Log.e(TAG, "Transactions listener cancelled", databaseError.toException());
                recordException(databaseError.toException());
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        });
    }

    // ===== UI UPDATES =====
    @SuppressLint("SetTextI18n")
    private void updateUserUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            try {
                String cashbookName = "My Cashbook"; // Default

                // Find current cashbook name
                for (CashbookModel cashbook : cashbooks) {
                    if (cashbook.getId().equals(currentCashbookId)) {
                        cashbookName = cashbook.getName();
                        break;
                    }
                }

                // Update UI elements
                binding.userNameTop.setText(cashbookName);
                binding.userNameBottom.setText(getDisplayName(currentUser));
                binding.uidText.setText("UID: " + currentUser.getUid().substring(0, 8) + "...");
                binding.dateToday.setText("Last Updated: " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(new Date()));

            } catch (Exception e) {
                Log.e(TAG, "Error updating user UI", e);
                recordException(e);
            }
        }
    }

    private String getDisplayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        } else if (user.getEmail() != null) {
            return user.getEmail();
        } else {
            return "User";
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateTransactionTableAndSummary() {
        try {
            // Clear previous transaction rows (keep header)
            if (binding.transactionTable.getChildCount() > 2) {
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

            // Update summary cards with formatted currency
            binding.balanceText.setText(formatCurrency(balance));
            binding.moneyIn.setText(formatCurrency(totalIncome));
            binding.moneyOut.setText(formatCurrency(totalExpense));

            // Set balance text color based on positive/negative
            if (balance >= 0) {
                binding.balanceText.setTextColor(ContextCompat.getColor(this, R.color.income_green));
            } else {
                binding.balanceText.setTextColor(ContextCompat.getColor(this, R.color.expense_red));
            }

            // Add recent transactions to table
            if (allTransactions.isEmpty()) {
                addNoTransactionsRow();
            } else {
                int limit = Math.min(allTransactions.size(), MAX_VISIBLE_TRANSACTIONS);
                for (int i = 0; i < limit; i++) {
                    addTransactionRow(allTransactions.get(i));
                }

                // Show "View More" indicator if there are more transactions
                if (allTransactions.size() > MAX_VISIBLE_TRANSACTIONS) {
                    addViewMoreRow(allTransactions.size() - MAX_VISIBLE_TRANSACTIONS);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating transaction table", e);
            recordException(e);
            showError("Error updating transaction display");
        }
    }

    private String formatCurrency(double amount) {
        return "₹" + String.format(Locale.US, "%.2f", amount);
    }

    private void addNoTransactionsRow() {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);
        row.setPadding(0, 16, 0, 16);

        TextView noDataView = new TextView(this);
        noDataView.setText("No transactions yet. Start by adding your first transaction!");
        noDataView.setTextColor(Color.GRAY);
        noDataView.setTextSize(14);
        noDataView.setGravity(Gravity.CENTER);
        noDataView.setPadding(16, 16, 16, 16);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.span = 4; // Span across all 4 columns
        noDataView.setLayoutParams(params);

        row.addView(noDataView);
        binding.transactionTable.addView(row);
    }

    private void addViewMoreRow(int remainingCount) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);
        row.setPadding(0, 8, 0, 8);

        TextView viewMoreView = new TextView(this);
        viewMoreView.setText("... and " + remainingCount + " more transactions");
        viewMoreView.setTextColor(ContextCompat.getColor(this, R.color.balance_blue));
        viewMoreView.setTextSize(12);
        viewMoreView.setGravity(Gravity.CENTER);
        viewMoreView.setTypeface(null, Typeface.ITALIC);
        viewMoreView.setPadding(8, 8, 8, 8);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.span = 4;
        viewMoreView.setLayoutParams(params);

        row.addView(viewMoreView);
        row.setOnClickListener(v -> navigateToTransactionList());
        binding.transactionTable.addView(row);
    }

    // ===== UPDATED TRANSACTION ROW WITH PROPER ALIGNMENT =====
    private void addTransactionRow(TransactionModel transaction) {TableRow row = new TableRow(this);row.setBackgroundResource(R.drawable.table_border);row.setPadding(0, 0, 0, 0);

        // Transaction category - LEFT aligned with custom padding for blue line alignment
        TextView entryView = createTableCellWithPadding(transaction.getTransactionCategory(), 2f, Typeface.NORMAL, Gravity.START, 10, 4);

        // Mode - CENTER aligned
        TextView modeView = createPerfectCenterCell(transaction.getPaymentMode(), 1f);
        modeView.setBackgroundResource(R.drawable.table_cell_border);

        // IN column - START aligned (cash in entries on LEFT side)
        TextView inView = createTableCell("IN".equalsIgnoreCase(transaction.getType()) ? formatCurrency(transaction.getAmount()) : "-", 1f, Typeface.NORMAL, Gravity.CENTER);

        // OUT column - END aligned (cash out entries on RIGHT side)
        TextView outView = createTableCell("OUT".equalsIgnoreCase(transaction.getType()) ? formatCurrency(transaction.getAmount()) : "-", 1f, Typeface.NORMAL, Gravity.CENTER);

        // Set colors
        modeView.setTextColor(ContextCompat.getColor(this, R.color.balance_blue));
        inView.setTextColor(ContextCompat.getColor(this, R.color.income_green));
        outView.setTextColor(ContextCompat.getColor(this, R.color.expense_red));

        // Add views to row in correct order: Transactions, Mode, In, Out
        row.addView(entryView);
        row.addView(modeView);
        row.addView(inView);
        row.addView(outView);

        row.setBackground(ContextCompat.getDrawable(this, R.drawable.table_row_selector));

        binding.transactionTable.addView(row);
    }

    // ===== HELPER METHODS FOR TABLE CREATION =====

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Create table cell with basic padding and gravity
     */
    /**
     * Create table cell with border
     */
    private TextView createTableCell(String text, float weight, int style, int gravity) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));

        // Add border to each cell
        textView.setBackgroundResource(R.drawable.table_cell_border);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.MATCH_PARENT, weight);
        textView.setLayoutParams(params);

        textView.setTextColor(Color.BLACK);
        textView.setTypeface(null, style);

        if (gravity == Gravity.CENTER) {
            textView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        } else {
            textView.setGravity(gravity);
        }

        textView.setTextSize(14);

        return textView;
    }


    /**
     * Create table cell with custom padding and margin for alignment
     */
    /**
     * Create table cell with custom padding and border
     */
    private TextView createTableCellWithPadding(String text, float weight, int style, int gravity, int leftPaddingDp, int leftMarginDp) {
        TextView textView = new TextView(this);
        textView.setText(text);

        // Set custom padding
        int leftPadding = dpToPx(leftPaddingDp);
        int normalPadding = dpToPx(12);
        textView.setPadding(leftPadding, normalPadding, dpToPx(8), normalPadding);

        // Add border to each cell
        textView.setBackgroundResource(R.drawable.table_cell_border);

        // Set layout params with margin
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dpToPx(leftMarginDp), 0, 0, 0);
        textView.setLayoutParams(params);

        textView.setTextColor(Color.BLACK);
        textView.setTypeface(null, style);
        textView.setGravity(gravity);
        textView.setTextSize(14);

        return textView;
    }


    private TextView createPerfectCenterCell(String text, float weight) {
        TextView textView = new TextView(this);
        textView.setText(text);

        // Set padding
        textView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        // MATCH_PARENT height is crucial for vertical centering in TableRow
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.MATCH_PARENT, weight);
        textView.setLayoutParams(params);

        // Perfect center alignment - both horizontal AND vertical
        textView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        textView.setTextColor(Color.BLACK);
        textView.setTypeface(null, Typeface.NORMAL);
        textView.setTextSize(14);

        return textView;
    }


    // ===== NAVIGATION METHODS =====
    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);
    }

    private void openCashInOutActivity(String type) {
        if (isGuest) {
            showGuestLimitationDialog();
            return;
        }

        if (currentCashbookId == null) {
            showError("Please create a cashbook first");
            return;
        }

        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra("transaction_type", type);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        logToAnalytics("HomePage: Opened CashInOut activity, type=" + type);
    }

    private void navigateToTransactionList() {
        Intent intent = new Intent(this, TransactionActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        logToAnalytics("HomePage: Navigated to transaction list");
    }

    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        logToAnalytics("HomePage: Navigated to settings");
    }

    private void showGuestLimitationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Guest Mode Limitation")
                .setMessage("To add transactions, please sign up for a free account. " +
                        "Your data will be securely stored and synced across devices.")
                .setPositiveButton("Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(this, Signup.class);
                    startActivity(intent);
                })
                .setNegativeButton("Continue as Guest", null)
                .show();
    }

    // ===== CASHBOOK MANAGEMENT METHODS =====
    private void showCreateFirstCashbookDialog(String userId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome to Cash Flow!");
        builder.setMessage("Let's create your first cashbook to start tracking your finances.");

        final EditText input = new EditText(this);
        input.setHint("e.g., My Main Cashbook");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                Toast.makeText(this, "Please enter a cashbook name", Toast.LENGTH_SHORT).show();
                showCreateFirstCashbookDialog(userId);
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void showCreateNewCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Cashbook");

        final EditText input = new EditText(this);
        input.setHint("e.g., Family Expenses");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                Toast.makeText(this, "Cashbook name cannot be empty.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

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
                        Toast.makeText(this, "Cashbook '" + name + "' created!", Toast.LENGTH_SHORT).show();
                        logToAnalytics("HomePage: Created new cashbook - " + name);
                        switchCashbook(cashbookId);
                        setLoadingState(false);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating cashbook", e);
                        recordException(e);
                        ErrorHandler.handleExportError(this, e);
                        setLoadingState(false);
                    });
        } else {
            setLoadingState(false);
            showError("Error creating cashbook");
        }
    }

    private void switchCashbook(String newCashbookId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        // Remove old transaction listener
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(currentUser.getUid())
                    .child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        // Update current cashbook
        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentUser.getUid(), currentCashbookId);

        logToAnalytics("HomePage: Switched to cashbook - " + newCashbookId);

        // Refresh UI and data
        updateUserUI();
        startListeningForTransactions(currentUser.getUid());
    }

    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }

    // ===== UTILITY METHODS =====

    // Loading state management
    private void setLoadingState(boolean loading) {
        isLoading = loading;

        // Disable/enable interactive elements during loading
        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.viewFullTransactionsButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);
    }

    // Error handling
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Error shown to user: " + message);
    }

    // Analytics and logging
    private void logToAnalytics(String message) {
        Log.d(TAG, message);
        try {
            FirebaseCrashlytics.getInstance().log(message);
        } catch (Exception e) {
            // Crashlytics not available
        }
    }

    private void recordException(Exception exception) {
        try {
            FirebaseCrashlytics.getInstance().recordException(exception);
        } catch (Exception e) {
            // Crashlytics not available
        }
    }

    // Firebase listener cleanup
    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        // Remove transactions listener
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
            transactionsListener = null;
        }

        // Remove cashbooks listener
        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
            cashbooksListener = null;
        }

        // Remove user profile listener
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId)
                    .removeEventListener(userProfileListener);
            userProfileListener = null;
        }
    }
}
