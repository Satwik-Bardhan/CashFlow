package com.example.cashflow.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cashflow.R;
import com.example.cashflow.models.Cashbook;
import com.example.cashflow.utils.DateUtils;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CashbookAdapter extends RecyclerView.Adapter<CashbookAdapter.CashbookViewHolder> {

    public interface OnCashbookClickListener {
        void onCashbookClick(Cashbook cashbook);
        void onFavoriteClick(Cashbook cashbook);
        void onMenuClick(Cashbook cashbook, View anchorView);
    }

    private Context context;
    private List<Cashbook> cashbookList;
    private OnCashbookClickListener listener;
    private NumberFormat currencyFormat;
    private SimpleDateFormat dateFormat;

    public CashbookAdapter(Context context, List<Cashbook> cashbookList,
                           OnCashbookClickListener listener) {
        this.context = context;
        this.cashbookList = new ArrayList<>(cashbookList);
        this.listener = listener;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        this.dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public CashbookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                R.layout.item_single_cashbook, parent, false);
        return new CashbookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CashbookViewHolder holder, int position) {
        Cashbook cashbook = cashbookList.get(position);
        holder.bind(cashbook);
    }

    @Override
    public int getItemCount() {
        return cashbookList != null ? cashbookList.size() : 0;
    }

    /**
     * IMPROVED: Use DiffUtil instead of notifyDataSetChanged for better performance
     * This calculates only the changes and updates affected items
     */
    public void updateCashbooks(List<Cashbook> newCashbooks) {
        CashbookDiffCallback diffCallback = new CashbookDiffCallback(
                this.cashbookList, newCashbooks);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.cashbookList.clear();
        this.cashbookList.addAll(newCashbooks);
        diffResult.dispatchUpdatesTo(this);
    }

    public class CashbookViewHolder extends RecyclerView.ViewHolder {
        private CardView cashbookItemCard;
        private CardView iconCard;
        private ImageView bookIcon;
        private TextView cashbookNameText;
        private TextView statusBadge;
        private ImageView favoriteButton;
        private ImageView menuButton;
        private TextView lastModifiedText;
        private TextView balanceText;
        private TextView transactionCountText;
        private TextView createdDateText;

        public CashbookViewHolder(@NonNull View itemView) {
            super(itemView);

            cashbookItemCard = itemView.findViewById(R.id.cashbookItemCard);
            iconCard = itemView.findViewById(R.id.iconCard);
            bookIcon = itemView.findViewById(R.id.bookIcon);
            cashbookNameText = itemView.findViewById(R.id.cashbookNameText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            favoriteButton = itemView.findViewById(R.id.favoriteButton);
            menuButton = itemView.findViewById(R.id.menuButton);
            lastModifiedText = itemView.findViewById(R.id.lastModifiedText);
            balanceText = itemView.findViewById(R.id.balanceText);
            transactionCountText = itemView.findViewById(R.id.transactionCountText);
            createdDateText = itemView.findViewById(R.id.createdDateText);
        }

        public void bind(Cashbook cashbook) {
            // Set cashbook name
            cashbookNameText.setText(cashbook.getName());

            // Set status badge - Show CURRENT instead of just ACTIVE
            if (cashbook.isCurrent()) {
                statusBadge.setText(context.getString(R.string.status_current));
                statusBadge.setTextColor(context.getColor(R.color.primary_blue));
                statusBadge.setVisibility(View.VISIBLE);
            } else if (cashbook.isActive()) {
                statusBadge.setText(context.getString(R.string.status_active));
                statusBadge.setTextColor(context.getColor(R.color.success_color));
                statusBadge.setVisibility(View.VISIBLE);
            } else {
                statusBadge.setText(context.getString(R.string.status_inactive));
                statusBadge.setTextColor(context.getColor(R.color.text_secondary));
                statusBadge.setVisibility(View.VISIBLE);
            }

            // Set favorite icon
            if (cashbook.isFavorite()) {
                favoriteButton.setImageResource(R.drawable.ic_star);
                favoriteButton.setColorFilter(context.getColor(R.color.favorite_color));
            } else {
                favoriteButton.setImageResource(R.drawable.ic_star_outline);
                favoriteButton.setColorFilter(context.getColor(R.color.text_secondary));
            }

            // Set last modified text using DateUtils
            if (cashbook.getLastModified() > 0) {
                String lastModified = DateUtils.getRelativeTimeSpan(
                        context, cashbook.getLastModified());
                lastModifiedText.setText(context.getString(
                        R.string.last_modified_format, lastModified));
                lastModifiedText.setVisibility(View.VISIBLE);
            } else {
                lastModifiedText.setVisibility(View.GONE);
            }

            // Set balance with color coding
            double balance = cashbook.getTotalBalance();
            balanceText.setText(currencyFormat.format(balance));
            if (balance >= 0) {
                balanceText.setTextColor(context.getColor(R.color.success_color));
            } else {
                balanceText.setTextColor(context.getColor(R.color.error_red));
            }

            // Set transaction count
            int transactionCount = cashbook.getTransactionCount();
            transactionCountText.setText(String.valueOf(transactionCount));

            // Set created date
            if (cashbook.getCreatedDate() > 0) {
                createdDateText.setText(dateFormat.format(
                        new Date(cashbook.getCreatedDate())));
            } else {
                createdDateText.setText("-");
            }

            // Set icon color dynamically
            int iconColor = getIconColorForCashbook(cashbook);
            iconCard.setCardBackgroundColor(iconColor);

            // Item click listener
            cashbookItemCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCashbookClick(cashbook);
                }
            });

            // Favorite button click listener
            favoriteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoriteClick(cashbook);
                }
            });

            // Menu button click listener
            menuButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMenuClick(cashbook, v);
                }
            });
        }

        private int getIconColorForCashbook(Cashbook cashbook) {
            // Use your existing CategoryColorUtil or implement color logic
            if (cashbook.isCurrent()) {
                return context.getColor(R.color.primary_blue);
            } else if (cashbook.isFavorite()) {
                return context.getColor(R.color.favorite_color);
            } else {
                return context.getColor(R.color.success_color);
            }
        }
    }

    /**
     * DiffUtil Callback for efficient list updates
     * This compares old and new lists to determine what changed
     */
    private static class CashbookDiffCallback extends DiffUtil.Callback {
        private List<Cashbook> oldList;
        private List<Cashbook> newList;

        public CashbookDiffCallback(List<Cashbook> oldList, List<Cashbook> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList != null ? oldList.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return newList != null ? newList.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Compare by unique ID
            return oldList.get(oldItemPosition).getCashbookId()
                    .equals(newList.get(newItemPosition).getCashbookId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Cashbook oldCashbook = oldList.get(oldItemPosition);
            Cashbook newCashbook = newList.get(newItemPosition);

            // Compare all displayed fields
            return oldCashbook.getName().equals(newCashbook.getName()) &&
                    oldCashbook.getTotalBalance() == newCashbook.getTotalBalance() &&
                    oldCashbook.getTransactionCount() == newCashbook.getTransactionCount() &&
                    oldCashbook.isActive() == newCashbook.isActive() &&
                    oldCashbook.isCurrent() == newCashbook.isCurrent() &&
                    oldCashbook.isFavorite() == newCashbook.isFavorite() &&
                    oldCashbook.getLastModified() == newCashbook.getLastModified();
        }
    }
}
