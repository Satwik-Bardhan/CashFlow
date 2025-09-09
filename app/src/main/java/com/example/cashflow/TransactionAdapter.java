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

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction);
    }

    public TransactionAdapter(List<TransactionModel> transactionList, OnItemClickListener listener) {
        this.transactionList = transactionList;
        this.listener = listener;
        Log.d(TAG, "TransactionAdapter initialized with " + transactionList.size() + " transactions");
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction_in, parent, false);
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
        int count = transactionList != null ? transactionList.size() : 0;
        Log.d(TAG, "getItemCount() returning: " + count);
        return count;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateTransactions(List<TransactionModel> newTransactions) {
        if (newTransactions != null) {
            this.transactionList = newTransactions;
            Log.d(TAG, "updateTransactions: Updated with " + newTransactions.size() + " transactions");
            notifyDataSetChanged();
        }
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, amountTextView, partyTextView, dateTextView,
                remarkTextView, paymentModeTextView, transactionTimeTextView;

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
        }

        @SuppressLint("SetTextI18n")
        void bind(final TransactionModel transaction) {
            if (transaction == null) {
                Log.w(TAG, "Binding null transaction at position: " + getAdapterPosition());
                return;
            }

            try {
                // Set transaction category/title
                String category = transaction.getTransactionCategory();
                titleTextView.setText(category != null ? category : "Unknown Category");

                // Set payment mode with color coding
                // In your TransactionAdapter.java bind() method:

                String paymentMode = transaction.getPaymentMode();
                paymentModeTextView.setText(paymentMode != null ? paymentMode : "N/A");

                if ("Cash".equalsIgnoreCase(paymentMode)) {
                    // Use your blue drawable
                    paymentModeTextView.setBackground(ContextCompat.getDrawable(itemView.getContext(), R.drawable.button_blue));
                }



                // Set party name
                String partyName = transaction.getPartyName();
                partyTextView.setText(partyName != null ? partyName : "No Party");

                // Set amount and color based on transaction type
                double amount = transaction.getAmount();
                String formattedAmount = "₹" + String.format(Locale.US, "%.2f", amount);
                amountTextView.setText(formattedAmount);

                if ("IN".equalsIgnoreCase(transaction.getType())) {
                    amountTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.income_green));
                } else {
                    amountTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.expense_red));
                }

                // Format and display timestamp
                formatAndDisplayDateTime(transaction.getTimestamp());

                // Handle remark visibility
                handleRemarkDisplay(transaction.getRemark());

                // Set up click listener
                setupClickListener(transaction);

            } catch (Exception e) {
                Log.e(TAG, "Error binding transaction: " + e.getMessage(), e);
            }
        }

        private void formatAndDisplayDateTime(long timestamp) {
            if (timestamp > 0) {
                try {
                    Date date = new Date(timestamp);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);

                    dateTextView.setText(dateFormat.format(date));
                    transactionTimeTextView.setText(timeFormat.format(date));
                } catch (Exception e) {
                    Log.e(TAG, "Error formatting date: " + e.getMessage());
                    dateTextView.setText("Invalid Date");
                    transactionTimeTextView.setText("--:--");
                }
            } else {
                dateTextView.setText("No Date");
                transactionTimeTextView.setText("--:--");
            }
        }

        private void handleRemarkDisplay(String remark) {
            if (!TextUtils.isEmpty(remark) && !remark.trim().isEmpty()) {
                remarkTextView.setText(remark);
                remarkTextView.setVisibility(View.VISIBLE);
            } else {
                remarkTextView.setVisibility(View.GONE);
            }
        }

        private void setupClickListener(final TransactionModel transaction) {
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    Log.d(TAG, "Transaction clicked: " + transaction.getTransactionCategory());
                    listener.onItemClick(transaction);
                }
            });

            itemView.setClickable(true);
            itemView.setFocusable(true);
        }
    }
}
