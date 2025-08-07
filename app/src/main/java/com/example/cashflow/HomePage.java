package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity; // Correction: Added the missing import for Gravity
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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

    private TextView userNameTop, dateTodayText, uidText, balanceText, moneyInText, moneyOutText, userNameBottom;
    private TableLayout transactionTable;
    private LinearLayout cashInButton, cashOutButton, viewFullTransactionsButton, userBox;
    private LinearLayout btnTransactions, btnHome, btnSettings;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener, cashbooksListener;

    private ArrayList<TransactionModel> allTransactions = new ArrayList<>();
    private List<CashbookModel> cashbooks = new ArrayList<>();
    private String currentCashbookId;

    private AlertDialog cashbookDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupClickListeners();

        if (getIntent().getBooleanExtra("isGuest", false)) {
            handleGuestMode();
        }
    }

    private void initializeUI() {
        userNameTop = findViewById(R.id.userNameTop);
        dateTodayText = findViewById(R.id.dateToday);
        uidText = findViewById(R.id.uidText);
        balanceText = findViewById(R.id.balanceText);
        moneyInText = findViewById(R.id.moneyIn);
        moneyOutText = findViewById(R.id.moneyOut);
        userNameBottom = findViewById(R.id.userNameBottom);
        transactionTable = findViewById(R.id.transactionTable);
        cashInButton = findViewById(R.id.cashInButton);
        cashOutButton = findViewById(R.id.cashOutButton);
        viewFullTransactionsButton = findViewById(R.id.viewFullTransactionsButton);
        userBox = findViewById(R.id.userBox);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupClickListeners() {
        cashInButton.setOnClickListener(v -> openCashInOutActivity("IN"));
        cashOutButton.setOnClickListener(v -> openCashInOutActivity("OUT"));
        viewFullTransactionsButton.setOnClickListener(v -> navigateToTransactionList());
        userBox.setOnClickListener(v -> showUserDropdown());
        btnHome.setOnClickListener(v -> Toast.makeText(HomePage.this, "Already on Home", Toast.LENGTH_SHORT).show());
        btnTransactions.setOnClickListener(v -> navigateToTransactionList());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(HomePage.this, SettingsActivity.class)));
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

    private class CashbookAdapter extends ArrayAdapter<CashbookModel> {
        private String userId;
        private AlertDialog dialog;

        public CashbookAdapter(Context context, List<CashbookModel> cashbooks, String userId, AlertDialog dialog) {
            super(context, 0, cashbooks);
            this.userId = userId;
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
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(cashbook.getName())) {
                mDatabase.child("users").child(currentUser.getUid()).child("cashbooks")
                        .child(cashbook.getId()).child("name").setValue(newName)
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, "Renamed successfully.", Toast.LENGTH_SHORT).show());
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
                .setMessage("Are you sure you want to delete '" + cashbook.getName() + "'? This will delete all its transactions.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mDatabase.child("users").child(currentUser.getUid()).child("cashbooks")
                            .child(cashbook.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Cashbook deleted.", Toast.LENGTH_SHORT).show();
                                if (cashbook.getId().equals(currentCashbookId)) {
                                    switchCashbook(cashbooks.get(0).getId());
                                }
                            });
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
                                        .addOnSuccessListener(aVoid1 -> Toast.makeText(HomePage.this, "Cashbook duplicated.", Toast.LENGTH_SHORT).show());
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {}
                        });
                    });
        }
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
                    .addOnFailureListener(e -> Toast.makeText(HomePage.this, "Failed to create cashbook.", Toast.LENGTH_LONG).show());
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
    private void handleGuestMode() {
        allTransactions.clear();
        updateTransactionTableAndSummary();
        userNameTop.setText("Guest User");
        uidText.setText("UID: GUEST");
        userNameBottom.setText("Guest User");
        dateTodayText.setText("Last Open: Guest Session");
    }

    private void loadActiveCashbookId(String userId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        currentCashbookId = prefs.getString("active_cashbook_id_" + userId, null);
        startListeningForCashbooks(userId);
    }

    private void startListeningForCashbooks(String userId) {
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
            public void onCancelled(@NonNull DatabaseError databaseError) { /* Handle error */ }
        };
        mDatabase.child("users").child(userId).child("cashbooks").addValueEventListener(cashbooksListener);
    }

    private void startListeningForTransactions(String userId) {
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
        }
        if (currentCashbookId == null) return;

        DatabaseReference transactionsRef = mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions");
        transactionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    allTransactions.add(snapshot.getValue(TransactionModel.class));
                }
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                updateTransactionTableAndSummary();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { /* Handle error */ }
        };
        transactionsRef.addValueEventListener(transactionsListener);
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
            userNameTop.setText(cashbookName);
            userNameBottom.setText(currentUser.getEmail());
            uidText.setText("UID: " + currentUser.getUid());
            dateTodayText.setText("Last Open: " + DateFormat.getDateTimeInstance().format(new Date()));
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateTransactionTableAndSummary() {
        if (transactionTable.getChildCount() > 2) {
            transactionTable.removeViews(2, transactionTable.getChildCount() - 2);
        }

        double totalIncome = 0, totalExpense = 0;
        for (TransactionModel transaction : allTransactions) {
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else {
                totalExpense += transaction.getAmount();
            }
        }

        balanceText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome - totalExpense));
        moneyInText.setText("₹" + String.format(Locale.US, "%.2f", totalIncome));
        moneyOutText.setText("₹" + String.format(Locale.US, "%.2f", totalExpense));

        if (allTransactions.isEmpty()) {
            // Handle empty state
        } else {
            for (int i = 0; i < Math.min(allTransactions.size(), 6); i++) {
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
        transactionTable.addView(row);
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

    private void removeFirebaseListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        if (transactionsListener != null && currentCashbookId != null) {
            mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
        }
        if (cashbooksListener != null) {
            mDatabase.child("users").child(userId).child("cashbooks").removeEventListener(cashbooksListener);
        }
    }

    private void showCreateFirstCashbookDialog(String userId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create Your First Cashbook");
        builder.setMessage("Please enter a name for your first cashbook to get started.");
        final EditText input = new EditText(this);
        input.setHint("e.g., My Main Book");
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String cashbookName = input.getText().toString().trim();
            if (!cashbookName.isEmpty()) {
                createNewCashbook(cashbookName);
            } else {
                Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void saveActiveCashbookId(String userId, String cashbookId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("active_cashbook_id_" + userId, cashbookId).apply();
    }
}
