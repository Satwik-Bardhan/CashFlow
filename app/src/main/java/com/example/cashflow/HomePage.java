package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";

    // NEW: ViewBinding declarations
    private ActivityHomePageBinding binding;
    private LayoutBottomNavigationBinding bottomNavBinding;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener, userProfileListener, cashbooksListener;

    private ArrayList<TransactionModel> allTransactions = new ArrayList<>();
    private List<CashbookModel> cashbooks = new ArrayList<>();
    private String currentCashbookId;
    private AlertDialog cashbookDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NEW: Initialize ViewBinding
        binding = ActivityHomePageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // NEW: Initialize bottom navigation binding from included layout
        bottomNavBinding = LayoutBottomNavigationBinding.bind(binding.bottomNavBar.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        setupClickListeners();

        // NEW: Access bottom navigation through bottomNavBinding
        bottomNavBinding.btnHome.setSelected(true);

        if (getIntent().getBooleanExtra("isGuest", false)) {
            handleGuestMode();
        }
    }

    private void setupClickListeners() {
        // NEW: Main activity views through binding
        binding.cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        binding.cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        binding.viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());
        binding.userBox.setOnClickListener(v -> showUserDropdown());

        // NEW: Bottom navigation views through bottomNavBinding
        bottomNavBinding.btnHome.setOnClickListener(v -> Toast.makeText(HomePage.this, "Already on Home", Toast.LENGTH_SHORT).show());
        bottomNavBinding.btnTransactions.setOnClickListener(v -> navigateToTransactionList());
        bottomNavBinding.btnSettings.setOnClickListener(v -> startActivity(new Intent(HomePage.this, SettingsActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !getIntent().getBooleanExtra("isGuest", false)) {
            loadActiveCashbookId(currentUser.getUid());
        } else {
            handleGuestMode();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFirebaseListeners();
    }

    // NEW: Critical memory leak prevention
    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeFirebaseListeners();
        binding = null;
        bottomNavBinding = null; // Also clean up bottom nav binding
    }

    private void handleGuestMode() {
        allTransactions.clear();
        updateTransactionTableAndSummary();

        // NEW: Using binding for all UI updates (note: dateToday, not dateTodayText)
        binding.userNameTop.setText("Guest User");
        binding.uidText.setText("UID: GUEST");
        binding.userNameBottom.setText("Guest User");
        binding.dateToday.setText("Last Open: Guest Session");
    }

    private void loadActiveCashbookId(String userId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id_" + userId, null);
        startListeningForCashbooks(userId);
    }

    private void startListeningForCashbooks(String userId) {
        // NEW: Properly remove existing listener before adding new one
        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks").removeEventListener(cashbooksListener);
        }

        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
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
                    showCreateFirstCashbookDialog(userId);
                    return;
                }
                updateUserUI();
                startListeningForTransactions(userId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // NEW: Proper error handling
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        };
        mDatabase.child("users").child(userId).child("cashbooks").addValueEventListener(cashbooksListener);
    }

    private void startListeningForTransactions(String userId) {
        // NEW: Properly remove existing listener before adding new one
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
        }
        if (currentCashbookId == null) return;

        DatabaseReference transactionsRef = mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions");
        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // NEW: Proper error handling
                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void updateUserUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String cashbookName = "My Cashbook"; // Default
            for (CashbookModel cashbook : cashbooks) {
                if (cashbook.getId().equals(currentCashbookId)) {
                    cashbookName = cashbook.getName();
                    break;
                }
            }
            // NEW: Using binding with correct field names from XML
            binding.userNameTop.setText(cashbookName);
            binding.userNameBottom.setText(currentUser.getEmail());
            binding.uidText.setText("UID: " + currentUser.getUid());
            binding.dateToday.setText("Last Open: " + DateFormat.getDateTimeInstance().format(new Date()));
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateTransactionTableAndSummary() {
        // NEW: Using binding for TableLayout access
        // Clear previous rows except the header
        if (binding.transactionTable.getChildCount() > 2) {
            binding.transactionTable.removeViews(2, binding.transactionTable.getChildCount() - 2);
        }

        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel transaction : allTransactions) {
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else {
                totalExpense += transaction.getAmount();
            }
        }

        // NEW: Using binding for all TextView updates (note: moneyIn/moneyOut, not moneyInText/moneyOutText)
        binding.balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
        binding.moneyIn.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        binding.moneyOut.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));

        if (allTransactions.isEmpty()) {
            // Optionally display a "No transactions" message in the table
        } else {
            // Display a limited number of recent transactions
            int limit = Math.min(allTransactions.size(), 5); // Show up to 5 transactions
            for (int i = 0; i < limit; i++) {
                addTransactionRow(allTransactions.get(i));
            }
        }
    }

    private void addTransactionRow(TransactionModel transaction) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);
        row.setPadding(0, 4, 0, 4);

        TextView entryView = createTableCell(transaction.getTransactionCategory(), 2f, Typeface.BOLD);
        TextView modeView = createTableCell(transaction.getPaymentMode(), 1f, Typeface.NORMAL);
        TextView outView = createTableCell("OUT".equalsIgnoreCase(transaction.getType()) ? "₹" + String.format(Locale.US, "%.2f", transaction.getAmount()) : "-", 1f, Typeface.NORMAL);
        TextView inView = createTableCell("IN".equalsIgnoreCase(transaction.getType()) ? "₹" + String.format(Locale.US, "%.2f", transaction.getAmount()) : "-", 1f, Typeface.NORMAL);

        modeView.setTextColor(ContextCompat.getColor(this, R.color.balance_blue));
        outView.setTextColor(ContextCompat.getColor(this, R.color.expense_red));
        inView.setTextColor(ContextCompat.getColor(this, R.color.income_green));

        row.addView(entryView);
        row.addView(modeView);
        row.addView(outView);
        row.addView(inView);

        // NEW: Using binding for table access
        binding.transactionTable.addView(row);
    }

    private TextView createTableCell(String text, float weight, int style) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setPadding(8, 8, 8, 8);
        textView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight));
        textView.setTextColor(Color.BLACK);
        textView.setTypeface(null, style);
        textView.setGravity(Gravity.CENTER);
        return textView;
    }

    private void openCashInOutActivity(String type) {
        Intent intent = new Intent(this, CashInOutActivity.class);
        intent.putExtra("transaction_type", type);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    private void navigateToTransactionList() {
        Intent intent = new Intent(this, TransactionActivity.class);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }

    // NEW: Enhanced Firebase listener cleanup
    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        // Remove transactions listener
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
            transactionsListener = null;
        }

        // Remove cashbooks listener
        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks").removeEventListener(cashbooksListener);
            cashbooksListener = null;
        }

        // Remove user profile listener
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
            userProfileListener = null;
        }
    }

    private void showCreateFirstCashbookDialog(String userId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create Your First Cashbook");
        final EditText input = new EditText(this);
        input.setHint("e.g., My Main Book");
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void showUserDropdown() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_cashbook_switcher, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        ListView listView = dialogView.findViewById(R.id.cashbookListView);
        Button addNewButton = dialogView.findViewById(R.id.addNewButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        cashbookDialog = builder.create();

        CashbookAdapter adapter = new CashbookAdapter(this, cashbooks, currentUser.getUid(), cashbookDialog);
        listView.setAdapter(adapter);

        addNewButton.setOnClickListener(v -> {
            showCreateNewCashbookDialog();
            cashbookDialog.dismiss();
        });

        cancelButton.setOnClickListener(v -> cashbookDialog.dismiss());

        cashbookDialog.show();
    }

    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }

    private void createNewCashbook(String name) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        DatabaseReference cashbooksRef = mDatabase.child("users").child(userId).child("cashbooks");
        String cashbookId = cashbooksRef.push().getKey();

        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            cashbooksRef.child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(HomePage.this, "Cashbook '" + name + "' created!", Toast.LENGTH_SHORT).show();
                        switchCashbook(cashbookId);
                    })
                    .addOnFailureListener(e -> {
                        // NEW: Proper error handling
                        ErrorHandler.handleExportError(HomePage.this, e);
                    });
        }
    }

    private void switchCashbook(String newCashbookId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
        }

        currentCashbookId = newCashbookId;
        saveActiveCashbookId(currentUser.getUid(), currentCashbookId);
        updateUserUI();
        startListeningForTransactions(currentUser.getUid());
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
                Toast.makeText(HomePage.this, "Cashbook name cannot be empty.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // Keep all your existing inner classes and methods (CashbookAdapter, etc.)
    private class CashbookAdapter extends ArrayAdapter<CashbookModel> {
        private AlertDialog dialog;

        public CashbookAdapter(Context context, List<CashbookModel> cashbooks, String userId, AlertDialog dialog) {
            super(context, 0, cashbooks);
            this.dialog = dialog;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_cashbook, parent, false);
            }

            CashbookModel cashbook = getItem(position);
            TextView nameTextView = convertView.findViewById(R.id.cashbookNameTextView);
            ImageView optionsMenu = convertView.findViewById(R.id.optionsMenuButton);

            String displayName = cashbook.getName();
            if (cashbook.getId().equals(currentCashbookId)) {
                displayName += " (Active)";
                nameTextView.setTypeface(null, Typeface.BOLD);
            } else {
                nameTextView.setTypeface(null, Typeface.NORMAL);
            }
            nameTextView.setText(displayName);

            convertView.setOnClickListener(v -> {
                if (!cashbook.getId().equals(currentCashbookId)) {
                    switchCashbook(cashbook.getId());
                }
                if (dialog != null) {
                    dialog.dismiss();
                }
            });

            optionsMenu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(getContext(), v);
                popup.getMenuInflater().inflate(R.menu.cashbook_options_menu, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.menu_rename) {
                        showRenameDialog(cashbook);
                        dialog.dismiss();
                        return true;
                    } else if (itemId == R.id.menu_delete) {
                        showDeleteDialog(cashbook);
                        dialog.dismiss();
                        return true;
                    } else if (itemId == R.id.menu_duplicate) {
                        duplicateCashbook(cashbook);
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                });
                popup.show();
            });

            return convertView;
        }
    }

    private void showRenameDialog(CashbookModel cashbook) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Cashbook");

        final EditText input = new EditText(this);
        input.setText(cashbook.getName());
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(cashbook.getName())) {
                mDatabase.child("users").child(currentUser.getUid()).child("cashbooks")
                        .child(cashbook.getId()).child("name").setValue(newName)
                        .addOnFailureListener(e -> ErrorHandler.handleExportError(HomePage.this, e));
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteDialog(CashbookModel cashbook) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        if (cashbooks.size() <= 1) {
            Toast.makeText(this, "You cannot delete your only cashbook.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Cashbook")
                .setMessage("Are you sure you want to delete '" + cashbook.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mDatabase.child("users").child(currentUser.getUid()).child("cashbooks")
                            .child(cashbook.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                if (cashbook.getId().equals(currentCashbookId)) {
                                    // Switch to the first available cashbook
                                    if (!cashbooks.isEmpty()) {
                                        switchCashbook(cashbooks.get(0).getId());
                                    }
                                }
                            })
                            .addOnFailureListener(e -> ErrorHandler.handleExportError(HomePage.this, e));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void duplicateCashbook(CashbookModel cashbookToDuplicate) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        DatabaseReference cashbooksRef = mDatabase.child("users").child(userId).child("cashbooks");
        String newCashbookId = cashbooksRef.push().getKey();

        if (newCashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(newCashbookId, cashbookToDuplicate.getName() + " (Copy)");
            cashbooksRef.child(newCashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        DatabaseReference oldTransactionsRef = cashbooksRef.child(cashbookToDuplicate.getId()).child("transactions");
                        DatabaseReference newTransactionsRef = cashbooksRef.child(newCashbookId).child("transactions");

                        oldTransactionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                newTransactionsRef.setValue(dataSnapshot.getValue())
                                        .addOnSuccessListener(aVoid1 -> Toast.makeText(HomePage.this, "Cashbook duplicated.", Toast.LENGTH_SHORT).show())
                                        .addOnFailureListener(e -> ErrorHandler.handleExportError(HomePage.this, e));
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                ErrorHandler.handleFirebaseError(HomePage.this, databaseError);
                            }
                        });
                    })
                    .addOnFailureListener(e -> ErrorHandler.handleExportError(HomePage.this, e));
        }
    }
}
