package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.cashflow.models.Users; // Assuming Users is in models package
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
 * HomePageViewModel - ViewModel for HomePage activity
 *
 * Responsibilities:
 * - Manage cashbooks and transactions data for authenticated users
 * - Handle Firebase data loading and caching
 * - Provide LiveData for UI observation
 *
 * Updated: November 2025 - All Guest Mode logic removed.
 */
public class HomePageViewModel extends AndroidViewModel {

    private static final String TAG = "HomePageViewModel";

    // Firebase
    private final DatabaseReference userDatabaseRef; // Renamed for clarity
    private String currentCashbookId;

    // LiveData for UI observation
    private final MutableLiveData<List<TransactionModel>> transactions = new MutableLiveData<>();
    private final MutableLiveData<List<CashbookModel>> cashbooks = new MutableLiveData<>();
    private final MutableLiveData<String> activeCashbookName = new MutableLiveData<>();
    private final MutableLiveData<Users> userProfile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Firebase listeners
    private ValueEventListener transactionsListener;
    private ValueEventListener cashbooksListener;
    private ValueEventListener userProfileListener;

    // Previous cashbook reference for cleanup
    private DatabaseReference previousTransactionsRef;

    /**
     * Constructor for HomePageViewModel
     */
    public HomePageViewModel(@NonNull Application application) {
        super(application);
        this.transactions.setValue(new ArrayList<>());
        this.cashbooks.setValue(new ArrayList<>());

        Log.d(TAG, "ViewModel initialized for authenticated user.");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            userDatabaseRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid());
            loadUserProfile(currentUser.getUid());
            loadCashbooks();
        } else {
            userDatabaseRef = null;
            Log.e(TAG, "ViewModel created, but user is not authenticated!");
            errorMessage.setValue("User not logged in.");
        }
    }

    /**
     * Constructor with cashbook ID
     */
    public HomePageViewModel(@NonNull Application application, String cashbookId) {
        this(application);
        this.currentCashbookId = cashbookId;

        if (currentCashbookId != null) {
            switchCashbook(currentCashbookId);
        }
    }

    // ============================================
    // LiveData Getters
    // ============================================

    public LiveData<List<TransactionModel>> getTransactions() {
        return transactions;
    }

    public LiveData<List<CashbookModel>> getCashbooks() {
        return cashbooks;
    }

    public LiveData<String> getActiveCashbookName() {
        return activeCashbookName;
    }

    public LiveData<Users> getUserProfile() {
        return userProfile;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public String getCurrentCashbookId() {
        return currentCashbookId;
    }

    // ============================================
    // User Profile
    // ============================================

    /**
     * Load user profile information from Firebase
     */
    private void loadUserProfile(String userId) {
        if (userDatabaseRef == null) return;

        try {
            userProfileListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        Users user = snapshot.getValue(Users.class);
                        userProfile.setValue(user);
                        Log.d(TAG, "User profile loaded successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user profile", e);
                        errorMessage.setValue("Error loading user profile");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "User profile loading cancelled", error.toException());
                    errorMessage.setValue("Error: " + error.getMessage());
                }
            };
            userDatabaseRef.addListenerForSingleValueEvent(userProfileListener);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up user profile listener", e);
        }
    }

    // ============================================
    // Cashbook Management
    // ============================================

    /**
     * Load all cashbooks for the current user from Firebase
     */
    private void loadCashbooks() {
        if (userDatabaseRef == null) {
            Log.w(TAG, "Cannot load cashbooks: userDatabaseRef is null");
            return;
        }

        try {
            isLoading.setValue(true);

            cashbooksListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        List<CashbookModel> cashbookList = new ArrayList<>();
                        for (DataSnapshot cashbookSnapshot : snapshot.getChildren()) {
                            CashbookModel cashbook = cashbookSnapshot.getValue(CashbookModel.class);
                            if (cashbook != null) {
                                if (cashbook.getCashbookId() == null) {
                                    cashbook.setCashbookId(cashbookSnapshot.getKey());
                                }
                                cashbookList.add(cashbook);
                            }
                        }
                        cashbooks.setValue(cashbookList);
                        Log.d(TAG, "Loaded " + cashbookList.size() + " cashbooks");

                        if (!cashbookList.isEmpty() && currentCashbookId == null) {
                            switchCashbook(cashbookList.get(0).getCashbookId());
                        }
                        isLoading.setValue(false);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing cashbooks", e);
                        errorMessage.setValue("Error loading cashbooks: " + e.getMessage());
                        isLoading.setValue(false);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Cashbooks listener cancelled", error.toException());
                    errorMessage.setValue("Error: " + error.getMessage());
                    isLoading.setValue(false);
                }
            };
            userDatabaseRef.child("cashbooks").addValueEventListener(cashbooksListener);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up cashbooks listener", e);
            errorMessage.setValue("Error setting up cashbooks listener");
            isLoading.setValue(false);
        }
    }

    /**
     * Switch to a different cashbook and load its transactions
     */
    public void switchCashbook(String cashbookId) {
        if (userDatabaseRef == null || cashbookId == null) {
            Log.w(TAG, "Cannot switch cashbook: invalid parameters");
            return;
        }

        try {
            isLoading.setValue(true);
            currentCashbookId = cashbookId;

            List<CashbookModel> currentCashbooks = cashbooks.getValue();
            if (currentCashbooks != null) {
                for (CashbookModel book : currentCashbooks) {
                    if (book.getCashbookId() != null && book.getCashbookId().equals(cashbookId)) {
                        activeCashbookName.setValue(book.getName());
                        Log.d(TAG, "Switched to cashbook: " + book.getName());
                        break;
                    }
                }
            }

            if (previousTransactionsRef != null && transactionsListener != null) {
                previousTransactionsRef.removeEventListener(transactionsListener);
                Log.d(TAG, "Removed previous transactions listener");
            }

            DatabaseReference newTransactionsRef = userDatabaseRef.child("cashbooks").child(cashbookId).child("transactions");
            previousTransactionsRef = newTransactionsRef;

            transactionsListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        List<TransactionModel> transactionList = new ArrayList<>();
                        for (DataSnapshot transactionSnapshot : snapshot.getChildren()) {
                            TransactionModel transaction = transactionSnapshot.getValue(TransactionModel.class);
                            if (transaction != null) {
                                transaction.setTransactionId(transactionSnapshot.getKey());
                                transactionList.add(transaction);
                            }
                        }
                        Collections.sort(transactionList, (t1, t2) ->
                                Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                        transactions.setValue(transactionList);
                        Log.d(TAG, "Loaded " + transactionList.size() + " transactions");
                        isLoading.setValue(false);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing transactions", e);
                        errorMessage.setValue("Error loading transactions: " + e.getMessage());
                        isLoading.setValue(false);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Transactions listener cancelled", error.toException());
                    errorMessage.setValue("Error: " + error.getMessage());
                    isLoading.setValue(false);
                }
            };
            newTransactionsRef.addValueEventListener(transactionsListener);
        } catch (Exception e) {
            Log.e(TAG, "Error switching cashbook", e);
            errorMessage.setValue("Error switching cashbook: " + e.getMessage());
            isLoading.setValue(false);
        }
    }

    // ============================================
    // Data Updates
    // ============================================

    public void refreshData() {
        Log.d(TAG, "Refreshing data...");
        if (currentCashbookId != null) {
            switchCashbook(currentCashbookId);
        } else {
            loadCashbooks();
        }
    }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            if (userDatabaseRef != null) {
                if (cashbooksListener != null) {
                    userDatabaseRef.child("cashbooks").removeEventListener(cashbooksListener);
                    Log.d(TAG, "Removed cashbooks listener");
                }
                if (transactionsListener != null && previousTransactionsRef != null) {
                    previousTransactionsRef.removeEventListener(transactionsListener);
                    Log.d(TAG, "Removed transactions listener");
                }
                if (userProfileListener != null) {
                    userDatabaseRef.removeEventListener(userProfileListener);
                    Log.d(TAG, "Removed user profile listener");
                }
            }
            Log.d(TAG, "ViewModel cleaned up successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up ViewModel", e);
        }
    }
}