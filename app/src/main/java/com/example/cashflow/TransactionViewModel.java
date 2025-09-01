package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.cashflow.db.DataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TransactionViewModel extends AndroidViewModel {

    private final DataRepository repository;
    private final boolean isGuest;
    private final String cashbookId;

    private final MutableLiveData<List<TransactionModel>> allTransactions = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionModel>> filteredTransactions = new MutableLiveData<>();

    public TransactionViewModel(@NonNull Application application, boolean isGuest, String cashbookId) {
        super(application);
        this.repository = DataRepository.getInstance(application);
        this.isGuest = isGuest;
        this.cashbookId = cashbookId;
        loadTransactions();
    }

    public LiveData<List<TransactionModel>> getFilteredTransactions() {
        return filteredTransactions;
    }

    private void loadTransactions() {
        repository.getAllTransactions(isGuest, cashbookId, transactions -> {
            allTransactions.postValue(transactions);
            filteredTransactions.postValue(transactions); // Initially, show all
        });
    }

    public void filter(String query, long startDate, long endDate, String entryType, List<String> categories, List<String> paymentModes) {
        List<TransactionModel> originalList = allTransactions.getValue();
        if (originalList == null) {
            return;
        }

        List<TransactionModel> filteredList = originalList.stream()
                .filter(transaction -> {
                    // Search Query Filter
                    boolean matchesSearch = query.isEmpty() ||
                            (transaction.getTransactionCategory() != null && transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
                            (transaction.getPartyName() != null && transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(query)) ||
                            (transaction.getRemark() != null && transaction.getRemark().toLowerCase(Locale.getDefault()).contains(query));

                    // Date Filter
                    boolean matchesDate = (startDate == 0 && endDate == 0) || (transaction.getTimestamp() >= startDate && transaction.getTimestamp() <= endDate);

                    // Entry Type Filter
                    boolean matchesEntryType = "All".equalsIgnoreCase(entryType) || (entryType != null && entryType.equalsIgnoreCase(transaction.getType()));

                    // Category Filter
                    boolean matchesCategory = categories.isEmpty() || categories.contains(transaction.getTransactionCategory());

                    // Payment Mode Filter
                    boolean matchesPaymentMode = paymentModes.isEmpty() || paymentModes.contains(transaction.getPaymentMode());

                    return matchesSearch && matchesDate && matchesEntryType && matchesCategory && matchesPaymentMode;
                })
                .collect(Collectors.toList());

        filteredTransactions.postValue(filteredList);
    }
}

