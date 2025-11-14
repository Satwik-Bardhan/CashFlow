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
import android.widget.ProgressBar; // [FIX] Added ProgressBar
import android.widget.Toast; // [FIX] Added Toast

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cashflow.CashbookModel;
import com.example.cashflow.R;
import com.example.cashflow.adapters.CashbookAdapter;
import com.example.cashflow.utils.ErrorHandler; // [FIX] Added ErrorHandler
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
import java.util.stream.Collectors; // [FIX] Added for filtering

public class CashbookSwitchDialog extends DialogFragment {

    private static final String TAG = "CashbookSwitchDialog";

    // [FIX] Corrected view IDs from dialog_switch_cashbook.xml
    private RecyclerView cashbookRecyclerView;
    private EditText searchCashbook;
    private LinearLayout emptyStateDialog;
    private ProgressBar loadingStateDialog; // [FIX] Corrected to ProgressBar
    private Button cancelDialogButton;
    private Button confirmDialogButton;
    private ImageView closeDialog;

    private CashbookAdapter adapter;
    private final List<CashbookModel> allCashbooks = new ArrayList<>();
    private CashbookModel selectedCashbook;
    private String currentCashbookId; // [FIX] Added to know current book

    private OnCashbookSelectedListener listener;

    public interface OnCashbookSelectedListener {
        void onCashbookSelected(CashbookModel cashbook);
    }

    public void setListener(OnCashbookSelectedListener listener) {
        this.listener = listener;
    }

    // [FIX] Added newInstance to pass current cashbook ID
    public static CashbookSwitchDialog newInstance(String currentCashbookId) {
        CashbookSwitchDialog dialog = new CashbookSwitchDialog();
        Bundle args = new Bundle();
        args.putString("current_cashbook_id", currentCashbookId);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentCashbookId = getArguments().getString("current_cashbook_id");
        }
        // [FIX] Set a dialog style
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.Theme_CashFlow_Dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // [FIX] Inflate the correct layout
        View view = inflater.inflate(R.layout.dialog_cashbook_switcher, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupRecyclerView();
        setupListeners();
        loadCashbooks();
    }

    private void initViews(View view) {
        // [FIX] Use correct IDs from dialog_cashbook_switcher.xml
        cashbookRecyclerView = view.findViewById(R.id.cashbookDialogRecyclerView);
        searchCashbook = view.findViewById(R.id.searchCashbook);
        emptyStateDialog = view.findViewById(R.id.emptyStateDialog);
        loadingStateDialog = view.findViewById(R.id.loadingStateDialog);
        cancelDialogButton = view.findViewById(R.id.cancelDialogButton);
        confirmDialogButton = view.findViewById(R.id.confirmDialogButton);
        closeDialog = view.findViewById(R.id.closeDialog);

        // [FIX] Disable confirm button initially
        confirmDialogButton.setEnabled(false);
        confirmDialogButton.setAlpha(0.5f);
        Log.d(TAG, "Views initialized");
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new CashbookAdapter(getContext(), allCashbooks, new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(CashbookModel cashbook) {
                if (cashbook.isCurrent()) {
                    // If user clicks the current book, just dismiss
                    dismiss();
                    return;
                }
                selectedCashbook = cashbook;
                confirmDialogButton.setEnabled(true);
                confirmDialogButton.setAlpha(1.0f);

                // [FIX] Update adapter to show selection
                for (CashbookModel cb : allCashbooks) {
                    cb.setCurrent(cb.getCashbookId().equals(cashbook.getCashbookId()));
                }
                adapter.updateCashbooks(allCashbooks);
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
                                // [FIX] Set 'isCurrent' flag
                                cashbook.setCurrent(cashbook.getCashbookId().equals(currentCashbookId));
                                allCashbooks.add(cashbook);
                            }
                        }

                        showLoading(false);
                        if (allCashbooks.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            // [FIX] Use the correct adapter update method
                            adapter.updateCashbooks(allCashbooks);
                        }

                        Log.d(TAG, "Loaded " + allCashbooks.size() + " cashbooks");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        if(getContext() != null) {
                            ErrorHandler.handleFirebaseError(getContext(), error);
                        }
                    }
                });
    }

    private void filterCashbooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateCashbooks(allCashbooks);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();

        List<CashbookModel> filtered = allCashbooks.stream()
                .filter(cashbook -> (cashbook.getName() != null && cashbook.getName().toLowerCase().contains(lowerQuery)) ||
                        (cashbook.getDescription() != null && cashbook.getDescription().toLowerCase().contains(lowerQuery)))
                .collect(Collectors.toList());

        adapter.updateCashbooks(filtered);
    }

    private void showLoading(boolean show) {
        loadingStateDialog.setVisibility(show ? View.VISIBLE : View.GONE);
        cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyStateDialog.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        emptyStateDialog.setVisibility(show ? View.VISIBLE : View.GONE);
        cashbookRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        loadingStateDialog.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        } else if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}