package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.cashflow.db.DataRepository;

import java.util.UUID;


public class CashInOutViewModel extends AndroidViewModel {

    private final DataRepository repository;
    private final boolean isGuest;

    public CashInOutViewModel(@NonNull Application application, boolean isGuest) {
        super(application);
        repository = DataRepository.getInstance(application);
        this.isGuest = isGuest;
        // The incorrect line "repository.setGuestMode(isGuest);" has been removed.
    }

    public void saveTransaction(String cashbookId, TransactionModel transaction) {
        if (isGuest) {
            // For guests, we need to generate a unique ID locally
            if(transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty()) {
                transaction.setTransactionId(UUID.randomUUID().toString());
            }
            repository.addGuestTransaction(transaction);
        } else {
            repository.addFirebaseTransaction(cashbookId, transaction);
        }
    }
}

