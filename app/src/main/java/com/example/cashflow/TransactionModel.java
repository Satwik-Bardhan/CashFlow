package com.example.cashflow;

import java.io.Serializable;

public class TransactionModel implements Serializable {
    private String transactionId;
    private String transactionCategory;
    private String partyName;
    private double amount;
    private String date;
    private String type;
    private String paymentMode;
    private String remark;
    private long timestamp;

    public TransactionModel() {}

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

    public TransactionModel(String transactionCategory, String partyName, double amount, String date, String type, String paymentMode, String remark) {
        this(null, transactionCategory, partyName, amount, date, type, paymentMode, remark, System.currentTimeMillis());
    }


    public String getTransactionId() { return transactionId; }
    public String getTransactionCategory() { return transactionCategory; }
    public String getPartyName() { return partyName; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getType() { return type; }
    public String getPaymentMode() { return paymentMode; }
    public String getRemark() { return remark; }
    public long getTimestamp() { return timestamp; }

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