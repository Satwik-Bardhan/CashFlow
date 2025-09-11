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
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cashflow.databinding.ActivityHomePageBinding;
import com.example.cashflow.databinding.ComponentBalanceCardBinding;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int MAX_VISIBLE_TRANSACTIONS = 5;

    // ViewBinding declarations
    private ActivityHomePageBinding binding;
    private ComponentBalanceCardBinding balanceCardBinding;
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

    // ===== ANIMATED BALANCE CARD =====
    private AnimatedBalanceCard animatedBalanceCard;
    private double currentBalance = 0.0;
    private double currentIncome = 0.0;
    private double currentExpense = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // [FIXED] The redundant call to applyTheme has been removed.
        // The theme is already set by MyApplication.java before this activity starts.
        super.onCreate(savedInstanceState);

        // Initialize ViewBinding
        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize balance card binding from the included layout
        balanceCardBinding = binding.balanceCardView;

        // Initialize bottom navigation binding from the included layout
        bottomNavBinding = binding.bottomNavCard;

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
        setupAnimatedBalanceCard();
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

    // ===== ANIMATED BALANCE CARD SETUP =====
    private void setupAnimatedBalanceCard() {
        try {
            // Initialize the animated balance card using the included layout
            View balanceCardComponent = binding.balanceCardView.getRoot();
            if (balanceCardComponent != null) {
                animatedBalanceCard = new AnimatedBalanceCard(balanceCardComponent);

                // Set initial user info
                setupInitialBalanceCardData();

                // Set click listener for balance card
                animatedBalanceCard.setOnCardClickListener(v -> {
                    // Navigate to detailed balance view or show balance breakdown
                    showBalanceBreakdownDialog();
                });

                // Show card with entry animation
                animatedBalanceCard.showWithAnimation();

                Log.d(TAG, "Animated balance card initialized successfully");
            } else {
                Log.w(TAG, "Balance card component not found in layout");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up animated balance card", e);
            recordException(e);
        }
    }

    private void setupInitialBalanceCardData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !isGuest) {
            String uid = "UID: " + currentUser.getUid().substring(0, 8) + "...";
            String displayName = getDisplayName(currentUser);

            // Set initial data in balance card views
            balanceCardBinding.uidText.setText(uid);
            balanceCardBinding.userNameBottom.setText(displayName);

            if (animatedBalanceCard != null) {
                animatedBalanceCard.setUserInfo(uid, displayName);
            }
        } else if (isGuest) {
            balanceCardBinding.uidText.setText("UID: GUEST");
            balanceCardBinding.userNameBottom.setText("Guest User");

            if (animatedBalanceCard != null) {
                animatedBalanceCard.setUserInfo("UID: GUEST", "Guest User");
            }
        }

        // Set initial balance data (will be updated when Firebase data loads)
        balanceCardBinding.balanceText.setText("₹0.00");
        balanceCardBinding.moneyIn.setText("₹0.00");
        balanceCardBinding.moneyOut.setText("₹0.00");

        if (animatedBalanceCard != null) {
            animatedBalanceCard.setBalanceData(0.0, 0.0, 0.0);
        }
    }

    private void showBalanceBreakdownDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Balance Breakdown");

        String message = String.format(Locale.US,
                "Current Balance: ₹%.2f\n\n" +
                        "Total Income: ₹%.2f\n" +
                        "Total Expenses: ₹%.2f\n\n" +
                        "Transactions: %d",
                currentBalance, currentIncome, currentExpense, allTransactions.size());

        builder.setMessage(message);
        builder.setPositiveButton("View Transactions", (dialog, which) -> navigateToTransactionList());
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void setupClickListeners() {
        // Main activity click listeners
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        binding.viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());
        binding.userBox.setOnClickListener(v -> showUserDropdownFromRight(binding.userDropdownIcon));
        binding.userDropdownIcon.setOnClickListener(v -> showUserDropdownFromRight(binding.userDropdownIcon));

        // Bottom navigation click listeners
        bottomNavBinding.btnHome.setOnClickListener(v ->
                Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show());
        bottomNavBinding.btnTransactions.setOnClickListener(v -> navigateToTransactionList());
        bottomNavBinding.btnSettings.setOnClickListener(v -> navigateToSettings());
    }

    private void showUserDropdownFromRight(View anchorView) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || isGuest) {
            showGuestOptions();
            return;
        }
        try {
            PopupMenu popupMenu = new PopupMenu(this, anchorView);
            popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                popupMenu.setGravity(Gravity.END); // Force drop from right
            }
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_switch) { showCashbookSwitcher(); return true; }
                if (itemId == R.id.action_add) { showCreateNewCashbookDialog(); return true; }
                if (itemId == R.id.action_settings) { navigateToSettings(); return true; }
                if (itemId == R.id.action_signout) { signOutUser(); return true; }
                return false;
            });
            popupMenu.show();
            logToAnalytics("HomePage: User dropdown shown (right side)");
        } catch (Exception e) {
            Log.e(TAG, "Error showing user dropdown", e);
            recordException(e);
            showError("Unable to show user options");
        }
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
        balanceCardBinding = null;
        bottomNavBinding = null;
        animatedBalanceCard = null;

        logToAnalytics("HomePage: Activity destroyed");
    }

    // ===== USER DROPDOWN METHOD =====
    private void showUserDropdown() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || isGuest) {
            showGuestOptions();
            return;
        }

        try {
            PopupMenu popupMenu = new PopupMenu(this, binding.userBox);
            popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
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
            });

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
                    Intent intent = new Intent(this, SignupActivity.class);
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
                        mAuth.signOut();
                        logToAnalytics("HomePage: User signed out");

                        Intent intent = new Intent(this, SigninActivity.class);
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
        currentBalance = 0.0;
        currentIncome = 0.0;
        currentExpense = 0.0;

        updateTransactionTableAndSummary();

        // Update balance card views for guest mode
        balanceCardBinding.uidText.setText("UID: GUEST");
        balanceCardBinding.userNameBottom.setText("Guest User");
        balanceCardBinding.balanceText.setText("₹0.00");
        balanceCardBinding.moneyIn.setText("₹0.00");
        balanceCardBinding.moneyOut.setText("₹0.00");

        // Update animated balance card for guest mode
        if (animatedBalanceCard != null) {
            animatedBalanceCard.setUserInfo("UID: GUEST", "Guest User");
            animatedBalanceCard.setBalanceData(0.0, 0.0, 0.0);
        }

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

    // ===== UI UPDATES WITH ANIMATION =====
    @SuppressLint("SetTextI18n")
    private void updateUserUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            try {
                String cashbookName = "My Cashbook"; // Default name

                // Find current cashbook name
                for (CashbookModel cashbook : cashbooks) {
                    if (cashbook.getId().equals(currentCashbookId)) {
                        cashbookName = cashbook.getName();
                        break;
                    }
                }

                // ===== UPDATE USER NAME TO SHOW CASHBOOK NAME =====
                // This should show the cashbook name, not the user's display name
                if (binding.userNameTop != null) {
                    binding.userNameTop.setText(cashbookName); // Show cashbook name
                }

                // Update balance card views
                if (balanceCardBinding != null) {
                    balanceCardBinding.uidText.setText("UID: " + currentUser.getUid().substring(0, 8) + "...");
                    balanceCardBinding.userNameBottom.setText(getDisplayName(currentUser)); // User name at bottom
                }

                // Update animated balance card user info
                if (animatedBalanceCard != null) {
                    animatedBalanceCard.setUserInfo(
                            "UID: " + currentUser.getUid().substring(0, 8) + "...",
                            getDisplayName(currentUser)
                    );
                }

                Log.d(TAG, "UI updated with cashbook: " + cashbookName);

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

            // Update balance card views
            balanceCardBinding.balanceText.setText(formatCurrency(balance));
            balanceCardBinding.moneyIn.setText(formatCurrency(totalIncome));
            balanceCardBinding.moneyOut.setText(formatCurrency(totalExpense));

            // Set balance text color based on positive/negative
            if (balance >= 0) {
                balanceCardBinding.balanceText.setTextColor(ContextCompat.getColor(this, R.color.income_green));
            } else {
                balanceCardBinding.balanceText.setTextColor(ContextCompat.getColor(this, R.color.expense_red));
            }

            // ===== ANIMATE BALANCE CARD WITH NEW VALUES =====
            if (animatedBalanceCard != null) {
                // Check if values have changed to trigger animation
                boolean valuesChanged = (Math.abs(balance - currentBalance) > 0.01) ||
                        (Math.abs(totalIncome - currentIncome) > 0.01) ||
                        (Math.abs(totalExpense - currentExpense) > 0.01);

                if (valuesChanged) {
                    // Update with animation
                    animatedBalanceCard.updateBalanceWithAnimation(balance, totalIncome, totalExpense);
                } else {
                    // Set without animation (for initial load)
                    animatedBalanceCard.setBalanceData(balance, totalIncome, totalExpense);
                }
            }

            // Update current values for future comparison
            currentBalance = balance;
            currentIncome = totalIncome;
            currentExpense = totalExpense;

            // Add recent transactions to table
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
            recordException(e);
            showError("Error updating transaction display");
        }
    }

    private String formatCurrency(double amount) {
        return "₹" + String.format(Locale.US, "%.2f", amount);
    }

    // ===== TRANSACTION HIGHLIGHT METHOD =====
    public void onNewTransactionAdded() {
        // Call this method when a new transaction is added to highlight the balance card
        if (animatedBalanceCard != null) {
            animatedBalanceCard.highlightNewTransaction();
        }
    }

    public void onBalanceError() {
        // Call this method for error states to shake the balance card
        if (animatedBalanceCard != null) {
            animatedBalanceCard.shakeAnimation();
        }
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
        params.span = 4;
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

    private void addTransactionRow(TransactionModel transaction) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_border);
        row.setPadding(0, 0, 0, 0);

        TextView entryView = createTableCellWithPadding(transaction.getTransactionCategory(), 2f, Typeface.NORMAL, Gravity.START, 10, 4);
        entryView.setBackgroundResource(R.drawable.table_cell_border);

        TextView modeView = createPerfectCenterCell(transaction.getPaymentMode(), 1f);
        modeView.setBackgroundResource(R.drawable.table_cell_border);

        TextView inView = createTableCell("IN".equalsIgnoreCase(transaction.getType()) ? formatCurrency(transaction.getAmount()) : "-", 1f, Typeface.NORMAL, Gravity.CENTER);
        inView.setBackgroundResource(R.drawable.table_cell_border);

        TextView outView = createTableCell("OUT".equalsIgnoreCase(transaction.getType()) ? formatCurrency(transaction.getAmount()) : "-", 1f, Typeface.NORMAL, Gravity.CENTER);
        outView.setBackgroundResource(R.drawable.table_cell_border);

        modeView.setTextColor(ContextCompat.getColor(this, R.color.balance_blue));
        inView.setTextColor(ContextCompat.getColor(this, R.color.income_green));
        outView.setTextColor(ContextCompat.getColor(this, R.color.expense_red));

        row.addView(entryView);
        row.addView(modeView);
        row.addView(inView);
        row.addView(outView);

        row.setBackground(ContextCompat.getDrawable(this, R.drawable.table_row_selector));

        binding.transactionTable.addView(row);
    }

    // ===== HELPER METHODS FOR TABLE CREATION =====
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

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

        if (gravity == Gravity.CENTER) {
            textView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        } else {
            textView.setGravity(gravity);
        }

        textView.setTextSize(14);
        return textView;
    }

    private TextView createTableCellWithPadding(String text, float weight, int style, int gravity, int leftPaddingDp, int leftMarginDp) {
        TextView textView = new TextView(this);
        textView.setText(text);

        int leftPadding = dpToPx(leftPaddingDp);
        int normalPadding = dpToPx(12);
        textView.setPadding(leftPadding, normalPadding, dpToPx(8), normalPadding);
        textView.setBackgroundResource(R.drawable.table_cell_border);

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
        textView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.MATCH_PARENT, weight);
        textView.setLayoutParams(params);

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
                    Intent intent = new Intent(this, SignupActivity.class);
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

        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(currentUser.getUid())
                    .child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentUser.getUid(), currentCashbookId);

        logToAnalytics("HomePage: Switched to cashbook - " + newCashbookId);

        updateUserUI();
        startListeningForTransactions(currentUser.getUid());
    }

    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }

    // ===== UTILITY METHODS =====
    private void setLoadingState(boolean loading) {
        isLoading = loading;

        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.viewFullTransactionsButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Error shown to user: " + message);
    }

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

