package com.example.cashflow.utils;

import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DecimalFormat;

public class CustomPieChartValueFormatter extends ValueFormatter {

    private final DecimalFormat format;

    public CustomPieChartValueFormatter() {
        // Format to show a whole number for the percentage
        format = new DecimalFormat("###,###,##0'%'"); // [FIX] Added % symbol
    }

    @Override
    public String getPieLabel(float value, PieEntry pieEntry) {
        // [FIX] This formatter is for the value *outside* the slice.
        // The label is separate.
        return format.format(value);
    }

    @Override
    public String getFormattedValue(float value) {
        // This is used for the value inside the slice
        return format.format(value);
    }
}