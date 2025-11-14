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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
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

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener, cashbooksListener;
    private FirebaseUser currentUser; // [FIX] Added
    private DatabaseReference userRef; // [FIX] Added

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

        // Initialize ViewBinding
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
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found. Redirecting to login.");
            signOutUser(); // This clears any state and navigates to Signin
            return; // Stop further execution of onCreate
        }

        currentUserId = currentUser.getUid();
        userRef = mDatabase.child("users").child(currentUserId); // [FIX] Set userRef
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
    private void loadCashbooksForBadge() {
        if (userRef == null) return;
        if (cashbooksListener != null) {
            userRef.child("cashbooks").removeEventListener(cashbooksListener);
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

                // This logic ensures a valid cashbook is always selected
                if (!activeCashbookFound && !cashbooks.isEmpty()) {
                    currentCashbookId = cashbooks.get(0).getCashbookId();
                    saveActiveCashbookId(currentCashbookId);
                } else if (cashbooks.isEmpty()) {
                    setLoadingState(false);
                    showCreateFirstCashbookDialog();
                    return;
                }

                updateCashbookBadge();

                // Now that we have the correct cashbook ID, load transactions
                updateUserUI();
                startListeningForTransactions();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load cashbooks for badge", databaseError.toException());
                setLoadingState(false);
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };
        userRef.child("cashbooks").addValueEventListener(cashbooksListener);
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
                drawable.getPaint().setColor(ThemeUtil.getThemeAttrColor(this, R.attr.balanceColor));
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
            // [FIX] Use correct menu file
            popupMenu.getMenuInflater().inflate(R.menu.menu_cashbook_item, popupMenu.getMenu());

            // [FIX] Animate the correct icon
            animateDropdownArrow(binding.userDropdownIcon, true);

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
                    showSignOutConfirmation(); // [FIX] Added confirmation
                    return true;
                }
                return false;
            });

            popupMenu.setOnDismissListener(menu -> {
                animateDropdownArrow(binding.userDropdownIcon, false);
            });

            popupMenu.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing dropdown menu", e);
            showSnackbar("Error showing menu options");
        }
    }

    private void animateDropdownArrow(View view, boolean isOpen) {
        if (view == null) return;
        float rotation = isOpen ? 180f : 0f;
        view.animate().rotation(rotation).setDuration(200).start();
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
        binding.userBox.setOnClickListener(this::showCashbookDropdownMenu);
        binding.userDropdownIcon.setOnClickListener(this::showCashbookDropdownMenu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null) {
            loadActiveCashbookId();
        } else {
            Log.e(TAG, "User is not logged in. Redirecting to Signin.");
            signOutUser();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // [FIX] Refresh badge when returning to activity
        if (currentUserId != null) {
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

    private void showSignOutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> signOutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void signOutUser() {
        mAuth.signOut();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadActiveCashbookId() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        if (currentCashbookId == null) {
            currentCashbookId = prefs.getString("active_cashbook_id_" + currentUserId, null);
        }
        loadCashbooksForBadge(); // This will load cashbooks, then transactions
    }

    private void startListeningForTransactions() {
        if (transactionsListener != null && currentCashbookId != null && userRef != null) {
            userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        if (currentCashbookId == null) {
            setLoadingState(false);
            allTransactions.clear();
            updateTransactionTableAndSummary();
            return;
        }

        DatabaseReference transactionsRef = userRef.child("cashbooks")
                .child(currentCashbookId).child("transactions");

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
        if (currentUser != null && binding != null) {
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
                balanceCardBinding.uidText.setText("UID: " + currentUserId.substring(0, 8) + "...");
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
        if (binding == null) return;
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

            int incomeColor = ThemeUtil.getThemeAttrColor(this, R.attr.incomeColor);
            int expenseColor = ThemeUtil.getThemeAttrColor(this, R.attr.expenseColor);

            balanceCardBinding.balanceText.setTextColor(balance >= 0 ? incomeColor : expenseColor);
            balanceCardBinding.moneyIn.setTextColor(incomeColor);
            balanceCardBinding.moneyOut.setTextColor(expenseColor);

            if (allTransactions.isEmpty()) {
                addNoTransactionsRow();
            } else {
                int limit = Math.min(allTransactions.size(), MAX_VISIBLE_TRANSACTIONS);
                binding.transactionCount.setText("TRANSACTIONS (" + allTransactions.size() + ")");
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
        noDataView.setText(R.string.msg_no_transactions);
        noDataView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.textColorSecondary));
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
        viewMoreView.setText(getString(R.string.view_more_transactions, remainingCount));
        viewMoreView.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.balanceColor));
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
        TableRow row = (TableRow) getLayoutInflater().inflate(R.layout.item_transaction_row, binding.transactionTable, false);

        TextView entryView = row.findViewById(R.id.table_cell_entry);
        TextView modeView = row.findViewById(R.id.table_cell_mode);
        TextView inView = row.findViewById(R.id.table_cell_in);
        TextView outView = row.findViewById(R.id.table_cell_out);

        entryView.setText(transaction.getTransactionCategory());
        modeView.setText(transaction.getPaymentMode());

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            inView.setText(formatCurrency(transaction.getAmount()));
            outView.setText("-");
        } else {
            inView.setText("-");
            outView.setText(formatCurrency(transaction.getAmount()));
        }

        row.setOnClickListener(v -> openTransactionDetail(transaction));
        binding.transactionTable.addView(row);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void openTransactionDetail(TransactionModel transaction) {
        Intent intent = new Intent(this, EditTransactionActivity.class);
        intent.putExtra("transaction_model", (Serializable) transaction);
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

    private void showCreateFirstCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_welcome_cashflow);
        builder.setMessage(R.string.msg_create_first_cashbook);
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_cashbook_name);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // Add padding to the EditText
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);

        builder.setPositiveButton(R.string.btn_create, (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                showSnackbar(getString(R.string.error_enter_cashbook_name));
                showCreateFirstCashbookDialog(); // Show again
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void showCreateNewCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_create_cashbook);
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_new_cashbook_name);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // Add padding to the EditText
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);
        builder.setPositiveButton(R.string.btn_create, (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private void createNewCashbook(String name) {
        if (currentUserId == null || userRef == null) return;
        setLoadingState(true);
        DatabaseReference cashbooksRef = userRef.child("cashbooks");
        String cashbookId = cashbooksRef.push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            newCashbook.setUserId(currentUserId);
            cashbooksRef.child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        showSnackbar("Cashbook '" + name + "' created");
                        switchCashbook(cashbookId); // [FIX] Switch to the new book
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

        // Detach old listener
        if (transactionsListener != null && currentCashbookId != null && userRef != null) {
            userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentCashbookId);

        // Re-load UI and attach new listener
        updateUserUI();
        startListeningForTransactions();
    }

    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUserId, cashbookId).apply();
    }

    private void setLoadingState(boolean loading) {
        if (binding == null) return; // [FIX] Add null check
        isLoading = loading;
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
        if (userRef == null) return;
        try {
            if (transactionsListener != null && currentCashbookId != null) {
                userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                        .removeEventListener(transactionsListener);
                transactionsListener = null;
            }
            if (cashbooksListener != null) {
                userRef.child("cashbooks").removeEventListener(cashbooksListener);
                cashbooksListener = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing listeners", e);
        }
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