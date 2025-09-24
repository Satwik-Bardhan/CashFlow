package com.example.cashflow;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.cashflow.db.DataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * TransactionViewModel - ViewModel for managing transaction list and filtering
 * Handles loading, filtering, and managing transactions for both guest and authenticated users
 */
public class TransactionViewModel extends AndroidViewModel {

    private static final String TAG = "TransactionViewModel";

    private final DataRepository repository;
    private final boolean isGuest;
    private final String cashbookId;

    // LiveData for reactive UI updates
    private final MutableLiveData<List<TransactionModel>> allTransactions = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionModel>> filteredTransactions = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    public TransactionViewModel(@NonNull Application application, boolean isGuest, String cashbookId) {
        super(application);
        this.repository = DataRepository.getInstance(application);
        this.isGuest = isGuest;
        this.cashbookId = cashbookId;

        Log.d(TAG, "TransactionViewModel initialized, guest: " + isGuest + ", cashbook: " + cashbookId);

        // Initialize with empty lists
        allTransactions.setValue(new ArrayList<>());
        filteredTransactions.setValue(new ArrayList<>());

        loadTransactions();
    }

    /**
     * Gets the filtered transactions LiveData for UI observation
     * @return LiveData containing filtered transactions
     */
    public LiveData<List<TransactionModel>> getFilteredTransactions() {
        return filteredTransactions;
    }

    /**
     * Gets the all transactions LiveData for UI observation
     * @return LiveData containing all transactions
     */
    public LiveData<List<TransactionModel>> getAllTransactions() {
        return allTransactions;
    }

    /**
     * Gets the error message LiveData for UI observation
     * @return LiveData containing error messages
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the loading state LiveData for UI observation
     * @return LiveData containing loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Loads transactions from repository with proper error handling
     */
    private void loadTransactions() {
        Log.d(TAG, "Loading transactions...");
        isLoading.postValue(true);

        repository.getAllTransactions(isGuest, cashbookId,
                transactions -> {
                    Log.d(TAG, "Transactions loaded successfully: " + transactions.size() + " items");
                    allTransactions.postValue(transactions);
                    filteredTransactions.postValue(transactions); // Initially, show all
                    isLoading.postValue(false);
                    errorMessage.postValue(null); // Clear any previous errors
                },
                error -> {
                    Log.e(TAG, "Error loading transactions: " + error);
                    errorMessage.postValue(error);
                    isLoading.postValue(false);

                    // Set empty lists on error to prevent UI issues
                    allTransactions.postValue(new ArrayList<>());
                    filteredTransactions.postValue(new ArrayList<>());
                }
        );
    }

    /**
     * Refreshes transactions by reloading from repository
     */
    public void refreshTransactions() {
        Log.d(TAG, "Refreshing transactions...");
        loadTransactions();
    }

    /**
     * Filters transactions based on multiple criteria
     * @param query Search query for category, party name, or remarks
     * @param startDate Start date for filtering (0 for no filter)
     * @param endDate End date for filtering (0 for no filter)
     * @param entryType Entry type filter ("All", "IN", "OUT")
     * @param categories List of categories to filter by (empty for no filter)
     * @param paymentModes List of payment modes to filter by (empty for no filter)
     */
    public void filter(String query, long startDate, long endDate, String entryType,
                       List<String> categories, List<String> paymentModes) {

        List<TransactionModel> originalList = allTransactions.getValue();
        if (originalList == null || originalList.isEmpty()) {
            Log.w(TAG, "No transactions to filter");
            filteredTransactions.postValue(new ArrayList<>());
            return;
        }

        Log.d(TAG, "Applying filters - Query: " + query + ", Type: " + entryType +
                ", Categories: " + categories.size() + ", Modes: " + paymentModes.size());

        try {
            // Ensure query is not null
            String searchQuery = (query != null) ? query.toLowerCase(Locale.getDefault()).trim() : "";

            // Ensure lists are not null
            List<String> safeCategories = (categories != null) ? categories : new ArrayList<>();
            List<String> safePaymentModes = (paymentModes != null) ? paymentModes : new ArrayList<>();

            List<TransactionModel> filteredList = originalList.stream()
                    .filter(transaction -> {
                        if (transaction == null) return false;

                        // Search Query Filter
                        boolean matchesSearch = searchQuery.isEmpty() ||
                                (transaction.getTransactionCategory() != null &&
                                        transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(searchQuery)) ||
                                (transaction.getPartyName() != null &&
                                        transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(searchQuery)) ||
                                (transaction.getRemark() != null &&
                                        transaction.getRemark().toLowerCase(Locale.getDefault()).contains(searchQuery));

                        // Date Filter
                        boolean matchesDate = (startDate == 0 && endDate == 0) ||
                                (transaction.getTimestamp() >= startDate && transaction.getTimestamp() <= endDate);

                        // Entry Type Filter
                        boolean matchesEntryType = "All".equalsIgnoreCase(entryType) ||
                                (entryType != null && entryType.equalsIgnoreCase(transaction.getType()));

                        // Category Filter
                        boolean matchesCategory = safeCategories.isEmpty() ||
                                safeCategories.contains(transaction.getTransactionCategory());

                        // Payment Mode Filter
                        boolean matchesPaymentMode = safePaymentModes.isEmpty() ||
                                safePaymentModes.contains(transaction.getPaymentMode());

                        return matchesSearch && matchesDate && matchesEntryType && matchesCategory && matchesPaymentMode;
                    })
                    .collect(Collectors.toList());

            Log.d(TAG, "Filter applied: " + filteredList.size() + " transactions match criteria");
            filteredTransactions.postValue(filteredList);

        } catch (Exception e) {
            Log.e(TAG, "Error applying filters", e);
            // On error, show all transactions
            filteredTransactions.postValue(originalList);
        }
    }

    /**
     * Clears all filters and shows all transactions
     */
    public void clearFilters() {
        Log.d(TAG, "Clearing all filters");
        List<TransactionModel> originalList = allTransactions.getValue();
        if (originalList != null) {
            filteredTransactions.postValue(originalList);
        }
    }

    /**
     * Gets count of filtered transactions
     * @return Number of filtered transactions
     */
    public int getFilteredTransactionCount() {
        List<TransactionModel> filtered = filteredTransactions.getValue();
        return (filtered != null) ? filtered.size() : 0;
    }

    /**
     * Gets count of all transactions
     * @return Total number of transactions
     */
    public int getAllTransactionCount() {
        List<TransactionModel> all = allTransactions.getValue();
        return (all != null) ? all.size() : 0;
    }

    /**
     * Calculates total income from filtered transactions
     * @return Total income amount
     */
    public double getTotalIncome() {
        List<TransactionModel> filtered = filteredTransactions.getValue();
        if (filtered == null || filtered.isEmpty()) return 0.0;

        return filtered.stream()
                .filter(t -> "IN".equalsIgnoreCase(t.getType()))
                .mapToDouble(TransactionModel::getAmount)
                .sum();
    }

    /**
     * Calculates total expenses from filtered transactions
     * @return Total expense amount
     */
    public double getTotalExpense() {
        List<TransactionModel> filtered = filteredTransactions.getValue();
        if (filtered == null || filtered.isEmpty()) return 0.0;

        return filtered.stream()
                .filter(t -> "OUT".equalsIgnoreCase(t.getType()))
                .mapToDouble(TransactionModel::getAmount)
                .sum();
    }

    /**
     * Calculates net balance from filtered transactions
     * @return Net balance (income - expense)
     */
    public double getNetBalance() {
        return getTotalIncome() - getTotalExpense();
    }

    /**
     * Gets the current mode (guest or authenticated)
     * @return true if in guest mode, false if authenticated
     */
    public boolean isGuestMode() {
        return isGuest;
    }

    /**
     * Gets the current cashbook ID
     * @return Cashbook ID
     */
    public String getCashbookId() {
        return cashbookId;
    }

    /**
     * Cleanup method called when ViewModel is cleared
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "TransactionViewModel cleared");
    }
}
