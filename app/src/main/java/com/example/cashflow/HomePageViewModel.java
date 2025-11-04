package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.cashflow.db.GuestDbHelper;
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
 * - Manage cashbooks and transactions data
 * - Handle Firebase data loading and caching
 * - Support both guest and authenticated user modes
 * - Provide LiveData for UI observation
 *
 * Updated: November 2025 - Enhanced with CashbookModel integration
 */
public class HomePageViewModel extends AndroidViewModel {

    private static final String TAG = "HomePageViewModel";

    // State variables
    private final boolean isGuest;
    private final GuestDbHelper dbHelper;
    private final DatabaseReference userCashbooksRef;
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
    public HomePageViewModel(@NonNull Application application, boolean isGuest) {
        super(application);
        this.isGuest = isGuest;
        this.dbHelper = new GuestDbHelper(application);
        this.transactions.setValue(new ArrayList<>());
        this.cashbooks.setValue(new ArrayList<>());

        Log.d(TAG, "ViewModel initialized - isGuest: " + isGuest);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (!isGuest && currentUser != null) {
            userCashbooksRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("cashbooks");
            loadUserProfile(currentUser.getUid());
            loadCashbooks();
        } else {
            userCashbooksRef = null;
            loadGuestData();
        }
    }

    /**
     * Constructor with cashbook ID
     */
    public HomePageViewModel(@NonNull Application application, boolean isGuest, String cashbookId) {
        this(application, isGuest);
        this.currentCashbookId = cashbookId;

        if (!isGuest && currentCashbookId != null) {
            // Directly switch to the provided cashbook
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
    // Guest Mode
    // ============================================

    /**
     * Load guest mode data from local database
     */
    private void loadGuestData() {
        try {
            isLoading.setValue(true);
            activeCashbookName.setValue("Guest Cashbook");

            List<TransactionModel> guestTransactions = dbHelper.getAllTransactions();
            transactions.setValue(guestTransactions != null ? guestTransactions : new ArrayList<>());

            Log.d(TAG, "Guest data loaded: " + (guestTransactions != null ? guestTransactions.size() : 0) + " transactions");
            isLoading.setValue(false);
        } catch (Exception e) {
            Log.e(TAG, "Error loading guest data", e);
            errorMessage.setValue("Error loading guest data: " + e.getMessage());
            isLoading.setValue(false);
        }
    }

    // ============================================
    // User Profile
    // ============================================

    /**
     * Load user profile information from Firebase
     */
    private void loadUserProfile(String userId) {
        try {
            DatabaseReference userProfileRef = FirebaseDatabase.getInstance()
                    .getReference("users").child(userId);

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

            userProfileRef.addListenerForSingleValueEvent(userProfileListener);
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
        if (userCashbooksRef == null) {
            Log.w(TAG, "Cannot load cashbooks: userCashbooksRef is null");
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
                                // Set the cashbook ID from Firebase key
                                if (cashbook.getCashbookId() == null) {
                                    cashbook.setCashbookId(cashbookSnapshot.getKey());
                                }
                                cashbookList.add(cashbook);
                                Log.d(TAG, "Loaded cashbook: " + cashbook.getName());
                            }
                        }

                        cashbooks.setValue(cashbookList);
                        Log.d(TAG, "Loaded " + cashbookList.size() + " cashbooks");

                        // Set first cashbook as active if none selected
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

            userCashbooksRef.addValueEventListener(cashbooksListener);
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
        if (isGuest || userCashbooksRef == null || cashbookId == null) {
            Log.w(TAG, "Cannot switch cashbook: invalid parameters");
            return;
        }

        try {
            isLoading.setValue(true);
            currentCashbookId = cashbookId;

            // Find and set the cashbook name
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

            // Remove old transactions listener
            if (previousTransactionsRef != null && transactionsListener != null) {
                previousTransactionsRef.removeEventListener(transactionsListener);
                Log.d(TAG, "Removed previous transactions listener");
            }

            // Setup new transactions listener
            DatabaseReference newTransactionsRef = userCashbooksRef.child(cashbookId).child("transactions");
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

                        // Sort transactions by timestamp, newest first
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

    /**
     * Refresh the current cashbook data
     */
    public void refreshData() {
        Log.d(TAG, "Refreshing data...");

        if (isGuest) {
            loadGuestData();
        } else if (currentCashbookId != null) {
            switchCashbook(currentCashbookId);
        } else {
            loadCashbooks();
        }
    }


    /**
     * Add a transaction to guest database
     */
    public void addGuestTransaction(TransactionModel transaction) {
        try {
            if (dbHelper != null) {
                dbHelper.addTransaction(transaction); // Assuming method exists
                Log.d(TAG, "Guest transaction added successfully");
                loadGuestData(); // Refresh the list
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding guest transaction", e);
            errorMessage.setValue("Error adding transaction: " + e.getMessage());
        }
    }

    /**
     * Delete a transaction from guest database
     */
    public void deleteGuestTransaction(String transactionId) {
        try {
            if (dbHelper != null) {
                dbHelper.deleteTransaction(transactionId); // Assuming method exists
                Log.d(TAG, "Guest transaction deleted successfully");
                loadGuestData(); // Refresh the list
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting guest transaction", e);
            errorMessage.setValue("Error deleting transaction: " + e.getMessage());
        }
    }



    public void clearError() {
        errorMessage.setValue(null);
    }

    /**
     * Set loading state manually
     */
    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    /**
     * Cleanup: Called when ViewModel is destroyed
     */
    @Override
    protected void onCleared() {
        super.onCleared();

        try {
            // Remove all Firebase listeners to prevent memory leaks
            if (userCashbooksRef != null) {
                if (cashbooksListener != null) {
                    userCashbooksRef.removeEventListener(cashbooksListener);
                    Log.d(TAG, "Removed cashbooks listener");
                }

                if (transactionsListener != null && previousTransactionsRef != null) {
                    previousTransactionsRef.removeEventListener(transactionsListener);
                    Log.d(TAG, "Removed transactions listener");
                }
            }

            if (userProfileListener != null) {
                DatabaseReference userRef = FirebaseDatabase.getInstance()
                        .getReference("users").child(FirebaseAuth.getInstance().getCurrentUser().getUid());
                userRef.removeEventListener(userProfileListener);
                Log.d(TAG, "Removed user profile listener");
            }

            // Close database helper if guest
            if (isGuest && dbHelper != null) {
                dbHelper.close();
                Log.d(TAG, "Closed guest database");
            }

            Log.d(TAG, "ViewModel cleaned up successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up ViewModel", e);
        }
    }
}
