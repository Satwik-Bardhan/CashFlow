package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
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
// Removed unused dialogs and models:
// import com.example.cashflow.dialogs.CashbookSwitchDialog;
// import com.example.cashflow.models.Cashbook;
import com.example.cashflow.utils.ErrorHandler;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

// Added missing imports for navigation
import com.example.cashflow.CashInOutActivity;
import com.example.cashflow.EditTransactionActivity;
import com.example.cashflow.TransactionActivity;
import com.example.cashflow.SignupActivity;
import com.example.cashflow.SigninActivity;
import com.example.cashflow.CashbookSwitchActivity;
import com.example.cashflow.SettingsActivity;


public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int MAX_VISIBLE_TRANSACTIONS = 5;

    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

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
    private String currentUserId; // Added missing field declaration
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

        // Get current user
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        // Initialize currency formatter
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Get guest mode status and cashbook ID
        Intent intent = getIntent();
        isGuest = intent.getBooleanExtra("isGuest", false);
        currentCashbookId = intent.getStringExtra("cashbook_id");


        Log.d(TAG, "HomePage started, guest mode: " + isGuest);

        setupUI();
        setupClickListeners();
        initDropdownViews();
        setupDropdownClick();
        setupBottomNavigation(); // Added missing call
    }


    private void setupBottomNavigation() {
        // Set active bottom navigation
        bottomNavBinding.btnHome.setSelected(true);

        // Bottom navigation click listeners
        bottomNavBinding.btnHome.setOnClickListener(v ->
                showSnackbar("Already on Home"));

        bottomNavBinding.btnTransactions.setOnClickListener(v ->
                navigateToTransactionList());

        // Cashbook Switcher Button - Opens CashbookSwitchActivity
        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v ->
                openCashbookSwitcher());

        bottomNavBinding.btnSettings.setOnClickListener(v ->
                navigateToSettings());

        // Update cashbook count badge
        updateCashbookBadge();

        Log.d(TAG, "Bottom navigation setup complete");
    }

    private void openCashbookSwitcher() {
        if (isGuest) {
            showGuestLimitationDialog();
            return;
        }

        if (currentUserId == null) {
            showSnackbar("Please log in first");
            return;
        }

        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);

        Log.d(TAG, "Opened CashbookSwitchActivity");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CASHBOOK_SWITCH && resultCode == RESULT_OK && data != null) {
            String newCashbookId = data.getStringExtra("selected_cashbook_id");
            String cashbookName = data.getStringExtra("cashbook_name");

            if (newCashbookId != null && !newCashbookId.equals(currentCashbookId)) {
                switchCashbook(newCashbookId);
                showSnackbar("Switched to: " + cashbookName);
                Log.d(TAG, "Cashbook switched via activity result: " + cashbookName);
            }
        }
    }

    private void updateCashbookBadge() {
        if (bottomNavBinding.btnCashbookSwitch == null || isGuest || currentUserId == null) {
            return;
        }

        loadCashbooksForBadge();
    }

    private void loadCashbooksForBadge() {
        if (currentUserId == null) return;

        // Detach any existing listener before adding a new one
        if (cashbooksListener != null) {
            mDatabase.child("users").child(currentUserId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        if (cashbook.getCashbookId() == null) {
                            cashbook.setCashbookId(snapshot.getKey());
                        }
                        cashbooks.add(cashbook);
                    }
                }

                updateBadgeDisplay();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks for badge", databaseError.toException());
            }
        };

        mDatabase.child("users").child(currentUserId).child("cashbooks")
                .addValueEventListener(cashbooksListener);
    }

    private void updateBadgeDisplay() {
        if (bottomNavBinding == null || bottomNavBinding.btnCashbookSwitch == null) {
            return;
        }

        try {
            int cashbookCount = cashbooks.size();

            // Remove existing badge if present
            View existingBadge = bottomNavBinding.btnCashbookSwitch.findViewWithTag("cashbook_badge");
            if (existingBadge != null) {
                bottomNavBinding.btnCashbookSwitch.removeView(existingBadge);
            }

            if (cashbookCount > 1) {
                // Create custom badge TextView
                TextView badge = new TextView(this);
                badge.setTag("cashbook_badge");
                badge.setText(String.valueOf(cashbookCount));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(Gravity.CENTER);
                badge.setTypeface(null, Typeface.BOLD);

                // Set background color
                ShapeDrawable drawable = new ShapeDrawable(
                        new OvalShape());
                drawable.getPaint().setColor(ContextCompat.getColor(this, R.color.primary_blue));
                badge.setBackground(drawable);

                // Position badge at top-right corner
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        dpToPx(22),
                        dpToPx(22),
                        Gravity.TOP | Gravity.END);
                params.setMargins(0, dpToPx(2), dpToPx(2), 0);
                badge.setLayoutParams(params);

                // Add badge to FrameLayout
                bottomNavBinding.btnCashbookSwitch.addView(badge);

                Log.d(TAG, "Badge updated: " + cashbookCount);
            } else {
                Log.d(TAG, "Badge not shown - only one cashbook");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating cashbook badge", e);
        }
    }

    /**
     * Initialize dropdown UI components
     */
    private void initDropdownViews() {
        // These should match your layout IDs
        currentCashbookTextView = findViewById(R.id.currentCashbookText);
        cashbookDropdownContainer = findViewById(R.id.cashbookDropdownContainer);

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
            // Ensure this menu resource exists in res/menu/
            popupMenu.getMenuInflater().inflate(R.menu.menu_cashbook_dropdown, popupMenu.getMenu());

            // Animate dropdown arrow if it exists
            if (dropdownArrow != null) {
                animateDropdownArrow(true);
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_switch_cashbook) {
                    openCashbookSwitcher();
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


    private void animateDropdownArrow(boolean isOpen) {
        if (dropdownArrow == null) return;

        float rotation = isOpen ? 180f : 0f;
        dropdownArrow.animate()
                .rotation(rotation)
                .setDuration(200)
                .start();
    }

    private void updateCurrentCashbook(CashbookModel cashbook) {
        if (currentCashbookTextView != null) {
            currentCashbookTextView.setText(cashbook.getName());
        }
        if (binding.userNameTop != null) {
            binding.userNameTop.setText(cashbook.getName());
        }
    }

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

    private void setupClickListeners() {
        // Main activity buttons
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        binding.viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());

        // Keep existing userBox functionality as fallback
        binding.userBox.setOnClickListener(v -> showCashbookDropdownMenu(v));
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && !isGuest) {
            currentUserId = currentUser.getUid(); // Ensure currentUserId is set
            Log.d(TAG, "Authenticated user session started");
            loadActiveCashbookId(currentUserId);
        } else {
            Log.d(TAG, "Guest mode session started");
            handleGuestMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh cashbook badge when returning to this activity
        updateCashbookBadge();
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

    private void showGuestModeInfo() {
        showSnackbar("Guest Mode: Data will not be saved after app restart");
    }

    private void loadActiveCashbookId(String userId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id_" + userId, null);

        Log.d(TAG, "Loading cashbooks for user");
        startListeningForCashbooks(userId);
    }

    private void startListeningForCashbooks(String userId) {
        setLoadingState(true);

        // Detach any existing listener before adding a new one
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
                            cashbook.setCashbookId(snapshot.getKey()); // Standardized
                            cashbooks.add(cashbook);

                            if (cashbook.getCashbookId().equals(currentCashbookId)) { // Standardized
                                activeCashbookFound = true;
                            }
                        }
                    }

                    // If no active cashbook or it doesn't exist, use first available
                    if (!activeCashbookFound && !cashbooks.isEmpty()) {
                        currentCashbookId = cashbooks.get(0).getCashbookId(); // Standardized
                        saveActiveCashbookId(userId, currentCashbookId);
                    } else if (cashbooks.isEmpty()) {
                        setLoadingState(false);
                        showCreateFirstCashbookDialog(userId);
                        return;
                    }

                    Log.d(TAG, "Loaded " + cashbooks.size() + " cashbooks");
                    updateUserUI();
                    updateCashbookBadge(); // Make sure badge updates after loading
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

    private void startListeningForTransactions(String userId) {
        // Detach any existing listener before adding a new one
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        if (currentCashbookId == null) {
            Log.w(TAG, "currentCashbookId is null, cannot listen for transactions.");
            setLoadingState(false);
            updateTransactionTableAndSummary(); // Clear table
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

    private void updateUserUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            try {
                String cashbookName = "Default Cashbook";

                // Find current cashbook name
                for (CashbookModel cashbook : cashbooks) {
                    if (cashbook.getCashbookId().equals(currentCashbookId)) {
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

    private String getDisplayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        } else if (user.getEmail() != null) {
            return user.getEmail();
        } else {
            return "Default User";
        }
    }

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

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }


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


    @SuppressLint("SetTextI18n")
    private void addTransactionRow(TransactionModel transaction) {
        TableRow row = new TableRow(this);
        // Using table_border, as it was in one of the duplicates
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


    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        Log.d(TAG, "Navigated to settings");
    }

    private void navigateToTransactionList() {
        Intent intent = new Intent(this, TransactionActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.putExtra("isGuest", isGuest);
        startActivity(intent);

        Log.d(TAG, "Navigated to transactions list");
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, EditTransactionActivity.class);
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
        updateCashbookBadge();
    }

    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }


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

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }


    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Check if mDatabase is null, which can happen if onDestroy runs early
        if (mDatabase == null) {
            return;
        }

        String userId = (currentUser != null) ? currentUser.getUid() : null;

        if (transactionsListener != null && userId != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
            transactionsListener = null;
        }

        if (cashbooksListener != null && userId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .removeEventListener(cashbooksListener);
            cashbooksListener = null;
        }

        if (userProfileListener != null && userId != null) {
            mDatabase.child("users").child(userId)
                    .removeEventListener(userProfileListener);
            userProfileListener = null;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

