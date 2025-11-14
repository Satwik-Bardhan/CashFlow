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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
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

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int MAX_VISIBLE_TRANSACTIONS = 5;
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // ViewBinding
    private ActivityHomePageBinding binding;
    private ComponentBalanceCardBinding balanceCardBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    // [FIX] Removed dropdownArrow, it was causing a crash as it doesn't exist in the XML
    // private ImageView dropdownArrow;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener, cashbooksListener;

    // Data
    private ArrayList<TransactionModel> allTransactions = new ArrayList<>();
    private List<CashbookModel> cashbooks = new ArrayList<>();

    // State
    private String currentCashbookId;
    private String currentUserId;
    private boolean isLoading = false;

    // Utils
    private NumberFormat currencyFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize child bindings
        balanceCardBinding = binding.balanceCardView;
        bottomNavBinding = binding.bottomNavCard;

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found. Redirecting to login.");
            signOutUser(); // This clears any state and navigates to Signin
            return;
        }

        currentUserId = currentUser.getUid();
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        Log.d(TAG, "HomePage started for user: " + currentUserId);

        setupUI();
        setupClickListeners();
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        bottomNavBinding.btnHome.setSelected(true);
        bottomNavBinding.btnTransactions.setOnClickListener(v -> navigateToTransactionList());
        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());
        bottomNavBinding.btnSettings.setOnClickListener(v -> navigateToSettings());
    }

    private void openCashbookSwitcher() {
        if (currentUserId == null) {
            showSnackbar("Please log in first");
            return;
        }

        Intent intent = new Intent(this, CashbookSwitchActivity.class);
        intent.putExtra("current_cashbook_id", currentCashbookId);
        startActivityForResult(intent, REQUEST_CODE_CASHBOOK_SWITCH);
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
            }
        }
    }

    // [FIX] This entire badge system was missing from your original HomePage.java
    // I've ported it over from your SettingsActivity.java to ensure consistency.
    private void loadCashbooksForBadge() {
        if (currentUserId == null) return;
        if (cashbooksListener != null) {
            mDatabase.child("users").child(currentUserId).child("cashbooks").removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbooks.clear();
                boolean activeCashbookFound = false;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        cashbook.setCashbookId(snapshot.getKey());
                        cashbooks.add(cashbook);
                        if (cashbook.getCashbookId().equals(currentCashbookId)) {
                            activeCashbookFound = true;
                        }
                    }
                }

                // This logic was in your original loadCashbooks, it's important
                if (!activeCashbookFound && !cashbooks.isEmpty()) {
                    currentCashbookId = cashbooks.get(0).getCashbookId();
                    saveActiveCashbookId(currentUserId, currentCashbookId);
                } else if (cashbooks.isEmpty()) {
                    setLoadingState(false);
                    showCreateFirstCashbookDialog(currentUserId);
                    return;
                }

                updateCashbookBadge();

                // Now that we have the correct cashbook ID, load transactions
                updateUserUI();
                startListeningForTransactions(currentUserId);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks for badge", databaseError.toException());
                setLoadingState(false);
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };
        mDatabase.child("users").child(currentUserId).child("cashbooks").addValueEventListener(cashbooksListener);
    }

    @SuppressLint("SetTextI18n")
    private void updateCashbookBadge() {
        if (bottomNavBinding == null || bottomNavBinding.btnCashbookSwitch == null) return;
        try {
            int cashbookCount = cashbooks.size();
            View existingBadge = bottomNavBinding.btnCashbookSwitch.findViewWithTag("cashbook_badge");
            if (existingBadge != null) {
                bottomNavBinding.btnCashbookSwitch.removeView(existingBadge);
            }

            if (cashbookCount > 1) {
                TextView badge = new TextView(this);
                badge.setTag("cashbook_badge");
                badge.setText(String.valueOf(cashbookCount));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(Gravity.CENTER);
                badge.setTypeface(null, Typeface.BOLD);

                ShapeDrawable drawable = new ShapeDrawable(new OvalShape());
                drawable.getPaint().setColor(ContextCompat.getColor(this, R.color.primary_blue));
                badge.setBackground(drawable);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(22), dpToPx(22), Gravity.TOP | Gravity.END);
                params.setMargins(0, dpToPx(2), dpToPx(2), 0);
                badge.setLayoutParams(params);

                bottomNavBinding.btnCashbookSwitch.addView(badge);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating cashbook badge", e);
        }
    }

    private void showCashbookDropdownMenu(View anchorView) {
        if (currentUserId == null) {
            showSnackbar("Not logged in.");
            return;
        }

        try {
            PopupMenu popupMenu = new PopupMenu(this, anchorView, Gravity.END);
            popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

            // [FIX] Removed logic for animating non-existent dropdownArrow
            // if (dropdownArrow != null) animateDropdownArrow(true);

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
                    showSignOutConfirmation(); // [FIX] Added confirmation dialog
                    return true;
                }
                return false;
            });

            // [FIX] Removed listener for non-existent dropdownArrow
            // popupMenu.setOnDismissListener(menu -> {
            //     if (dropdownArrow != null) animateDropdownArrow(false);
            // });

            popupMenu.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing dropdown menu", e);
            showSnackbar("Error showing menu options");
        }
    }

    // [FIX] Added confirmation for signing out
    private void showSignOutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> signOutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupUI() {
        setLoadingState(false);
        binding.cashInButton.setContentDescription("Add cash in transaction");
        binding.cashOutButton.setContentDescription("Add cash out transaction");
        binding.userBox.setContentDescription("User information and cashbook selector");
        binding.viewFullTransactionsButton.setContentDescription("View all transactions");
    }

    private void setupClickListeners() {
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        binding.viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());

        // [FIX] Make sure click listener is set on the correct views
        binding.userBox.setOnClickListener(this::showCashbookDropdownMenu);
        if (binding.userDropdownIcon != null) {
            binding.userDropdownIcon.setOnClickListener(this::showCashbookDropdownMenu);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null) {
            loadActiveCashbookId(currentUserId);
        } else {
            Log.e(TAG, "User is not logged in. Redirecting to Signin.");
            signOutUser();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // [FIX] Refresh badge when returning to activity
        if (!isGuest) {
            loadCashbooksForBadge();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeFirebaseListeners();
        binding = null;
        balanceCardBinding = null;
        bottomNavBinding = null;
    }

    private void signOutUser() {
        mAuth.signOut();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadActiveCashbookId(String userId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        if (currentCashbookId == null) {
            currentCashbookId = prefs.getString("active_cashbook_id_" + userId, null);
        }
        // [FIX] Call the correct method
        loadCashbooksForBadge();
    }

    // [FIX] Renamed this method as it's now part of the loadCashbooksForBadge flow
    // private void startListeningForCashbooks(String userId) { ... }

    private void startListeningForTransactions(String userId) {
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        if (currentCashbookId == null) {
            setLoadingState(false);
            allTransactions.clear();
            updateTransactionTableAndSummary();
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
                    Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
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
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        });
    }

    private void updateUserUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && binding != null) { // [FIX] Check if binding is null
            try {
                String cashbookName = "My Cashbook";
                for (CashbookModel cashbook : cashbooks) {
                    if (cashbook.getCashbookId().equals(currentCashbookId)) {
                        cashbookName = cashbook.getName();
                        break;
                    }
                }
                binding.userNameTop.setText(cashbookName);
                if (binding.currentCashbookText != null) {
                    binding.currentCashbookText.setText(cashbookName);
                }
                balanceCardBinding.uidText.setText("UID: " + currentUser.getUid().substring(0, 8) + "...");
                balanceCardBinding.userNameBottom.setText(getDisplayName(currentUser));
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI", e);
            }
        }
    }

    private String getDisplayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        } else if (user.getEmail() != null) {
            return user.getEmail();
        }
        return "CashFlow User";
    }

    @SuppressLint("SetTextI18n")
    private void updateTransactionTableAndSummary() {
        if (binding == null) return; // [FIX] Add null check for binding
        try {
            if (binding.transactionTable.getChildCount() > 1) {
                binding.transactionTable.removeViews(1, binding.transactionTable.getChildCount() - 1);
            }

            double totalIncome = 0, totalExpense = 0;
            for (TransactionModel transaction : allTransactions) {
                if ("IN".equalsIgnoreCase(transaction.getType())) {
                    totalIncome += transaction.getAmount();
                } else {
                    totalExpense += transaction.getAmount();
                }
            }
            double balance = totalIncome - totalExpense;

            balanceCardBinding.balanceText.setText(formatCurrency(balance));
            balanceCardBinding.moneyIn.setText(formatCurrency(totalIncome));
            balanceCardBinding.moneyOut.setText(formatCurrency(totalExpense));

            if (balance >= 0) {
                balanceCardBinding.balanceText.setTextColor(ContextCompat.getColor(this, R.color.income_green));
            } else {
                balanceCardBinding.balanceText.setTextColor(ContextCompat.getColor(this, R.color.expense_red));
            }

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
        }
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    private void addNoTransactionsRow() {
        if (binding == null) return;
        TableRow row = new TableRow(this);
        row.setPadding(0, 16, 0, 16);
        TextView noDataView = new TextView(this);
        noDataView.setText("No transactions yet");
        // [FIX] Use theme attribute for color
        noDataView.setTextColor(ContextCompat.getColor(this, R.color.textColorSecondary));
        noDataView.setTextSize(14);
        noDataView.setGravity(Gravity.CENTER);
        noDataView.setPadding(16, 16, 16, 16);
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.span = 4;
        noDataView.setLayoutParams(params);
        row.addView(noDataView);
        binding.transactionTable.addView(row);
    }

    private void addViewMoreRow(int remainingCount) {
        if (binding == null) return;
        TableRow row = new TableRow(this);
        row.setPadding(0, 8, 0, 8);
        TextView viewMoreView = new TextView(this);
        viewMoreView.setText("+" + remainingCount + " more transactions");
        viewMoreView.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
        viewMoreView.setTextSize(12);
        viewMoreView.setGravity(Gravity.CENTER);
        viewMoreView.setTypeface(null, Typeface.ITALIC);
        viewMoreView.setPadding(8, 8, 8, 8);
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.span = 4;
        viewMoreView.setLayoutParams(params);
        row.addView(viewMoreView);
        row.setOnClickListener(v -> navigateToTransactionList());
        binding.transactionTable.addView(row);
    }

    private void addTransactionRow(TransactionModel transaction) {
        if (binding == null) return;
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);

        TextView entryView = createTableCell(transaction.getTransactionCategory(), 2f, Typeface.NORMAL, Gravity.START);
        TextView modeView = createTableCell(transaction.getPaymentMode(), 1f, Typeface.NORMAL, Gravity.CENTER);
        TextView inView = createTableCell("IN".equalsIgnoreCase(transaction.getType()) ? formatCurrency(transaction.getAmount()) : "-", 1f, Typeface.NORMAL, Gravity.CENTER);
        TextView outView = createTableCell("OUT".equalsIgnoreCase(transaction.getType()) ? formatCurrency(transaction.getAmount()) : "-", 1f, Typeface.NORMAL, Gravity.CENTER);

        modeView.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
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
        textView.setText(text != null ? text : "");
        textView.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));
        textView.setBackgroundResource(R.drawable.table_cell_border);
        TableRow.LayoutParams params = new TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, weight);
        textView.setLayoutParams(params);
        // [FIX] Use theme attribute for color
        textView.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
        textView.setTypeface(null, style);
        textView.setGravity(gravity);
        textView.setTextSize(14);
        return textView;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, EditTransactionActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void navigateToTransactionList() {
        Intent intent = new Intent(this, TransactionActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void openCashInOutActivity(String type) {
        if (currentCashbookId == null) {
            showSnackbar("Please create a cashbook first");
            return;
        }
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra("transaction_type", type);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void showCreateFirstCashbookDialog(String userId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Welcome to CashFlow");
        builder.setMessage("Create your first cashbook to get started!");
        final EditText input = new EditText(this);
        input.setHint("e.g., My Main Cashbook");
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
        input.setHint("e.g., Family Expenses");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void createNewCashbook(String name) {
        if (currentUserId == null) return;
        setLoadingState(true);
        DatabaseReference cashbooksRef = mDatabase.child("users").child(currentUserId).child("cashbooks");
        String cashbookId = cashbooksRef.push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            cashbooksRef.child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        showSnackbar("Cashbook '" + name + "' created");
                        switchCashbook(cashbookId);
                        setLoadingState(false);
                    })
                    .addOnFailureListener(e -> {
                        showSnackbar("Failed to create cashbook");
                        setLoadingState(false);
                    });
        }
    }

    private void switchCashbook(String newCashbookId) {
        if (currentUserId == null) return;
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(currentUserId).child("cashbooks")
                    .child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }
        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentUserId, currentCashbookId);
        updateUserUI();
        startListeningForTransactions(currentUserId);
    }

    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }

    private void setLoadingState(boolean loading) {
        if (binding == null) return; // [FIX] Add null check
        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.viewFullTransactionsButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);
    }

    private void showSnackbar(String message) {
        if (binding != null) { // [FIX] Add null check
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void removeFirebaseListeners() {
        if (mDatabase == null || currentUserId == null) return;
        try {
            if (transactionsListener != null && currentCashbookId != null) {
                mDatabase.child("users").child(currentUserId).child("cashbooks")
                        .child(currentCashbookId).child("transactions")
                        .removeEventListener(transactionsListener);
                transactionsListener = null;
            }
            if (cashbooksListener != null) {
                mDatabase.child("users").child(currentUserId).child("cashbooks")
                        .removeEventListener(cashbooksListener);
                cashbooksListener = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing listeners", e);
        }
    }
}