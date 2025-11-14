package com.example.cashflow;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale; // [FIX] Added for formatting

public class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {

    private List<LegendData> legendList;

    public LegendAdapter(List<LegendData> legendList) {
        this.legendList = legendList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // [FIX] Using item_legend.xml as you specified
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_legend_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LegendData data = legendList.get(position);
        holder.bind(data); // [FIX] Call bind method
    }

    @Override
    public int getItemCount() {
        return legendList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        View colorSwatch;
        TextView categoryName, categoryAmount, categoryPercentage;

        public ViewHolder(View view) {
            super(view);
            // [FIX] Corrected IDs from item_legend.xml
            colorSwatch = view.findViewById(R.id.colorSwatch);
            categoryName = view.findViewById(R.id.categoryNameTextView);
            categoryAmount = view.findViewById(R.id.amountTextView);
            categoryPercentage = view.findViewById(R.id.percentageTextView);
        }

        // [FIX] Added bind method to set data and theme-aware colors
        public void bind(LegendData data) {
            Context context = itemView.getContext();

            categoryName.setText(data.getCategoryName());

            // [FIX] Format amount and percentage from LegendData
            try {
                double amountValue = Double.parseDouble(data.getAmount());
                categoryAmount.setText(String.format(Locale.US, "₹%.2f", amountValue));
            } catch (NumberFormatException e) {
                categoryAmount.setText("₹--.--");
            }

            categoryPercentage.setText(String.format(Locale.US, "(%.1f%%)", data.getPercentage()));
            colorSwatch.setBackgroundColor(data.getColor());

            // [FIX] Apply theme colors
            categoryName.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.textColorPrimary));
            categoryAmount.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.textColorPrimary));
            categoryPercentage.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.textColorSecondary));
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