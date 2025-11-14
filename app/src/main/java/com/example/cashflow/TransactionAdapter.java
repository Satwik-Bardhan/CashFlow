package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil; // [FIX] Added for DiffUtil
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private static final String TAG = "TransactionAdapter";
    private List<TransactionModel> transactionList;
    private final OnItemClickListener listener;

    // [FIX] Define constants for the two view types
    private static final int VIEW_TYPE_IN = 1;
    private static final int VIEW_TYPE_OUT = 2;

    public interface OnItemClickListener {
        void onItemClick(TransactionModel transaction);
        // [FIX] Added for delete/edit buttons
        void onEditClick(TransactionModel transaction);
        void onDeleteClick(TransactionModel transaction);
        void onCopyClick(TransactionModel transaction);
    }

    public TransactionAdapter(List<TransactionModel> transactionList, OnItemClickListener listener) {
        this.transactionList = new ArrayList<>(transactionList); // [FIX] Create a new list
        this.listener = listener;
    }

    // [FIX] This method now determines which layout to use
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
        // [FIX] Inflate the correct layout based on the viewType
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

    // [FIX] Replaced with DiffUtil for better performance
    public void updateTransactions(List<TransactionModel> newTransactions) {
        if (newTransactions == null) {
            newTransactions = new ArrayList<>();
        }

        TransactionDiffCallback diffCallback = new TransactionDiffCallback(this.transactionList, newTransactions);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.transactionList.clear();
        this.transactionList.addAll(newTransactions);
        diffResult.dispatchUpdatesTo(this);
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, amountTextView, partyTextView, dateTextView,
                remarkTextView, paymentModeTextView, transactionTimeTextView;
        View transactionTypeIndicator, remarkLayout;
        ImageButton editButton, copyButton, deleteButton; // [FIX] Added buttons

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
            remarkLayout = itemView.findViewById(R.id.remarkLayout);

            // [FIX] Initialize buttons
            editButton = itemView.findViewById(R.id.editButton);
            copyButton = itemView.findViewById(R.id.copyButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        void bind(final TransactionModel transaction) {
            if (transaction == null) {
                return;
            }

            Context context = itemView.getContext();

            // Set data
            titleTextView.setText(transaction.getTransactionCategory());
            partyTextView.setText(transaction.getPartyName() != null ? transaction.getPartyName() : "No Party");
            paymentModeTextView.setText(transaction.getPaymentMode());

            // Set amount and color based on type
            if ("IN".equalsIgnoreCase(transaction.getType())) {
                amountTextView.setText("₹" + String.format("%.2f", transaction.getAmount()));
                // [FIX] Use theme-aware colors
                amountTextView.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.incomeColor));
                transactionTypeIndicator.setBackgroundColor(ThemeUtil.getThemeAttrColor(context, R.attr.incomeColor));
            } else {
                amountTextView.setText("- ₹" + String.format("%.2f", transaction.getAmount()));
                // [FIX] Use theme-aware colors
                amountTextView.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.expenseColor));
                transactionTypeIndicator.setBackgroundColor(ThemeUtil.getThemeAttrColor(context, R.attr.expenseColor));
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

            // Set click listener for the whole item
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(transaction);
                }
            });

            // [FIX] Set listeners for buttons
            if (editButton != null) {
                editButton.setOnClickListener(v -> {
                    if (listener != null) listener.onEditClick(transaction);
                });
            }
            if (copyButton != null) {
                copyButton.setOnClickListener(v -> {
                    if (listener != null) listener.onCopyClick(transaction);
                });
            }
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (listener != null) listener.onDeleteClick(transaction);
                });
            }
        }
    }

    // [FIX] Added DiffUtil Callback for performance
    private static class TransactionDiffCallback extends DiffUtil.Callback {
        private final List<TransactionModel> oldList;
        private final List<TransactionModel> newList;

        public TransactionDiffCallback(List<TransactionModel> oldList, List<TransactionModel> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getTransactionId().equals(newList.get(newItemPosition).getTransactionId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TransactionModel oldItem = oldList.get(oldItemPosition);
            TransactionModel newItem = newList.get(newItemPosition);
            return oldItem.getAmount() == newItem.getAmount() &&
                    oldItem.getTimestamp() == newItem.getTimestamp() &&
                    oldItem.getType().equals(newItem.getType()) &&
                    Objects.equals(oldItem.getRemark(), newItem.getRemark()) &&
                    Objects.equals(oldItem.getTransactionCategory(), newItem.getTransactionCategory()) &&
                    Objects.equals(oldItem.getPartyName(), newItem.getPartyName());
        }
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            if (context == null) return Color.BLACK; // Fallback
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}