package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.cashflow.db.DataRepository;

/**
 * TransactionDetailViewModel - ViewModel for managing transaction details
 * Handles updating and deleting transactions for both guest and authenticated users
 */
public class TransactionDetailViewModel extends AndroidViewModel {

    private static final String TAG = "TransactionDetailViewModel";

    private final DataRepository repository;
    private final boolean isGuest;

    public TransactionDetailViewModel(@NonNull Application application, boolean isGuest) {
        super(application);
        this.repository = DataRepository.getInstance(application);
        this.isGuest = isGuest;

        Log.d(TAG, "TransactionDetailViewModel initialized, guest mode: " + isGuest);
    }

    /**
     * Updates a transaction based on user authentication status
     * @param cashbookId The ID of the cashbook (ignored for guest users)
     * @param transaction The transaction to update
     */
    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (transaction == null) {
            Log.e(TAG, "Transaction is null, cannot update");
            return;
        }

        if (transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty()) {
            Log.e(TAG, "Transaction ID is null or empty, cannot update");
            return;
        }

        if (isGuest) {
            updateGuestTransaction(transaction);
        } else {
            updateFirebaseTransaction(cashbookId, transaction);
        }
    }

    /**
     * Updates guest transaction in local SQLite database
     * @param transaction The transaction to update
     */
    private void updateGuestTransaction(TransactionModel transaction) {
        repository.updateGuestTransaction(transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Guest transaction updated successfully");
                    // Handle success - you can add UI feedback here
                } else {
                    Log.e(TAG, "Failed to update guest transaction");
                    // Handle error - you can show error message to user
                }
            }
        });
    }

    /**
     * Updates Firebase transaction
     * @param cashbookId The ID of the cashbook
     * @param transaction The transaction to update
     */
    private void updateFirebaseTransaction(String cashbookId, TransactionModel transaction) {
        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot update Firebase transaction");
            return;
        }

        repository.updateFirebaseTransaction(cashbookId, transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction updated successfully");
                    // Handle success
                } else {
                    Log.e(TAG, "Failed to update Firebase transaction");
                    // Handle error
                }
            }
        });
    }

    /**
     * Deletes a transaction based on user authentication status
     * @param cashbookId The ID of the cashbook (ignored for guest users)
     * @param transactionId The ID of the transaction to delete
     */
    public void deleteTransaction(String cashbookId, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            Log.e(TAG, "Transaction ID is null or empty, cannot delete");
            return;
        }

        if (isGuest) {
            deleteGuestTransaction(transactionId);
        } else {
            deleteFirebaseTransaction(cashbookId, transactionId);
        }
    }

    /**
     * Deletes guest transaction from local SQLite database
     * @param transactionId The ID of the transaction to delete
     */
    private void deleteGuestTransaction(String transactionId) {
        repository.deleteGuestTransaction(transactionId, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Guest transaction deleted successfully");
                    // Handle success - maybe navigate back or refresh UI
                } else {
                    Log.e(TAG, "Failed to delete guest transaction");
                    // Handle error
                }
            }
        });
    }

    /**
     * Deletes Firebase transaction
     * @param cashbookId The ID of the cashbook
     * @param transactionId The ID of the transaction to delete
     */
    private void deleteFirebaseTransaction(String cashbookId, String transactionId) {
        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot delete Firebase transaction");
            return;
        }

        repository.deleteFirebaseTransaction(cashbookId, transactionId, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction deleted successfully");
                    // Handle success
                } else {
                    Log.e(TAG, "Failed to delete Firebase transaction");
                    // Handle error
                }
            }
        });
    }

    /**
     * Validates transaction data before updating
     * @param transaction The transaction to validate
     * @return true if valid, false otherwise
     */
    public boolean isTransactionValid(TransactionModel transaction) {
        if (transaction == null) {
            Log.w(TAG, "Transaction validation failed: transaction is null");
            return false;
        }

        if (transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty()) {
            Log.w(TAG, "Transaction validation failed: transaction ID is empty");
            return false;
        }

        if (transaction.getAmount() <= 0) {
            Log.w(TAG, "Transaction validation failed: invalid amount");
            return false;
        }

        if (transaction.getTransactionCategory() == null || transaction.getTransactionCategory().trim().isEmpty()) {
            Log.w(TAG, "Transaction validation failed: category is empty");
            return false;
        }

        if (transaction.getType() == null ||
                (!transaction.getType().equals("IN") && !transaction.getType().equals("OUT"))) {
            Log.w(TAG, "Transaction validation failed: invalid type");
            return false;
        }

        return true;
    }

    /**
     * Gets the current mode (guest or authenticated)
     * @return true if in guest mode, false if authenticated
     */
    public boolean isGuestMode() {
        return isGuest;
    }

    /**
     * Cleanup method called when ViewModel is cleared
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "TransactionDetailViewModel cleared");
    }
}
