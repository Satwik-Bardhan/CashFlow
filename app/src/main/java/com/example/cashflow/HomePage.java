package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.DialogInterface; // Added import for DialogInterface
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog; // Added import for AlertDialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List; // Added import
import java.util.Locale;

public class HomePage extends AppCompatActivity {

    private static final String TAG = "HomePage";

    LinearLayout btnSettings;
    TextView userNameTop;
    TextView dateTodayText;
    TextView uidText;
    TextView balanceText;
    TextView moneyInText;
    TextView moneyOutText;
    TextView userNameBottom;
    TableLayout transactionTable;

    LinearLayout cashInButton;
    LinearLayout cashOutButton;
    LinearLayout viewFullTransactionsButton;

    LinearLayout btnTransactions;
    LinearLayout btnHome;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private ValueEventListener userProfileListener;
    private ArrayList<TransactionModel> allTransactions = new ArrayList<>();

    private LinearLayout userBox; // New: Reference to the userBox layout


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View rootLayout = findViewById(R.id.main_root_layout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

                LinearLayout fixedBottomContainer = findViewById(R.id.fixedBottomContainer);
                if (fixedBottomContainer != null) {
                    fixedBottomContainer.setPadding(
                            fixedBottomContainer.getPaddingLeft(),
                            fixedBottomContainer.getPaddingTop(),
                            fixedBottomContainer.getPaddingRight(),
                            systemBars.bottom
                    );
                }
                return insets;
            });
        } else {
            Log.e(TAG, "Root layout (R.id.main_root_layout) not found. Insets might not be applied.");
            Toast.makeText(this, "Layout error: root view not found.", Toast.LENGTH_LONG).show();
        }

        LinearLayout fixedBottomContainer = findViewById(R.id.fixedBottomContainer);
        if (fixedBottomContainer != null) {
            btnSettings = fixedBottomContainer.findViewById(R.id.btnSettings);
        } else {
            Log.e(TAG, "fixedBottomContainer not found in layout.");
        }

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
        userBox = findViewById(R.id.userBox); // Initialize userBox

        btnTransactions = findViewById(R.id.btnTransactions);
        btnHome = findViewById(R.id.btnHome);


        if (userNameTop == null || dateTodayText == null || uidText == null ||
                balanceText == null || moneyInText == null || moneyOutText == null ||
                userNameBottom == null || transactionTable == null ||
                cashInButton == null || cashOutButton == null || viewFullTransactionsButton == null ||
                userBox == null || // Added userBox to null check
                btnTransactions == null || btnHome == null || btnSettings == null) {
            Log.e(TAG, "Missing UI components. Check layout IDs in activity_main.xml.");
            Toast.makeText(this, "Error: UI components missing in layout.", Toast.LENGTH_LONG).show();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);
        updateUserUI(isGuest);

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(HomePage.this, SettingsActivity.class);
            startActivity(intent);
        });

        cashInButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomePage.this, CashInOutActivity.class);
            intent.putExtra("transaction_type", "IN");
            startActivity(intent);
        });

        cashOutButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomePage.this, CashInOutActivity.class);
            intent.putExtra("transaction_type", "OUT");
            startActivity(intent);
        });

        viewFullTransactionsButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomePage.this, TransactionActivity.class);
            startActivity(intent);
        });

        // Set Listeners for the dropdown userBox
        userBox.setOnClickListener(v -> showUserDropdown());

        // Set Listeners for Bottom Navigation Bar
        btnHome.setOnClickListener(v -> {
            Toast.makeText(HomePage.this, "Already on Home", Toast.LENGTH_SHORT).show();
        });

        btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(HomePage.this, TransactionActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(HomePage.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void showUserDropdown() {
        // Placeholder for dropdown functionality
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Switch User / Cashbook");

        List<String> userOptions = new ArrayList<>();
        userOptions.add("Create New Cashbook");
        userOptions.add("My Main Cashbook (example@gmail.com)");
        userOptions.add("Family Cashbook");
        userOptions.add("Business Cashbook");

        builder.setItems(userOptions.toArray(new String[0]), (dialog, which) -> {
            String selectedOption = userOptions.get(which);
            Toast.makeText(HomePage.this, selectedOption + " selected", Toast.LENGTH_SHORT).show();
            // Implement logic here to switch users or create a new one
            // This is a placeholder for now.
        });

        builder.show();
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !getIntent().getBooleanExtra("isGuest", false)) {
            Log.d(TAG, "onStart: User logged in, starting transaction listener.");
            startListeningForTransactions(currentUser.getUid());
            startListeningForUserProfile(currentUser.getUid());
        } else if (getIntent().getBooleanExtra("isGuest", false)) {
            Log.d(TAG, "onStart: Guest user, clearing transactions and profile.");
            allTransactions.clear();
            updateTransactionTableAndSummary();
            userNameTop.setText("Guest User");
            uidText.setText("UID: GUEST");
            userNameBottom.setText("Guest User");
            dateTodayText.setText("Last Open: Guest Session");
        } else {
            Log.d(TAG, "onStart: No user, not starting listener.");
            allTransactions.clear();
            updateTransactionTableAndSummary();
            userNameTop.setText("Welcome User");
            uidText.setText("UID: N/A");
            userNameBottom.setText("User Name");
            dateTodayText.setText("Last Open: Not Available");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (transactionsListener != null) {
                mDatabase.child("users").child(currentUser.getUid()).child("transactions").removeEventListener(transactionsListener);
                Log.d(TAG, "Firebase transaction listener removed in onStop.");
            }
            if (userProfileListener != null) {
                mDatabase.child("users").child(currentUser.getUid()).removeEventListener(userProfileListener);
                Log.d(TAG, "Firebase user profile listener removed in onStop.");
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateUserUI(boolean isGuest) {
        if (isGuest) {
            userNameTop.setText("Guest User");
            uidText.setText("UID: GUEST");
            userNameBottom.setText("Guest User");
            dateTodayText.setText("Last Open: Guest Session");
        } else {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userNameTop.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail());
                uidText.setText("UID: " + currentUser.getUid());
                userNameBottom.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User Name");
                dateTodayText.setText("Last Open: " + DateFormat.getDateTimeInstance().format(new Date()));
            } else {
                userNameTop.setText("Welcome User");
                uidText.setText("UID: N/A");
                userNameBottom.setText("User Name");
                dateTodayText.setText("Last Open: Not Available");
            }
        }
    }

    private void startListeningForUserProfile(String userId) {
        if (userProfileListener != null) {
            mDatabase.child("users").child(userId).removeEventListener(userProfileListener);
        }

        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Users userProfile = dataSnapshot.getValue(Users.class);
                FirebaseUser firebaseUser = mAuth.getCurrentUser();

                if (userProfile != null) {
                    Log.d(TAG, "onDataChange: User profile loaded from database for HomePage.");
                    userNameTop.setText(userProfile.getUserName() != null ? userProfile.getUserName() : "User Name");
                    userNameBottom.setText(firebaseUser != null && firebaseUser.getEmail() != null ? firebaseUser.getEmail() : (userProfile.getMail() != null ? userProfile.getMail() : "N/A Email"));
                    uidText.setText("UID: " + (userProfile.getUserId() != null ? userProfile.getUserId() : "N/A"));
                } else {
                    Log.d(TAG, "onDataChange: User profile not found in database. Setting default for HomePage.");
                    if (firebaseUser != null) {
                        userNameTop.setText(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : firebaseUser.getEmail());
                        userNameBottom.setText(firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "N/A Email");
                        uidText.setText("UID: " + firebaseUser.getUid());
                    } else {
                        userNameTop.setText("Welcome User");
                        uidText.setText("UID: N/A");
                        userNameBottom.setText("User Name");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "User profile load onCancelled: " + databaseError.getMessage(), databaseError.toException());
                Toast.makeText(HomePage.this, "Failed to load user profile.", Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.child("users").child(userId).addValueEventListener(userProfileListener);
    }


    private void startListeningForTransactions(String userId) {
        if (transactionsListener != null) {
            mDatabase.child("users").child(userId).child("transactions").removeEventListener(transactionsListener);
            Log.d(TAG, "Removed existing transaction listener before attaching new one.");
        }

        mDatabase.child("users").child(userId).child("transactions").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: Transaction data received from Firebase.");
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null) {
                        transaction.setTransactionId(snapshot.getKey());
                        if (snapshot.hasChild("timestamp")) {
                            transaction.setTimestamp(snapshot.child("timestamp").getValue(Long.class));
                        } else {
                            transaction.setTimestamp(0);
                            Log.w(TAG, "Transaction " + snapshot.getKey() + " has no timestamp. Defaulting to 0.");
                        }
                        if (!snapshot.hasChild("paymentMode")) transaction.setPaymentMode("Cash");
                        if (!snapshot.hasChild("remark")) transaction.setRemark("");
                        if (!snapshot.hasChild("partyName")) transaction.setPartyName("");

                        allTransactions.add(transaction);
                    } else {
                        Log.w(TAG, "onDataChange: Transaction is null for snapshot: " + snapshot.getKey());
                    }
                }
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                Log.d(TAG, "Transactions sorted. Total: " + allTransactions.size());

                updateTransactionTableAndSummary();
                Log.d(TAG, "Transactions loaded and UI updated.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "loadPost:onCancelled", databaseError.toException());
                Toast.makeText(HomePage.this, "Failed to load transactions: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        Log.d(TAG, "Firebase transaction listener attached for user: " + userId);
    }

    @SuppressLint("SetTextI18n")
    private void updateTransactionTableAndSummary() {
        Log.d(TAG, "Updating transaction table and summary. Displaying " + Math.min(allTransactions.size(), 6) + " entries in table.");
        int childCount = transactionTable.getChildCount();
        if (childCount > 1) {
            transactionTable.removeViews(1, childCount - 1);
            Log.d(TAG, "Cleared " + (childCount - 1) + " old rows from table.");
        }

        double currentTotalIncomeAll = 0;
        double currentTotalExpenseAll = 0;

        for (TransactionModel transaction : allTransactions) {
            if (transaction.getType().equalsIgnoreCase("IN")) {
                currentTotalIncomeAll += transaction.getAmount();
            } else if (transaction.getType().equalsIgnoreCase("OUT")) {
                currentTotalExpenseAll += transaction.getAmount();
            }
        }

        double currentBalanceAll = currentTotalIncomeAll - currentTotalExpenseAll;
        balanceText.setText("₹" + String.format(Locale.US, "%.2f", currentBalanceAll));
        moneyInText.setText("₹" + String.format(Locale.US, "%.2f", currentTotalIncomeAll));
        moneyOutText.setText("₹" + String.format(Locale.US, "%.2f", currentTotalExpenseAll));
        Log.d(TAG, "Balance summary updated for ALL transactions: Income=" + currentTotalIncomeAll + ", Expense=" + currentTotalExpenseAll + ", Balance=" + currentBalanceAll);


        if (allTransactions.isEmpty()) {
            Log.d(TAG, "No transactions found. Displaying empty message in table.");
            TableRow messageRow = new TableRow(this);
            TableLayout.LayoutParams rowParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            messageRow.setLayoutParams(rowParams);

            TextView messageTextView = new TextView(this);
            messageTextView.setText("No transactions yet. Add entries to display transactions.");
            messageTextView.setPadding(16, 16, 16, 16);
            messageTextView.setTextColor(Color.GRAY);
            messageTextView.setTextSize(14f);
            messageTextView.setGravity(Gravity.CENTER);

            TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            textParams.span = 4;
            messageTextView.setLayoutParams(textParams);
            messageRow.addView(messageTextView);

            transactionTable.addView(messageRow);
        } else {
            for (int i = 0; i < Math.min(allTransactions.size(), 6); i++) {
                TransactionModel transaction = allTransactions.get(i);
                addTransactionRow(transaction);
            }
            Log.d(TAG, "Added " + Math.min(allTransactions.size(), 6) + " recent rows to table.");
        }
        Log.d(TAG, "Home Page UI fully updated.");
    }

    private void addTransactionRow(TransactionModel transaction) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.table_row_border);
        row.setPadding(0, 4, 0, 4);


        TextView entryView = new TextView(this);
        entryView.setText(transaction.getTransactionCategory());
        entryView.setPadding(8, 8, 8, 8);
        entryView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f));
        entryView.setTextColor(Color.BLACK);
        entryView.setTypeface(null, Typeface.BOLD);

        TextView modeView = new TextView(this);
        modeView.setText(transaction.getPaymentMode());
        modeView.setPadding(8, 8, 8, 8);
        modeView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        modeView.setTextColor(ContextCompat.getColor(this, R.color.balance_blue));
        modeView.setGravity(Gravity.CENTER);

        TextView outView = new TextView(this);
        outView.setText(transaction.getType().equalsIgnoreCase("OUT") ? "₹" + String.format(Locale.US, "%.2f", transaction.getAmount()) : "-");
        outView.setPadding(8, 8, 8, 8);
        outView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        outView.setTextColor(ContextCompat.getColor(this, R.color.homepage_expense_amount_red));
        outView.setGravity(Gravity.CENTER);

        TextView inView = new TextView(this);
        inView.setText(transaction.getType().equalsIgnoreCase("IN") ? "₹" + String.format(Locale.US, "%.2f", transaction.getAmount()) : "-");
        inView.setPadding(8, 8, 8, 8);
        inView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        inView.setTextColor(ContextCompat.getColor(this, R.color.homepage_income_amount_green));
        inView.setGravity(Gravity.CENTER);

        row.addView(entryView);
        row.addView(modeView);
        row.addView(outView);
        row.addView(inView);

        transactionTable.addView(row);
    }
}