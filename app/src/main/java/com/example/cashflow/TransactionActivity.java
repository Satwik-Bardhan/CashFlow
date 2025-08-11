package com.example.cashflow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TransactionActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private static final String TAG = "TransactionActivity";

    private List<TransactionModel> allTransactions = new ArrayList<>();
    private List<TransactionModel> displayedTransactions = new ArrayList<>();
    private TransactionAdapter transactionAdapter;
    private RecyclerView transactionRecyclerView;
    private PieChart pieChart;

    private TextView incomeText, expenseText, balanceText;
    private EditText searchEditText;
    private ImageView filterButton;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener transactionsListener;
    private String currentCashbookId;

    private ActivityResultLauncher<Intent> filterLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_activity);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        currentCashbookId = getIntent().getStringExtra("cashbook_id");
        if (currentCashbookId == null) {
            Toast.makeText(this, "Error: No active cashbook found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeUI();
        setupRecyclerView();
        setupClickListeners();
        setupFilterLauncher();
    }

    private void initializeUI() {
        incomeText = findViewById(R.id.incomeText);
        expenseText = findViewById(R.id.expenseText);
        balanceText = findViewById(R.id.balanceText);
        pieChart = findViewById(R.id.pieChart);
        searchEditText = findViewById(R.id.searchEditText);
        filterButton = findViewById(R.id.filterButton); // Updated ID
    }

    private void setupRecyclerView() {
        transactionRecyclerView = findViewById(R.id.transactionRecyclerView);
        transactionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(displayedTransactions, this);
        transactionRecyclerView.setAdapter(transactionAdapter);
    }

    private void setupClickListeners() {
        filterButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FiltersActivity.class);
            // You can pass current filters to the FiltersActivity here if needed
            filterLauncher.launch(intent);
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Listeners for bottom navigation and other buttons remain the same
    }

    private void setupFilterLauncher() {
        filterLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        // Get filter settings from the result Intent
                        // For now, just re-filter the list
                        filterTransactions();
                        Toast.makeText(this, "Filters Applied!", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startListeningForTransactions(currentUser.getUid());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && transactionsListener != null) {
            mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(currentCashbookId).child("transactions").removeEventListener(transactionsListener);
        }
    }

    private void startListeningForTransactions(String userId) {
        DatabaseReference transactionsRef = mDatabase.child("users").child(userId).child("cashbooks").child(currentCashbookId).child("transactions");
        transactionsListener = transactionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allTransactions.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    allTransactions.add(snapshot.getValue(TransactionModel.class));
                }
                Collections.sort(allTransactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                filterTransactions();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(TransactionActivity.this, "Failed to load transactions.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterTransactions() {
        displayedTransactions.clear();
        String query = searchEditText.getText().toString().toLowerCase(Locale.getDefault());

        // This is where you would apply the filters received from FiltersActivity
        // For now, it only filters by the search query
        for (TransactionModel transaction : allTransactions) {
            if (query.isEmpty() || (transaction.getTransactionCategory() != null && transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query))) {
                displayedTransactions.add(transaction);
            }
        }
        updateUI();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUI() {
        transactionAdapter.notifyDataSetChanged();
        // calculateTotals() and setupPieChart() methods would be here
    }

    @Override
    public void onItemClick(TransactionModel transaction) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra("transaction_model", transaction);
        intent.putExtra("cashbook_id", currentCashbookId);
        startActivity(intent);
    }
}
