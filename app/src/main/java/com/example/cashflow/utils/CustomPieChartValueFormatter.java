package com.example.cashflow.utils;

import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DecimalFormat;

public class CustomPieChartValueFormatter extends ValueFormatter {

    private final DecimalFormat format;

    public CustomPieChartValueFormatter() {
        // Format to show a whole number for the percentage
        format = new DecimalFormat("###,###,##0");
    }

    @Override
    public String getPieLabel(float value, PieEntry pieEntry) {
        // This is where we create the multi-line label, e.g., "34%\nLeisure"
        // The 'value' is the percentage, and 'pieEntry.getLabel()' is the category name.
        return format.format(value) + "%\n" + pieEntry.getLabel();
    }
}

