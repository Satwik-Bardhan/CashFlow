package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.cashflow.db.DataRepository;

import java.util.UUID;

/**
 * CashInOutViewModel - ViewModel for handling transaction creation and editing
 * Manages authenticated mode (Firebase) transactions.
 * Guest mode logic has been removed.
 */
public class CashInOutViewModel extends AndroidViewModel {

    private static final String TAG = "CashInOutViewModel";

    private final DataRepository repository;

    public CashInOutViewModel(@NonNull Application application) {
        super(application);
        repository = DataRepository.getInstance(application);
        Log.d(TAG, "CashInOutViewModel initialized for authenticated user.");
    }

    /**
     * Saves a transaction for an authenticated user to Firebase.
     * @param cashbookId The ID of the cashbook
     * @param transaction The transaction to save
     */
    public void saveTransaction(String cashbookId, TransactionModel transaction) {
        if (!isTransactionValid(transaction)) {
            Log.e(TAG, "Invalid transaction. Save aborted.");
            return;
        }

        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot save Firebase transaction");
            return;
        }

        repository.addTransaction(cashbookId, transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction saved successfully");
                } else {
                    Log.e(TAG, "Failed to save Firebase transaction");
                }
            }
        });
    }

    /**
     * Updates an existing transaction in Firebase.
     * @param cashbookId The ID of the cashbook
     * @param transaction The transaction to update
     */
    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (transaction == null || transaction.getTransactionId() == null) {
            Log.e(TAG, "Transaction or transaction ID is null, cannot update");
            return;
        }

        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot update Firebase transaction");
            return;
        }

        repository.updateTransaction(cashbookId, transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction updated successfully");
                } else {
                    Log.e(TAG, "Failed to update Firebase transaction");
                }
            }
        });
    }

    /**
     * Deletes a transaction from Firebase.
     * @param cashbookId The ID of the cashbook
     * @param transactionId The ID of the transaction to delete
     */
    public void deleteTransaction(String cashbookId, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            Log.e(TAG, "Transaction ID is null or empty, cannot delete");
            return;
        }

        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot delete Firebase transaction");
            return;
        }

        repository.deleteTransaction(cashbookId, transactionId, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction deleted successfully");
                } else {
                    Log.e(TAG, "Failed to delete Firebase transaction");
                }
            }
        });
    }

    /**
     * Validates transaction data before saving
     * @param transaction The transaction to validate
     * @return true if valid, false otherwise
     */
    public boolean isTransactionValid(TransactionModel transaction) {
        if (transaction == null) {
            Log.w(TAG, "Transaction validation failed: transaction is null");
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

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "CashInOutViewModel cleared");
    }
}