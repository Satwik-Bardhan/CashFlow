package com.example.cashflow;

public class LegendData {
    private String categoryName;
    private String amount;
    private int percentage;
    private int color;

    public LegendData(String categoryName, String amount, int percentage, int color) {
        this.categoryName = categoryName;
        this.amount = amount;
        this.percentage = percentage;
        this.color = color;
    }

    // Getters
    public String getCategoryName() { return categoryName; }
    public String getAmount() { return amount; }
    public int getPercentage() { return percentage; }
    public int getColor() { return color; }
}
