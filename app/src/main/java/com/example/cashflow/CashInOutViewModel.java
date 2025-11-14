package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.cashflow.db.DataRepository;

/**
 * CashInOutViewModel - ViewModel for handling transaction creation and editing
 * [FIX] Manages authenticated mode (Firebase) transactions only.
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
        if (!isTransactionValid(transaction, false)) { // [FIX] Use new validation flag
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
        if (!isTransactionValid(transaction, true)) { // [FIX] Use new validation flag
            Log.e(TAG, "Invalid transaction, update aborted.");
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
     * @param isUpdate Check for transactionId only if it's an update
     * @return true if valid, false otherwise
     */
    public boolean isTransactionValid(TransactionModel transaction, boolean isUpdate) {
        if (transaction == null) {
            Log.w(TAG, "Transaction validation failed: transaction is null");
            return false;
        }

        // [FIX] Only require transactionId if we are updating an existing entry
        if (isUpdate && (transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty())) {
            Log.w(TAG, "Transaction validation failed: transaction ID is empty for update");
            return false;
        }

        if (transaction.getAmount() <= 0) {
            Log.w(TAG, "Transaction validation failed: invalid amount");
            return false;
        }

        if (transaction.getTransactionCategory() == null ||
                transaction.getTransactionCategory().trim().isEmpty() ||
                transaction.getTransactionCategory().equals("Select Category")) {
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