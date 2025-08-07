package com.example.cashflow;

import android.annotation.SuppressLint;
import android.text.TextUtils;
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

    private final List<TransactionModel> transactionList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction);
    }

    public TransactionAdapter(List<TransactionModel> transactionList, OnItemClickListener listener) {
        this.transactionList = transactionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionModel transaction = transactionList.get(position);
        holder.bind(transaction);
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, amountTextView, partyTextView, dateTextView, remarkTextView, paymentModeTextView, transactionTimeTextView;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
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
            titleTextView.setText(transaction.getTransactionCategory());
            paymentModeTextView.setText(transaction.getPaymentMode());
            partyTextView.setText(transaction.getPartyName());

            // Set amount and color based on transaction type
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                amountTextView.setText("₹" + String.format(Locale.US, "%.2f", transaction.getAmount()));
                amountTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.income_green));
            } else {
                amountTextView.setText("₹" + String.format(Locale.US, "%.2f", transaction.getAmount()));
                amountTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.expense_red));
            }

            // Format timestamp into separate date and time strings
            if (transaction.getTimestamp() > 0) {
                Date date = new Date(transaction.getTimestamp());
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                dateTextView.setText(dateFormat.format(date));
                transactionTimeTextView.setText(timeFormat.format(date));
            }

            // Show remark only if it exists
            if (!TextUtils.isEmpty(transaction.getRemark())) {
                remarkTextView.setText(transaction.getRemark());
                remarkTextView.setVisibility(View.VISIBLE);
            } else {
                remarkTextView.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(transaction);
                }
            });
        }
    }
}
