package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class TransactionViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final String cashbookId;

    public TransactionViewModelFactory(Application application, String cashbookId) {
        this.application = application;
        this.cashbookId = cashbookId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TransactionViewModel.class)) {
            // [FIX] Removed isGuest, now only passes cashbookId
            return (T) new TransactionViewModel(application, cashbookId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}