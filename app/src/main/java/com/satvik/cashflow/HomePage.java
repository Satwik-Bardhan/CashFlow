package com.satvik.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.satvik.cashflow.databinding.ActivityHomePageBinding;
import com.satvik.cashflow.databinding.ComponentBalanceCardBinding;
import com.satvik.cashflow.databinding.LayoutBottomNavigationBinding;
import com.satvik.cashflow.utils.DateTimeUtils;
import com.satvik.cashflow.utils.ErrorHandler;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";
    private static final int REQUEST_CODE_CASHBOOK_SWITCH = 1001;

    // ViewBinding
    private ActivityHomePageBinding binding;
    private ComponentBalanceCardBinding balanceCardBinding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    // UI Elements for Daily Header
    private View dailySummaryHeader;
    private TextView dailyDateText, dailyBalanceText;

    private CardView transactionListCard;
    private LinearLayout emptyStateView;

    private boolean isDailyListExpanded = true;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener, cashbooksListener;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;

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

        balanceCardBinding = binding.balanceCardView;
        bottomNavBinding = binding.bottomNavCard;

        // Initialize views
        dailySummaryHeader = findViewById(R.id.dailySummaryHeader);
        dailyDateText = findViewById(R.id.dailyDateText);
        dailyBalanceText = findViewById(R.id.dailyBalanceText);

        transactionListCard = findViewById(R.id.transactionListCard);
        emptyStateView = findViewById(R.id.emptyStateView);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found. Redirecting to login.");
            signOutUser();
            return;
        }

        currentUserId = currentUser.getUid();
        userRef = mDatabase.child("users").child(currentUserId);
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        currentCashbookId = getIntent().getStringExtra("cashbook_id");

        Log.d(TAG, "HomePage started for user: " + currentUserId);

        setupUI();
        setupClickListeners();
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        bottomNavBinding.btnHome.setSelected(true);

        bottomNavBinding.btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        bottomNavBinding.btnCashbookSwitch.setOnClickListener(v -> openCashbookSwitcher());

        bottomNavBinding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("cashbook_id", currentCashbookId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void openCashbookSwitcher() {
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

                if (!activeCashbookFound && !cashbooks.isEmpty()) {
                    currentCashbookId = cashbooks.get(0).getCashbookId();
                    saveActiveCashbookId(currentCashbookId);
                } else if (cashbooks.isEmpty()) {
                    setLoadingState(false);
                    showCreateFirstCashbookDialog();
                    return;
                }

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

    private void setupUI() {
        setLoadingState(false);
        binding.cashInButton.setContentDescription("Add cash in transaction");
        binding.cashOutButton.setContentDescription("Add cash out transaction");
        binding.userBox.setContentDescription("User information and cashbook selector");
        // [FIX] Removed viewFullTransactionsButton description
    }

    private void setupClickListeners() {
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));

        // [FIX] Removed viewFullTransactionsButton listener

        // Toggle Daily List visibility when clicking the header
        if (dailySummaryHeader != null) {
            dailySummaryHeader.setOnClickListener(v -> toggleDailyList());
        }
    }

    private void toggleDailyList() {
        isDailyListExpanded = !isDailyListExpanded;
        if (isDailyListExpanded) {
            if (transactionListCard != null) transactionListCard.setVisibility(View.VISIBLE);
        } else {
            if (transactionListCard != null) transactionListCard.setVisibility(View.GONE);
        }
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
        loadCashbooksForBadge();
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
                long lastModified = System.currentTimeMillis();

                for (CashbookModel cashbook : cashbooks) {
                    if (cashbook.getCashbookId().equals(currentCashbookId)) {
                        cashbookName = cashbook.getName();
                        lastModified = cashbook.getLastModified();
                        break;
                    }
                }

                binding.userNameTop.setText(cashbookName);

                if (binding.lastOpenedText != null) {
                    String timeSpan = DateTimeUtils.getRelativeTimeSpan(lastModified);
                    binding.lastOpenedText.setText("Last opened: " + timeSpan);
                }

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
            binding.transactionTable.removeAllViews();

            double globalTotalIncome = 0, globalTotalExpense = 0;
            for (TransactionModel transaction : allTransactions) {
                if ("IN".equalsIgnoreCase(transaction.getType())) {
                    globalTotalIncome += transaction.getAmount();
                } else {
                    globalTotalExpense += transaction.getAmount();
                }
            }
            double globalBalance = globalTotalIncome - globalTotalExpense;

            double todayIncome = 0, todayExpense = 0;
            List<TransactionModel> todaysTransactions = new ArrayList<>();

            for (TransactionModel transaction : allTransactions) {
                if (isToday(transaction.getTimestamp())) {
                    todaysTransactions.add(transaction);
                    if ("IN".equalsIgnoreCase(transaction.getType())) {
                        todayIncome += transaction.getAmount();
                    } else {
                        todayExpense += transaction.getAmount();
                    }
                }
            }

            double todayBalance = todayIncome - todayExpense;

            balanceCardBinding.balanceText.setText(formatCurrency(globalBalance));
            balanceCardBinding.moneyIn.setText(formatCurrency(globalTotalIncome));
            balanceCardBinding.moneyOut.setText(formatCurrency(globalTotalExpense));
            balanceCardBinding.balanceText.setTextColor(Color.WHITE);

            if (dailyDateText != null) {
                dailyDateText.setText(DateTimeUtils.formatDate(System.currentTimeMillis(), "dd MMM yyyy"));
            }

            if (dailyBalanceText != null) {
                String sign = todayBalance >= 0 ? "+ " : "- ";
                dailyBalanceText.setText(sign + formatCurrency(Math.abs(todayBalance)));
                if (todayBalance >= 0) {
                    dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.incomeColor));
                } else {
                    dailyBalanceText.setTextColor(ThemeUtil.getThemeAttrColor(this, R.attr.expenseColor));
                }
            }

            if (todaysTransactions.isEmpty()) {
                if (emptyStateView != null) emptyStateView.setVisibility(View.VISIBLE);
                binding.transactionTable.setVisibility(View.GONE);
            } else {
                if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
                binding.transactionTable.setVisibility(View.VISIBLE);

                for (TransactionModel transaction : todaysTransactions) {
                    addTransactionRow(transaction);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating transaction table", e);
        }
    }

    private boolean isToday(long timestamp) {
        Calendar transactionCal = Calendar.getInstance();
        transactionCal.setTimeInMillis(timestamp);

        Calendar todayCal = Calendar.getInstance();

        return transactionCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                transactionCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR);
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    private void addTransactionRow(TransactionModel transaction) {
        if (binding == null) return;

        View rowView = getLayoutInflater().inflate(R.layout.item_transaction_row_daily, binding.transactionTable, false);

        TextView rowCategory = rowView.findViewById(R.id.rowCategory);
        TextView rowMode = rowView.findViewById(R.id.rowMode);
        TextView rowIn = rowView.findViewById(R.id.rowIn);
        TextView rowOut = rowView.findViewById(R.id.rowOut);

        rowCategory.setText(transaction.getTransactionCategory());
        rowMode.setText(transaction.getPaymentMode());

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            rowIn.setText(formatCurrency(transaction.getAmount()));
            rowOut.setText("-");
        } else {
            rowIn.setText("-");
            rowOut.setText(formatCurrency(transaction.getAmount()));
        }

        rowView.setOnClickListener(v -> openTransactionDetail(transaction));

        binding.transactionTable.addView(rowView);
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
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
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
                showCreateFirstCashbookDialog();
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

        if (transactionsListener != null && currentCashbookId != null && userRef != null) {
            userRef.child("cashbooks").child(currentCashbookId).child("transactions")
                    .removeEventListener(transactionsListener);
        }

        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentCashbookId);

        updateUserUI();
        startListeningForTransactions();
    }

    private void saveActiveCashbookId(String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + currentUserId, cashbookId).apply();
    }

    private void setLoadingState(boolean loading) {
        if (binding == null) return;
        isLoading = loading;
        binding.cashInButton.setEnabled(!loading);
        binding.cashOutButton.setEnabled(!loading);
        binding.userBox.setEnabled(!loading);
    }

    private void showSnackbar(String message) {
        if (binding != null) {
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

    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}