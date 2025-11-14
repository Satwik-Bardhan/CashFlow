package com.example.cashflow;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PieChartFragment extends Fragment {

    private static final String TAG = "PieChartFragment";
    private PieChart pieChart;
    private List<TransactionModel> transactions;

    public static PieChartFragment newInstance(ArrayList<TransactionModel> transactions) {
        PieChartFragment fragment = new PieChartFragment();
        Bundle args = new Bundle();
        args.putSerializable("transactions", transactions);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // [FIX] Inflate the correct layout file
        View view = inflater.inflate(R.layout.layout_pie_chart, container, false);

        pieChart = view.findViewById(R.id.pieChart);

        if (getArguments() != null) {
            // [FIX] Handle deserialization safely
            try {
                transactions = (List<TransactionModel>) getArguments().getSerializable("transactions");
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving transactions from arguments", e);
                transactions = new ArrayList<>();
            }
        } else {
            transactions = new ArrayList<>();
        }

        setupPieChart();
        loadPieChartData();

        return view;
    }

    private void setupPieChart() {
        if (getContext() == null) return;
        int textColor = ThemeUtil.getThemeAttrColor(getContext(), R.attr.textColorPrimary);

        pieChart.setDrawHoleEnabled(true);
        pieChart.setUsePercentValues(true);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.setEntryLabelColor(textColor); // [FIX] Theme-aware color
        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextSize(24f);
        pieChart.setCenterTextColor(textColor); // [FIX] Theme-aware color
        pieChart.getDescription().setEnabled(false);

        Legend legend = pieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.VERTICAL);
        legend.setDrawInside(false);
        legend.setEnabled(true);
        legend.setTextColor(textColor); // [FIX] Theme-aware color
    }

    private void loadPieChartData() {
        if (getContext() == null) return;
        int textColor = ThemeUtil.getThemeAttrColor(getContext(), R.attr.textColorPrimary);

        ArrayList<PieEntry> entries = new ArrayList<>();

        if (transactions != null && !transactions.isEmpty()) {
            // Group expenses by category
            java.util.Map<String, Double> categoryTotals = new java.util.HashMap<>();

            for (TransactionModel transaction : transactions) {
                if ("OUT".equalsIgnoreCase(transaction.getType())) {
                    String category = transaction.getTransactionCategory();
                    if (category == null || category.isEmpty()) category = "Other";
                    double amount = transaction.getAmount();
                    categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                }
            }

            // Convert to PieEntry list
            for (java.util.Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            }
        }

        PieDataSet dataSet;
        if (entries.isEmpty()) {
            entries.add(new PieEntry(100f, "No Data"));
            dataSet = new PieDataSet(entries, "");
            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(ThemeUtil.getThemeAttrColor(getContext(), R.attr.dividerHorizontal)); // Use divider color for no data
            dataSet.setColors(colors);
        } else {
            dataSet = new PieDataSet(entries, "Expense Categories");
            // Use predefined colors
            ArrayList<Integer> colors = new ArrayList<>();
            for (int color : ColorTemplate.MATERIAL_COLORS) {
                colors.add(color);
            }
            for (int color : ColorTemplate.VORDIPLOM_COLORS) {
                colors.add(color);
            }
            dataSet.setColors(colors);
        }

        PieData data = new PieData(dataSet);
        data.setDrawValues(true);
        data.setValueTextSize(12f);
        data.setValueTextColor(textColor); // [FIX] Theme-aware color

        pieChart.setData(data);
        pieChart.invalidate();
    }

    public void updateData(ArrayList<TransactionModel> newTransactions) {
        this.transactions = newTransactions;
        if (pieChart != null && getContext() != null) {
            loadPieChartData();
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