package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.cashflow.db.DataRepository;

import java.util.UUID;

/**
 * CashInOutViewModel - ViewModel for handling transaction creation and editing
 * Manages both guest mode (local SQLite) and authenticated mode (Firebase) transactions
 */
public class CashInOutViewModel extends AndroidViewModel {

    private static final String TAG = "CashInOutViewModel";

    private final DataRepository repository;
    private final boolean isGuest;

    public CashInOutViewModel(@NonNull Application application, boolean isGuest) {
        super(application);
        repository = DataRepository.getInstance(application);
        this.isGuest = isGuest;

        Log.d(TAG, "CashInOutViewModel initialized, guest mode: " + isGuest);
    }

    /**
     * Saves a transaction based on user authentication status
     * @param cashbookId The ID of the cashbook (ignored for guest users)
     * @param transaction The transaction to save
     */
    public void saveTransaction(String cashbookId, TransactionModel transaction) {
        if (transaction == null) {
            Log.e(TAG, "Transaction is null, cannot save");
            return;
        }

        if (isGuest) {
            saveGuestTransaction(transaction);
        } else {
            saveFirebaseTransaction(cashbookId, transaction);
        }
    }

    /**
     * Saves transaction for guest users (local SQLite database)
     * @param transaction The transaction to save
     */
    private void saveGuestTransaction(TransactionModel transaction) {
        // For guests, generate a unique ID locally if not present
        if (transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty()) {
            transaction.setTransactionId(UUID.randomUUID().toString());
        }

        repository.addGuestTransaction(transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Guest transaction saved successfully");
                    // You can add additional success handling here
                    // For example, notify the UI through LiveData
                } else {
                    Log.e(TAG, "Failed to save guest transaction");
                    // Handle failure - maybe show error message to user
                }
            }
        });
    }

    /**
     * Saves transaction for authenticated users (Firebase)
     * @param cashbookId The ID of the target cashbook
     * @param transaction The transaction to save
     */
    private void saveFirebaseTransaction(String cashbookId, TransactionModel transaction) {
        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot save Firebase transaction");
            return;
        }

        repository.addFirebaseTransaction(cashbookId, transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction saved successfully");
                    // You can add additional success handling here
                } else {
                    Log.e(TAG, "Failed to save Firebase transaction");
                    // Handle failure
                }
            }
        });
    }

    /**
     * Updates an existing transaction
     * @param cashbookId The ID of the cashbook (ignored for guest users)
     * @param transaction The transaction to update
     */
    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (transaction == null || transaction.getTransactionId() == null) {
            Log.e(TAG, "Transaction or transaction ID is null, cannot update");
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
                } else {
                    Log.e(TAG, "Failed to update guest transaction");
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
                } else {
                    Log.e(TAG, "Failed to update Firebase transaction");
                }
            }
        });
    }

    /**
     * Deletes a transaction
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
                } else {
                    Log.e(TAG, "Failed to delete guest transaction");
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
        // Clean up resources if needed
        Log.d(TAG, "CashInOutViewModel cleared");
    }
}
