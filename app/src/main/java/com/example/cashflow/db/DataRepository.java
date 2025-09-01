package com.example.cashflow.db;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.cashflow.CashbookModel;
import com.example.cashflow.TransactionModel;
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
import java.util.UUID;

public class DataRepository {

    private static volatile DataRepository INSTANCE;
    private final GuestDbHelper guestDbHelper;
    private final DatabaseReference userDatabase;
    private final FirebaseAuth mAuth;

    public interface DataCallback<T> {
        void onCallback(T data);
    }

    private DataRepository(Application application) {
        guestDbHelper = new GuestDbHelper(application);
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        userDatabase = (currentUser != null && !currentUser.isAnonymous())
                ? FirebaseDatabase.getInstance().getReference().child("users").child(currentUser.getUid())
                : null;
    }

    public static DataRepository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (DataRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DataRepository(application);
                }
            }
        }
        return INSTANCE;
    }

    // --- TRANSACTION METHODS ---

    public void getAllTransactions(boolean isGuest, String cashbookId, DataCallback<List<TransactionModel>> callback) {
        if (isGuest) {
            new Thread(() -> {
                List<TransactionModel> transactions = guestDbHelper.getAllTransactions();
                callback.onCallback(transactions);
            }).start();
        } else {
            if (userDatabase == null || cashbookId == null) {
                callback.onCallback(new ArrayList<>());
                return;
            }
            userDatabase.child("cashbooks").child(cashbookId).child("transactions").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    List<TransactionModel> transactions = new ArrayList<>();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        transactions.add(snapshot.getValue(TransactionModel.class));
                    }
                    Collections.sort(transactions, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                    callback.onCallback(transactions);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    callback.onCallback(new ArrayList<>());
                }
            });
        }
    }

    public void addGuestTransaction(TransactionModel transaction) {
        new Thread(() -> guestDbHelper.addTransaction(transaction)).start();
    }

    public void addFirebaseTransaction(String cashbookId, TransactionModel transaction) {
        if (userDatabase == null || cashbookId == null) return;
        String transactionId = userDatabase.child("cashbooks").child(cashbookId).child("transactions").push().getKey();
        if (transactionId != null) {
            transaction.setTransactionId(transactionId);
            userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transactionId).setValue(transaction);
        }
    }

    public void updateGuestTransaction(TransactionModel transaction) {
        new Thread(() -> guestDbHelper.updateTransaction(transaction)).start();
    }

    public void updateFirebaseTransaction(String cashbookId, TransactionModel transaction) {
        if (userDatabase == null || cashbookId == null || transaction.getTransactionId() == null) return;
        userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transaction.getTransactionId()).setValue(transaction);
    }

    public void deleteGuestTransaction(String transactionId) {
        new Thread(() -> guestDbHelper.deleteTransaction(transactionId)).start();
    }

    public void deleteFirebaseTransaction(String cashbookId, String transactionId) {
        if (userDatabase == null || cashbookId == null || transactionId == null) return;
        userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transactionId).removeValue();
    }

    // --- CASHBOOK METHODS ---

    public void getCashbooks(DataCallback<List<CashbookModel>> callback) {
        if (userDatabase == null) {
            callback.onCallback(new ArrayList<>());
            return;
        }
        userDatabase.child("cashbooks").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<CashbookModel> cashbooks = new ArrayList<>();
                for(DataSnapshot snapshot : dataSnapshot.getChildren()){
                    CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                    if(cashbook != null){
                        cashbook.setId(snapshot.getKey());
                        cashbooks.add(cashbook);
                    }
                }
                callback.onCallback(cashbooks);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onCallback(new ArrayList<>());
            }
        });
    }

    public void createNewCashbook(String name, DataCallback<String> callback) {
        if (userDatabase == null) {
            callback.onCallback(null);
            return;
        }
        String cashbookId = userDatabase.child("cashbooks").push().getKey();
        if(cashbookId != null){
            CashbookModel newCashbook = new CashbookModel(cashbookId, name);
            userDatabase.child("cashbooks").child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> callback.onCallback(cashbookId))
                    .addOnFailureListener(e -> callback.onCallback(null));
        } else {
            callback.onCallback(null);
        }
    }
}

