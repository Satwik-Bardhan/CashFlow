package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.cashflow.db.DataRepository;

public class TransactionDetailViewModel extends AndroidViewModel {

    private final DataRepository repository;
    private final boolean isGuest;

    public TransactionDetailViewModel(@NonNull Application application, boolean isGuest) {
        super(application);
        this.repository = DataRepository.getInstance(application);
        this.isGuest = isGuest;
        // The incorrect line "repository.setGuestMode(isGuest);" has been removed.
    }

    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (isGuest) {
            repository.updateGuestTransaction(transaction);
        } else {
            repository.updateFirebaseTransaction(cashbookId, transaction);
        }
    }

    public void deleteTransaction(String cashbookId, String transactionId) {
        if (isGuest) {
            repository.deleteGuestTransaction(transactionId);
        } else {
            repository.deleteFirebaseTransaction(cashbookId, transactionId);
        }
    }
}

