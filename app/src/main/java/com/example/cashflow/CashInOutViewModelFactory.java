package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class CashInOutViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final boolean isGuest;

    public CashInOutViewModelFactory(Application application, boolean isGuest) {
        this.application = application;
        this.isGuest = isGuest;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CashInOutViewModel.class)) {
            return (T) new CashInOutViewModel(application, isGuest);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
