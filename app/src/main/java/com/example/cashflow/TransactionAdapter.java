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
import android.graphics.Color;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final List<TransactionModel> transactionList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction, String transactionId);
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

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionModel transaction = transactionList.get(position);

        holder.titleTextView.setText(transaction.getTransactionCategory());

        holder.amountTextView.setText("₹" + String.format(Locale.US, "%.2f", transaction.getAmount()));
        if (transaction.getType().equalsIgnoreCase("OUT")) {
            holder.amountTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.homepage_expense_amount_red));
        } else {
            holder.amountTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.homepage_income_amount_green));
        }

        holder.paymentModeTextView.setText(transaction.getPaymentMode());
        holder.paymentModeTextView.setTextColor(Color.BLACK);

        holder.partyTextView.setText(transaction.getPartyName());
        holder.partyTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.secondary_text_color));

        holder.dateTextView.setText(transaction.getDate());
        holder.dateTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.secondary_text_color));

        if (!TextUtils.isEmpty(transaction.getRemark())) {
            holder.remarkTextView.setText(transaction.getRemark());
            holder.remarkTextView.setVisibility(View.VISIBLE);
        } else {
            holder.remarkTextView.setVisibility(View.GONE);
        }
        holder.remarkTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.secondary_text_color));

        if (transaction.getTimestamp() > 0) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            holder.transactionTimeTextView.setText(timeFormat.format(new Date(transaction.getTimestamp())));
            holder.transactionTimeTextView.setVisibility(View.VISIBLE);
        } else {
            holder.transactionTimeTextView.setVisibility(View.GONE);
        }
        holder.transactionTimeTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.secondary_text_color));


        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(transaction, transaction.getTransactionId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
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
    }
}