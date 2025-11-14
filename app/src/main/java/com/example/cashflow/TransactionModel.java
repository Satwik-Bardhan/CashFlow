package com.example.cashflow;

import java.io.Serializable;

// [FIX] Implement Serializable so this object can be passed in an Intent
public class TransactionModel implements Serializable {
    private String transactionId;
    private String transactionCategory;
    private String partyName;
    private double amount;
    private String type; // "IN" or "OUT"
    private String paymentMode; // e.g., "Cash", "Online"
    private String remark;
    private long timestamp;

    // A default constructor is required for Firebase to work correctly.
    public TransactionModel() {
    }

    // --- Getters and Setters for all fields ---

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionCategory() {
        return transactionCategory;
    }

    public void setTransactionCategory(String transactionCategory) {
        this.transactionCategory = transactionCategory;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // [FIX] This was in your original file but returned null.
    // It's likely an error from an older version.
    // It is safe to remove, but I will keep it commented out.
    // public String getDescription() {
    //     return null;
    // }
}