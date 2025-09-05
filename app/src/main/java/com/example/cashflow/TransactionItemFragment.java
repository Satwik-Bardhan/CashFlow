package com.example.cashflow;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TransactionItemFragment extends Fragment {

    private static final String TAG = "TransactionItemFragment";
    private RecyclerView transactionRecyclerView;
    private TransactionAdapter transactionAdapter;
    private List<TransactionModel> transactionList;
    private TransactionAdapter.OnItemClickListener clickListener;
    private TextView emptyStateText;

    public static TransactionItemFragment newInstance(ArrayList<TransactionModel> transactions) {
        TransactionItemFragment fragment = new TransactionItemFragment();
        Bundle args = new Bundle();
        args.putSerializable("transactions", transactions);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transaction_list, container, false);

        transactionRecyclerView = view.findViewById(R.id.transactionRecyclerView);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        if (getArguments() != null) {
            transactionList = (List<TransactionModel>) getArguments().getSerializable("transactions");
        }

        setupRecyclerView();

        Log.d(TAG, "Fragment created with " + (transactionList != null ? transactionList.size() : 0) + " transactions");

        return view;
    }

    private void setupRecyclerView() {
        if (transactionList == null) {
            transactionList = new ArrayList<>();
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        transactionRecyclerView.setLayoutManager(layoutManager);

        // IMPORTANT: Configure for unlimited item display
        transactionRecyclerView.setNestedScrollingEnabled(false);
        transactionRecyclerView.setHasFixedSize(false);

        transactionAdapter = new TransactionAdapter(transactionList, clickListener);
        transactionRecyclerView.setAdapter(transactionAdapter);

        // Update empty state
        updateEmptyState();

        Log.d(TAG, "RecyclerView setup with " + transactionList.size() + " transactions");
    }

    public void updateTransactions(List<TransactionModel> newTransactions) {
        if (newTransactions != null) {
            this.transactionList = newTransactions;

            if (transactionAdapter != null) {
                transactionAdapter.updateTransactions(newTransactions);
                updateEmptyState();
                Log.d(TAG, "Transactions updated: " + newTransactions.size() + " items");
            }
        }
    }

    private void updateEmptyState() {
        if (transactionList == null || transactionList.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            transactionRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            transactionRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    public void setOnItemClickListener(TransactionAdapter.OnItemClickListener listener) {
        this.clickListener = listener;
        if (transactionAdapter != null) {
            transactionAdapter = new TransactionAdapter(transactionList, listener);
            transactionRecyclerView.setAdapter(transactionAdapter);
        }
    }
}
