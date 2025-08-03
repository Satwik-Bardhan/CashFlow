package com.example.cashflow;

import java.io.Serializable;

public class TransactionModel implements Serializable {
    private String transactionId;
    private String transactionCategory; // Renamed from 'category'
    private String partyName;           // New: Field for Party/Customer/Supplier
    private double amount;
    private String date; // E.g., "MMM dd, yyyy"
    private String type; // "IN" or "OUT"
    private String paymentMode; // "Cash" or "Online"
    private String remark;
    private long timestamp; // New: Timestamp for sorting

    public TransactionModel() {
        // Default constructor required by Firebase
    }

    // Full constructor including all new fields
    public TransactionModel(String transactionId, String transactionCategory, String partyName, double amount, String date, String type, String paymentMode, String remark, long timestamp) {
        this.transactionId = transactionId;
        this.transactionCategory = transactionCategory;
        this.partyName = partyName;
        this.amount = amount;
        this.date = date;
        this.type = type;
        this.paymentMode = paymentMode;
        this.remark = remark;
        this.timestamp = timestamp;
    }

    // Overload constructor for simpler saving (new transaction)
    public TransactionModel(String transactionCategory, String partyName, double amount, String date, String type, String paymentMode, String remark) {
        this(null, transactionCategory, partyName, amount, date, type, paymentMode, remark, System.currentTimeMillis());
    }


    // Getters
    public String getTransactionId() { return transactionId; }
    public String getTransactionCategory() { return transactionCategory; }
    public String getPartyName() { return partyName; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getType() { return type; }
    public String getPaymentMode() { return paymentMode; }
    public String getRemark() { return remark; }
    public long getTimestamp() { return timestamp; }

    // Setters (Firebase needs these)
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setTransactionCategory(String transactionCategory) { this.transactionCategory = transactionCategory; }
    public void setPartyName(String partyName) { this.partyName = partyName; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setDate(String date) { this.date = date; }
    public void setType(String type) { this.type = type; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}