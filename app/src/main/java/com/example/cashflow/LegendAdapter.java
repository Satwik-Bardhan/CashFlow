package com.example.cashflow; // Your actual package name

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {

    private List<LegendData> legendList;

    public LegendAdapter(List<LegendData> legendList) {
        this.legendList = legendList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.legend_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        LegendData data = legendList.get(position);

        // Set category name
        holder.categoryName.setText(data.getCategoryName());

        // Set amount
        holder.categoryAmount.setText("₹" + data.getAmount());

        // Set percentage
        holder.categoryPercentage.setText(data.getPercentage() + "%");

        // Set color indicator
        holder.colorIndicator.setBackgroundColor(data.getColor());
    }

    @Override
    public int getItemCount() {
        return legendList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        View colorIndicator;
        TextView categoryName, categoryAmount, categoryPercentage;

        public ViewHolder(View view) {
            super(view);
            colorIndicator = view.findViewById(R.id.colorIndicator);
            categoryName = view.findViewById(R.id.categoryName);
            categoryAmount = view.findViewById(R.id.categoryAmount);
            categoryPercentage = view.findViewById(R.id.categoryPercentage);
        }
    }
}
