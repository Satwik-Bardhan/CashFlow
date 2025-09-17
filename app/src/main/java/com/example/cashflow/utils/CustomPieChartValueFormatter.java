package com.example.cashflow.utils;

import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import java.text.DecimalFormat;

public class CustomPieChartValueFormatter extends ValueFormatter {
    private final DecimalFormat format;

    public CustomPieChartValueFormatter() {
        format = new DecimalFormat("##0");
    }

    @Override
    public String getPieLabel(float value, PieEntry pieEntry) {
        // Format: "49%\nUtilities" - percentage on top, label below
        return format.format(value) + "%\t" + pieEntry.getLabel();
    }
}
