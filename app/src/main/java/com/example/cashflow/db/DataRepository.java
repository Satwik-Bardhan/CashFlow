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

/**
 * DataRepository - Centralized data access layer for CashFlow app
 * Handles both Firebase (authenticated users) and SQLite (guest users) operations
 */
public class DataRepository {

    private static final String TAG = "DataRepository";
    private static volatile DataRepository INSTANCE;

    private final GuestDbHelper guestDbHelper;
    private final DatabaseReference userDatabase;
    private final FirebaseAuth mAuth;

    public interface DataCallback<T> {
        void onCallback(T data);
    }

    public interface ErrorCallback {
        void onError(String error);
    }

    private DataRepository(Application application) {
        guestDbHelper = new GuestDbHelper(application);
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        userDatabase = (currentUser != null && !currentUser.isAnonymous()) ?
                FirebaseDatabase.getInstance().getReference().child("users").child(currentUser.getUid()) :
                null;
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

    // --- ENHANCED TRANSACTION METHODS ---

    public void getAllTransactions(boolean isGuest, String cashbookId, DataCallback<List<TransactionModel>> callback, ErrorCallback errorCallback) {
        if (isGuest) {
            new Thread(() -> {
                try {
                    List<TransactionModel> transactions = guestDbHelper.getAllTransactions();
                    callback.onCallback(transactions);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting guest transactions", e);
                    if (errorCallback != null) {
                        errorCallback.onError("Failed to load offline transactions");
                    }
                }
            }).start();
        } else {
            if (userDatabase == null || cashbookId == null) {
                callback.onCallback(new ArrayList<>());
                return;
            }

            userDatabase.child("cashbooks").child(cashbookId).child("transactions")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            try {
                                List<TransactionModel> transactions = new ArrayList<>();
                                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                                    if (transaction != null) {
                                        transaction.setTransactionId(snapshot.getKey());
                                        transactions.add(transaction);
                                    }
                                }
                                // Sort by timestamp, newest first
                                Collections.sort(transactions, (t1, t2) ->
                                        Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                                callback.onCallback(transactions);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing Firebase transactions", e);
                                if (errorCallback != null) {
                                    errorCallback.onError("Failed to process transaction data");
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.e(TAG, "Firebase transaction query cancelled", databaseError.toException());
                            callback.onCallback(new ArrayList<>());
                            if (errorCallback != null) {
                                errorCallback.onError("Database connection failed");
                            }
                        }
                    });
        }
    }

    public void addGuestTransaction(TransactionModel transaction, DataCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                guestDbHelper.addTransaction(transaction);
                if (callback != null) {
                    callback.onCallback(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error adding guest transaction", e);
                if (callback != null) {
                    callback.onCallback(false);
                }
            }
        }).start();
    }

    public void addFirebaseTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        if (userDatabase == null || cashbookId == null) {
            if (callback != null) {
                callback.onCallback(false);
            }
            return;
        }

        String transactionId = userDatabase.child("cashbooks").child(cashbookId).child("transactions").push().getKey();
        if (transactionId != null) {
            transaction.setTransactionId(transactionId);
            userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transactionId)
                    .setValue(transaction)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Transaction added successfully to Firebase");
                        if (callback != null) {
                            callback.onCallback(true);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error adding transaction to Firebase", e);
                        if (callback != null) {
                            callback.onCallback(false);
                        }
                    });
        } else {
            if (callback != null) {
                callback.onCallback(false);
            }
        }
    }

    public void updateGuestTransaction(TransactionModel transaction, DataCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                guestDbHelper.updateTransaction(transaction);
                if (callback != null) {
                    callback.onCallback(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating guest transaction", e);
                if (callback != null) {
                    callback.onCallback(false);
                }
            }
        }).start();
    }

    public void updateFirebaseTransaction(String cashbookId, TransactionModel transaction, DataCallback<Boolean> callback) {
        if (userDatabase == null || cashbookId == null || transaction.getTransactionId() == null) {
            if (callback != null) {
                callback.onCallback(false);
            }
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).child("transactions")
                .child(transaction.getTransactionId())
                .setValue(transaction)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Transaction updated successfully in Firebase");
                    if (callback != null) {
                        callback.onCallback(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating transaction in Firebase", e);
                    if (callback != null) {
                        callback.onCallback(false);
                    }
                });
    }

    public void deleteGuestTransaction(String transactionId, DataCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                guestDbHelper.deleteTransaction(transactionId);
                if (callback != null) {
                    callback.onCallback(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting guest transaction", e);
                if (callback != null) {
                    callback.onCallback(false);
                }
            }
        }).start();
    }

    public void deleteFirebaseTransaction(String cashbookId, String transactionId, DataCallback<Boolean> callback) {
        if (userDatabase == null || cashbookId == null || transactionId == null) {
            if (callback != null) {
                callback.onCallback(false);
            }
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).child("transactions").child(transactionId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Transaction deleted successfully from Firebase");
                    if (callback != null) {
                        callback.onCallback(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting transaction from Firebase", e);
                    if (callback != null) {
                        callback.onCallback(false);
                    }
                });
    }

    // --- ENHANCED CASHBOOK METHODS ---

    public void getCashbooks(DataCallback<List<CashbookModel>> callback, ErrorCallback errorCallback) {
        if (userDatabase == null) {
            callback.onCallback(new ArrayList<>());
            return;
        }

        userDatabase.child("cashbooks").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    List<CashbookModel> cashbooks = new ArrayList<>();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                        if (cashbook != null) {
                            cashbook.setId(snapshot.getKey());
                            cashbooks.add(cashbook);
                        }
                    }
                    callback.onCallback(cashbooks);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing cashbooks", e);
                    if (errorCallback != null) {
                        errorCallback.onError("Failed to process cashbook data");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Cashbooks query cancelled", error.toException());
                callback.onCallback(new ArrayList<>());
                if (errorCallback != null) {
                    errorCallback.onError("Database connection failed");
                }
            }
        });
    }

    public void createNewCashbook(String name, DataCallback<String> callback, ErrorCallback errorCallback) {
        if (userDatabase == null) {
            if (errorCallback != null) {
                errorCallback.onError("User not authenticated");
            }
            callback.onCallback(null);
            return;
        }

        if (name == null || name.trim().isEmpty()) {
            if (errorCallback != null) {
                errorCallback.onError("Cashbook name cannot be empty");
            }
            callback.onCallback(null);
            return;
        }

        String cashbookId = userDatabase.child("cashbooks").push().getKey();
        if (cashbookId != null) {
            CashbookModel newCashbook = new CashbookModel(cashbookId, name.trim());

            userDatabase.child("cashbooks").child(cashbookId).setValue(newCashbook)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Cashbook created successfully: " + name);
                        callback.onCallback(cashbookId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating cashbook: " + name, e);
                        if (errorCallback != null) {
                            errorCallback.onError("Failed to create cashbook");
                        }
                        callback.onCallback(null);
                    });
        } else {
            if (errorCallback != null) {
                errorCallback.onError("Failed to generate cashbook ID");
            }
            callback.onCallback(null);
        }
    }

    public void deleteCashbook(String cashbookId, DataCallback<Boolean> callback, ErrorCallback errorCallback) {
        if (userDatabase == null || cashbookId == null) {
            if (errorCallback != null) {
                errorCallback.onError("Invalid request");
            }
            if (callback != null) {
                callback.onCallback(false);
            }
            return;
        }

        userDatabase.child("cashbooks").child(cashbookId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Cashbook deleted successfully: " + cashbookId);
                    if (callback != null) {
                        callback.onCallback(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting cashbook: " + cashbookId, e);
                    if (errorCallback != null) {
                        errorCallback.onError("Failed to delete cashbook");
                    }
                    if (callback != null) {
                        callback.onCallback(false);
                    }
                });
    }

    public void duplicateCashbook(String originalCashbookId, String newName, DataCallback<String> callback, ErrorCallback errorCallback) {
        if (userDatabase == null || originalCashbookId == null || newName == null) {
            if (errorCallback != null) {
                errorCallback.onError("Invalid request");
            }
            callback.onCallback(null);
            return;
        }

        // First, get the original cashbook data
        userDatabase.child("cashbooks").child(originalCashbookId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        CashbookModel originalCashbook = dataSnapshot.getValue(CashbookModel.class);
                        if (originalCashbook != null) {
                            String newCashbookId = userDatabase.child("cashbooks").push().getKey();
                            if (newCashbookId != null) {
                                CashbookModel duplicatedCashbook = new CashbookModel(newCashbookId, newName.trim());

                                userDatabase.child("cashbooks").child(newCashbookId).setValue(duplicatedCashbook)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Cashbook duplicated successfully: " + newName);
                                            callback.onCallback(newCashbookId);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error duplicating cashbook", e);
                                            if (errorCallback != null) {
                                                errorCallback.onError("Failed to duplicate cashbook");
                                            }
                                            callback.onCallback(null);
                                        });
                            } else {
                                if (errorCallback != null) {
                                    errorCallback.onError("Failed to generate new cashbook ID");
                                }
                                callback.onCallback(null);
                            }
                        } else {
                            if (errorCallback != null) {
                                errorCallback.onError("Original cashbook not found");
                            }
                            callback.onCallback(null);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error reading original cashbook", databaseError.toException());
                        if (errorCallback != null) {
                            errorCallback.onError("Failed to read original cashbook");
                        }
                        callback.onCallback(null);
                    }
                });
    }

    // --- UTILITY METHODS ---

    /**
     * Cleanup method to remove listeners and prevent memory leaks
     */
    public void cleanup() {
        // Close SQLite database if needed
        if (guestDbHelper != null) {
            guestDbHelper.close();
        }

        // Firebase listeners are automatically removed when activity is destroyed
        // But this method can be extended for manual cleanup if needed
        Log.d(TAG, "DataRepository cleaned up");
    }

    /**
     * Check if user is authenticated and has Firebase access
     */
    public boolean isUserAuthenticated() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null && !currentUser.isAnonymous();
    }

    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }
}
