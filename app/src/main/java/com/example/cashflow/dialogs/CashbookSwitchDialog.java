package com.example.cashflow.dialogs;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cashflow.CashbookModel;
import com.example.cashflow.R;
import com.example.cashflow.adapters.CashbookAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CashbookSwitchDialog extends DialogFragment {

    private static final String TAG = "CashbookSwitchDialog";

    private RecyclerView cashbookRecyclerView;
    private EditText searchCashbook;
    private LinearLayout emptyStateDialog;
    private LinearLayout loadingStateDialog;
    private Button cancelDialogButton;
    private Button confirmDialogButton;
    private ImageView closeDialog;

    private CashbookAdapter adapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private CashbookModel selectedCashbook;

    private OnCashbookSelectedListener listener;

    public interface OnCashbookSelectedListener {
        void onCashbookSelected(CashbookModel cashbook);
    }

    public void setListener(OnCashbookSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_switch_cashbook, container, false);

        initViews(view);
        setupRecyclerView();
        setupListeners();
        loadCashbooks();

        return view;
    }

    private void initViews(View view) {
        cashbookRecyclerView = view.findViewById(R.id.cashbookDialogRecyclerView1);
        searchCashbook = view.findViewById(R.id.searchCashbook1);
        emptyStateDialog = view.findViewById(R.id.emptyStateDialog1);
        loadingStateDialog = view.findViewById(R.id.loadingStateDialog1);
        cancelDialogButton = view.findViewById(R.id.cancelDialogButton1);
        confirmDialogButton = view.findViewById(R.id.confirmDialogButton1);
        closeDialog = view.findViewById(R.id.closeDialog1);

        Log.d(TAG, "Views initialized");
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new CashbookAdapter(getContext(), allCashbooks, new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(CashbookModel cashbook) {
                selectedCashbook = cashbook;
                confirmDialogButton.setEnabled(true);
                confirmDialogButton.setAlpha(1.0f);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFavoriteClick(CashbookModel cashbook) {
                // Not used in dialog
            }

            @Override
            public void onMenuClick(CashbookModel cashbook, View anchorView) {
                // Not used in dialog
            }
        });

        cashbookRecyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        closeDialog.setOnClickListener(v -> dismiss());

        cancelDialogButton.setOnClickListener(v -> dismiss());

        confirmDialogButton.setOnClickListener(v -> {
            if (selectedCashbook != null && listener != null) {
                listener.onCashbookSelected(selectedCashbook);
                dismiss();
            }
        });

        searchCashbook.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCashbooks(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCashbooks() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showError("Not authenticated");
            return;
        }

        showLoading(true);
        String userId = currentUser.getUid();

        FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("cashbooks")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        allCashbooks.clear();

                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            CashbookModel cashbook = snapshot.getValue(CashbookModel.class);
                            if (cashbook != null) {
                                if (cashbook.getCashbookId() == null) {
                                    cashbook.setCashbookId(snapshot.getKey());
                                }
                                allCashbooks.add(cashbook);
                            }
                        }

                        showLoading(false);
                        if (allCashbooks.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            adapter.updateList(allCashbooks);
                        }

                        Log.d(TAG, "Loaded " + allCashbooks.size() + " cashbooks");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        showError("Error: " + error.getMessage());
                    }
                });
    }

    private void filterCashbooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateList(allCashbooks);
            return;
        }

        List<CashbookModel> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (CashbookModel cashbook : allCashbooks) {
            if (cashbook.getName().toLowerCase().contains(lowerQuery) ||
                    (cashbook.getDescription() != null &&
                            cashbook.getDescription().toLowerCase().contains(lowerQuery))) {
                filtered.add(cashbook);
            }
        }

        adapter.updateList(filtered);
    }

    private void showLoading(boolean show) {
        loadingStateDialog.setVisibility(show ? View.VISIBLE : View.GONE);
        cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        emptyStateDialog.setVisibility(show ? View.VISIBLE : View.GONE);
        cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        }
    }
}
