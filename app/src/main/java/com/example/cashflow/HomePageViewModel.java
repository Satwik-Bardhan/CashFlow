package com.example.cashflow;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.cashflow.db.GuestDbHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomePageViewModel extends AndroidViewModel {

    private final boolean isGuest;
    private final GuestDbHelper dbHelper;
    private final DatabaseReference userCashbooksRef;

    private final MutableLiveData<List<TransactionModel>> transactions = new MutableLiveData<>();
    private final MutableLiveData<List<CashbookModel>> cashbooks = new MutableLiveData<>();
    private final MutableLiveData<String> activeCashbookName = new MutableLiveData<>();
    private final MutableLiveData<Users> userProfile = new MutableLiveData<>();

    private ValueEventListener transactionsListener;
    private ValueEventListener cashbooksListener;

    public HomePageViewModel(@NonNull Application application, boolean isGuest) {
        super(application);
        this.isGuest = isGuest;
        this.dbHelper = new GuestDbHelper(application);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (!isGuest && currentUser != null) {
            userCashbooksRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUser.getUid()).child("cashbooks");
            loadUserProfile(currentUser.getUid());
            loadCashbooks();
        } else {
            userCashbooksRef = null;
            loadGuestData();
        }
    }

    // LiveData Getters
    public LiveData<List<TransactionModel>> getTransactions() { return transactions; }
    public LiveData<List<CashbookModel>> getCashbooks() { return cashbooks; }
    public LiveData<String> getActiveCashbookName() { return activeCashbookName; }
    public LiveData<Users> getUserProfile() { return userProfile; }


    private void loadGuestData() {
        activeCashbookName.setValue("Guest Book");
        transactions.setValue(dbHelper.getAllTransactions());
    }

    private void loadUserProfile(String userId) {
        DatabaseReference userProfileRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userProfileRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userProfile.setValue(snapshot.getValue(Users.class));
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void loadCashbooks() {
        if (userCashbooksRef == null) return;
        cashbooksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<CashbookModel> cashbookList = new ArrayList<>();
                for (DataSnapshot cashbookSnapshot : snapshot.getChildren()) {
                    CashbookModel cashbook = cashbookSnapshot.getValue(CashbookModel.class);
                    if (cashbook != null) {
                        cashbook.setId(cashbookSnapshot.getKey());
                        cashbookList.add(cashbook);
                    }
                }
                cashbooks.setValue(cashbookList);
                // For simplicity, let's just make the first one active for now
                if (!cashbookList.isEmpty()) {
                    switchCashbook(cashbookList.get(0).getId());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { /* Handle error */ }
        };
        userCashbooksRef.addValueEventListener(cashbooksListener);
    }

    public void switchCashbook(String cashbookId) {
        if (isGuest || userCashbooksRef == null) return;

        // Find the name of the new active cashbook
        List<CashbookModel> currentCashbooks = cashbooks.getValue();
        if (currentCashbooks != null) {
            for (CashbookModel book : currentCashbooks) {
                if (book.getId().equals(cashbookId)) {
                    activeCashbookName.setValue(book.getName());
                    break;
                }
            }
        }


        // Remove previous listener to avoid multiple listeners
        if (transactionsListener != null) {
            // This requires storing the previous cashbook ref, for simplicity we'll just detach all for now
            userCashbooksRef.child(activeCashbookName.getValue()).child("transactions").removeEventListener(transactionsListener);
        }

        DatabaseReference newTransactionsRef = userCashbooksRef.child(cashbookId).child("transactions");
        transactionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TransactionModel> transactionList = new ArrayList<>();
                for (DataSnapshot transactionSnapshot : snapshot.getChildren()) {
                    transactionList.add(transactionSnapshot.getValue(TransactionModel.class));
                }
                // Sort transactions by timestamp, newest first
                Collections.sort(transactionList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                transactions.setValue(transactionList);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { /* Handle error */ }
        };
        newTransactionsRef.addValueEventListener(transactionsListener);
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up Firebase listeners to prevent memory leaks
        if (userCashbooksRef != null) {
            if(cashbooksListener != null) userCashbooksRef.removeEventListener(cashbooksListener);
            // You would also remove the transactions listener here
        }
    }
}

