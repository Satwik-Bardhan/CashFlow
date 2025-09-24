package com.example.cashflow.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cashflow.R;
import com.example.cashflow.models.Cashbook;

import java.util.List;

public class CashbookAdapter extends RecyclerView.Adapter<CashbookAdapter.CashbookViewHolder> {

    public interface OnCashbookClickListener {
        void onCashbookClick(Cashbook cashbook);
        void onMenuClick(Cashbook cashbook, View anchorView);
    }

    private List<Cashbook> cashbookList;
    private OnCashbookClickListener listener;

    public CashbookAdapter(List<Cashbook> cashbookList, OnCashbookClickListener listener) {
        this.cashbookList = cashbookList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CashbookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.cashbook_list_item, parent, false);
        return new CashbookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CashbookViewHolder holder, int position) {
        Cashbook cashbook = cashbookList.get(position);
        holder.bind(cashbook, listener);
    }

    @Override
    public int getItemCount() {
        return cashbookList != null ? cashbookList.size() : 0;
    }

    public void updateCashbooks(List<Cashbook> newCashbooks) {
        this.cashbookList = newCashbooks;
        notifyDataSetChanged();
    }

    class CashbookViewHolder extends RecyclerView.ViewHolder {
        TextView cashbookNameText, cashbookStatusText, cashbookBalanceText;
        View currentIndicator;
        ImageView menuButton;

        public CashbookViewHolder(@NonNull View itemView) {
            super(itemView);
            cashbookNameText = itemView.findViewById(R.id.cashbookNameText);
            cashbookStatusText = itemView.findViewById(R.id.cashbookStatusText);
            cashbookBalanceText = itemView.findViewById(R.id.cashbookBalanceText);
            currentIndicator = itemView.findViewById(R.id.currentIndicator);
            menuButton = itemView.findViewById(R.id.cashbookMenuButton);
        }

        public void bind(Cashbook cashbook, OnCashbookClickListener listener) {
            cashbookNameText.setText(cashbook.getName());

            // Show/hide current status
            if (cashbook.isCurrent()) {
                cashbookStatusText.setText("(Current)");
                cashbookStatusText.setVisibility(View.VISIBLE);
                if (currentIndicator != null) {
                    currentIndicator.setVisibility(View.VISIBLE);
                }
            } else {
                cashbookStatusText.setVisibility(View.GONE);
                if (currentIndicator != null) {
                    currentIndicator.setVisibility(View.GONE);
                }
            }

            // Show balance if available
            if (cashbookBalanceText != null) {
                String balanceText = String.format("Balance: ₹%.2f", cashbook.getTotalBalance());
                cashbookBalanceText.setText(balanceText);
            }

            // Click listener for the entire item
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCashbookClick(cashbook);
                }
            });

            // Click listener for the menu button
            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMenuClick(cashbook, v);
                    }
                });
            }
        }
    }
}
