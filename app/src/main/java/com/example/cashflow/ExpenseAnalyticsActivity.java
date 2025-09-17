package com.example.cashflow;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ExpenseAnalyticsActivity extends AppCompatActivity implements OnChartValueSelectedListener {

    private PieChart fullScreenPieChart;
    private RecyclerView monthlyCardsRecyclerView, detailedLegendRecyclerView;
    private ImageButton closeButton;
    private TextView selectedCategoryText;

    private ArrayList<TransactionModel> allTransactions;
    private List<MonthlyExpense> monthlyExpenses;
    private MonthlyExpense currentSelectedMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_analytics);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Retrieve the transaction data passed from the previous activity
        allTransactions = (ArrayList<TransactionModel>) getIntent().getSerializableExtra("all_transactions");

        if (allTransactions == null || allTransactions.isEmpty()) {
            Toast.makeText(this, "No transaction data available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeUI();
        processTransactionData();
        setupRecyclerViews();
        setupClickListeners();
        setupPieChart();

        // Initially display data for the most recent month
        if (!monthlyExpenses.isEmpty()) {
            updatePieChartForMonth(monthlyExpenses.get(0));
        }
    }

    private void initializeUI() {
        fullScreenPieChart = findViewById(R.id.fullScreenPieChart);
        monthlyCardsRecyclerView = findViewById(R.id.monthlyCardsRecyclerView);
        detailedLegendRecyclerView = findViewById(R.id.detailedLegendRecyclerView);
        closeButton = findViewById(R.id.closeButton);
    }

    private void setupPieChart() {
        fullScreenPieChart.setOnChartValueSelectedListener(this);
        fullScreenPieChart.setRotationEnabled(true);
        fullScreenPieChart.setHighlightPerTapEnabled(true);
        fullScreenPieChart.setEntryLabelTextSize(12f);
        fullScreenPieChart.setEntryLabelColor(Color.WHITE);
        fullScreenPieChart.setHoleRadius(40f);
        fullScreenPieChart.setTransparentCircleRadius(45f);
        fullScreenPieChart.setDrawCenterText(true);
        fullScreenPieChart.setRotationAngle(0);
        fullScreenPieChart.setNoDataText("No expense data available for this month");
        fullScreenPieChart.setNoDataTextColor(Color.WHITE);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e instanceof PieEntry) {
            PieEntry pieEntry = (PieEntry) e;
            String category = (String) pieEntry.getData();
            if (category == null) category = pieEntry.getLabel();
            Toast.makeText(this, category + ": ₹" + String.format(Locale.US, "%.2f", pieEntry.getValue()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNothingSelected() {
        // Handle case when nothing is selected
    }

    private void processTransactionData() {
        // Group transactions by month
        Map<String, List<TransactionModel>> transactionsByMonth = allTransactions.stream()
                .filter(t -> "OUT".equalsIgnoreCase(t.getType()))
                .collect(Collectors.groupingBy(t -> {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(t.getTimestamp());
                    return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
                }));

        monthlyExpenses = new ArrayList<>();
        for (Map.Entry<String, List<TransactionModel>> entry : transactionsByMonth.entrySet()) {
            double total = entry.getValue().stream().mapToDouble(TransactionModel::getAmount).sum();
            monthlyExpenses.add(new MonthlyExpense(entry.getKey(), total, entry.getValue()));
        }

        // Sort months from most recent to oldest
        Collections.sort(monthlyExpenses, Comparator.comparing(MonthlyExpense::getMonth).reversed());
    }

    private void setupRecyclerViews() {
        // Monthly Cards RecyclerView
        monthlyCardsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        MonthlyCardAdapter monthlyAdapter = new MonthlyCardAdapter(monthlyExpenses, this::updatePieChartForMonth);
        monthlyCardsRecyclerView.setAdapter(monthlyAdapter);

        // Detailed Legend RecyclerView
        detailedLegendRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> finish());
    }

    private void updatePieChartForMonth(MonthlyExpense monthlyExpense) {
        currentSelectedMonth = monthlyExpense;

        // Group expenses by category for the selected month
        Map<String, Double> expenseByCategory = monthlyExpense.getTransactions().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionCategory() != null ? t.getTransactionCategory() : "Others",
                        Collectors.summingDouble(TransactionModel::getAmount)
                ));

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<LegendItem> legendItems = new ArrayList<>();
        int[] colors = ColorTemplate.MATERIAL_COLORS;

        int colorIndex = 0;
        for (Map.Entry<String, Double> entry : expenseByCategory.entrySet()) {
            float amount = entry.getValue().floatValue();
            entries.add(new PieEntry(amount, entry.getKey()));
            legendItems.add(new LegendItem(
                    entry.getKey(),
                    amount,
                    (float) (amount / monthlyExpense.getTotalExpense() * 100),
                    colors[colorIndex % colors.length]
            ));
            colorIndex++;
        }

        // Sort legend items by amount (descending)
        Collections.sort(legendItems, (a, b) -> Float.compare(b.amount, a.amount));

        // Update Pie Chart
        PieDataSet dataSet = new PieDataSet(entries, "Monthly Expenses");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(new PercentFormatter(fullScreenPieChart));
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);

        PieData pieData = new PieData(dataSet);
        fullScreenPieChart.setData(pieData);
        fullScreenPieChart.setUsePercentValues(true);
        fullScreenPieChart.getDescription().setEnabled(false);
        fullScreenPieChart.getLegend().setEnabled(false);
        fullScreenPieChart.setDrawEntryLabels(false);

        // Format center text
        String centerText = String.format(Locale.US, "Total Expenses\n₹%.2f", monthlyExpense.getTotalExpense());
        fullScreenPieChart.setCenterText(centerText);
        fullScreenPieChart.setCenterTextColor(Color.WHITE);
        fullScreenPieChart.setCenterTextSize(16f);
        fullScreenPieChart.animateXY(1000, 1000);
        fullScreenPieChart.invalidate();

        // Update Legend RecyclerView
        LegendAdapter legendAdapter = new LegendAdapter(legendItems);
        detailedLegendRecyclerView.setAdapter(legendAdapter);
    }

    // --- Inner classes remain the same but with enhanced ViewHolder binding ---

    static class MonthlyExpense {
        private String month; // Format: "yyyy-MM"
        private double totalExpense;
        private List<TransactionModel> transactions;

        public MonthlyExpense(String month, double totalExpense, List<TransactionModel> transactions) {
            this.month = month;
            this.totalExpense = totalExpense;
            this.transactions = transactions;
        }

        public String getMonth() { return month; }
        public double getTotalExpense() { return totalExpense; }
        public List<TransactionModel> getTransactions() { return transactions; }
    }

    static class LegendItem {
        String category;
        float amount;
        float percentage;
        int color;

        public LegendItem(String category, float amount, float percentage, int color) {
            this.category = category;
            this.amount = amount;
            this.percentage = percentage;
            this.color = color;
        }
    }

    // Interface for click listener
    interface OnMonthClickListener {
        void onMonthClick(MonthlyExpense monthlyExpense);
    }

    static class MonthlyCardAdapter extends RecyclerView.Adapter<MonthlyCardAdapter.ViewHolder> {
        private List<MonthlyExpense> monthlyExpenses;
        private OnMonthClickListener clickListener;
        private int selectedPosition = 0;

        MonthlyCardAdapter(List<MonthlyExpense> monthlyExpenses, OnMonthClickListener listener) {
            this.monthlyExpenses = monthlyExpenses;
            this.clickListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MonthlyExpense monthlyExpense = monthlyExpenses.get(position);
            holder.bind(monthlyExpense, position == selectedPosition);
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onMonthClick(monthlyExpense);
                }
                notifyItemChanged(selectedPosition);
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(selectedPosition);
            });
        }

        @Override
        public int getItemCount() {
            return monthlyExpenses.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView monthName, totalExpense;
            LinearLayout cardContainer;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                monthName = itemView.findViewById(R.id.monthNameTextView);
                totalExpense = itemView.findViewById(R.id.totalExpenseTextView);
                cardContainer = itemView.findViewById(R.id.cardContainer);
            }

            void bind(MonthlyExpense data, boolean isSelected) {
                // Format "yyyy-MM" to "Month Year"
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
                    SimpleDateFormat formatter = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
                    monthName.setText(formatter.format(parser.parse(data.getMonth())));
                } catch (Exception e) {
                    monthName.setText(data.getMonth());
                }

                totalExpense.setText(String.format(Locale.US, "₹%.0f", data.getTotalExpense()));

                if (isSelected) {
                    cardContainer.setBackgroundColor(Color.parseColor("#2196F3")); // Active color
                    monthName.setTextColor(Color.WHITE);
                    totalExpense.setTextColor(Color.WHITE);
                } else {
                    cardContainer.setBackgroundColor(Color.parseColor("#3A3A3A")); // Default dark color
                    monthName.setTextColor(Color.WHITE);
                    totalExpense.setTextColor(Color.parseColor("#FFFFFF"));
                }
            }
        }
    }

    static class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private List<LegendItem> legendItems;

        LegendAdapter(List<LegendItem> items) {
            this.legendItems = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_legend_detail, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(legendItems.get(position));
        }

        @Override
        public int getItemCount() {
            return legendItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View colorSwatch;
            TextView categoryName, amount, percentage;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                colorSwatch = itemView.findViewById(R.id.colorSwatch);
                categoryName = itemView.findViewById(R.id.categoryNameTextView);
                amount = itemView.findViewById(R.id.amountTextView);
                percentage = itemView.findViewById(R.id.percentageTextView);
            }

            void bind(LegendItem item) {
                colorSwatch.setBackgroundColor(item.color);
                categoryName.setText(item.category);
                amount.setText(String.format(Locale.US, "₹%.2f", item.amount));
                percentage.setText(String.format(Locale.US, "(%.1f%%)", item.percentage));
            }
        }
    }
}
