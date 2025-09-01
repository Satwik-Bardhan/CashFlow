package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

// This Factory's only job is to create a TransactionViewModel
public class TransactionViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final boolean isGuest;
    private final String cashbookId;

    public TransactionViewModelFactory(Application application, boolean isGuest, String cashbookId) {
        this.application = application;
        this.isGuest = isGuest;
        this.cashbookId = cashbookId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TransactionViewModel.class)) {
            // This now correctly creates a TransactionViewModel
            return (T) new TransactionViewModel(application, isGuest, cashbookId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}

