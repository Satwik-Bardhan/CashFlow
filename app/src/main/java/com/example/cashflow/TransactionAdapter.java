package com.example.cashflow;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private static final String TAG = "TransactionAdapter";
    private List<TransactionModel> transactionList;
    private final OnItemClickListener listener;

    // --- [NEW] Define constants for the two view types ---
    private static final int VIEW_TYPE_IN = 1;
    private static final int VIEW_TYPE_OUT = 2;
    // --- End of New ---

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction);
    }

    public TransactionAdapter(List<TransactionModel> transactionList, OnItemClickListener listener) {
        this.transactionList = transactionList;
        this.listener = listener;
    }

    // --- [MODIFIED] This method now determines which layout to use ---
    @Override
    public int getItemViewType(int position) {
        TransactionModel transaction = transactionList.get(position);
        if ("IN".equalsIgnoreCase(transaction.getType())) {
            return VIEW_TYPE_IN;
        } else {
            return VIEW_TYPE_OUT;
        }
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        // --- [MODIFIED] Inflate the correct layout based on the viewType ---
        if (viewType == VIEW_TYPE_IN) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_in, parent, false);
        } else { // viewType == VIEW_TYPE_OUT
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_out, parent, false);
        }
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        if (position < transactionList.size()) {
            TransactionModel transaction = transactionList.get(position);
            holder.bind(transaction);
        }
    }

    @Override
    public int getItemCount() {
        return transactionList != null ? transactionList.size() : 0;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateTransactions(List<TransactionModel> newTransactions) {
        if (newTransactions != null) {
            this.transactionList = newTransactions;
            notifyDataSetChanged();
        }
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, amountTextView, partyTextView, dateTextView,
                remarkTextView, paymentModeTextView, transactionTimeTextView;
        View transactionTypeIndicator, remarkLayout; // Added remarkLayout

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            initializeViews();
        }

        private void initializeViews() {
            titleTextView = itemView.findViewById(R.id.titleTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
            partyTextView = itemView.findViewById(R.id.partyTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            remarkTextView = itemView.findViewById(R.id.remarkTextView);
            paymentModeTextView = itemView.findViewById(R.id.paymentModeTextView);
            transactionTimeTextView = itemView.findViewById(R.id.transactionTimeTextView);
            transactionTypeIndicator = itemView.findViewById(R.id.transactionTypeIndicator);
            remarkLayout = itemView.findViewById(R.id.remarkLayout); // Initialize remark layout
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void bind(final TransactionModel transaction) {
            if (transaction == null) {
                return;
            }

            // Set data
            titleTextView.setText(transaction.getTransactionCategory());
            partyTextView.setText(transaction.getPartyName() != null ? transaction.getPartyName() : "No Party");
            paymentModeTextView.setText(transaction.getPaymentMode());

            // Set amount and color based on type
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                amountTextView.setText("₹" + String.format("%.2f", transaction.getAmount()));
                amountTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.income_green)); // Assuming you have this color
                transactionTypeIndicator.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.income_green));
            } else {
                amountTextView.setText("- ₹" + String.format("%.2f", transaction.getAmount()));
                amountTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.expense_red)); // Assuming you have this color
                transactionTypeIndicator.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.expense_red));
            }

            // Format and display timestamp
            if (transaction.getTimestamp() > 0) {
                Date date = new Date(transaction.getTimestamp());
                dateTextView.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date));
                transactionTimeTextView.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(date));
            }

            // Handle remark visibility
            String remark = transaction.getRemark();
            if (!TextUtils.isEmpty(remark) && remarkLayout != null) {
                remarkTextView.setText(remark);
                remarkLayout.setVisibility(View.VISIBLE);
            } else if (remarkLayout != null) {
                remarkLayout.setVisibility(View.GONE);
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(transaction);
                }
            });
        }
    }
}