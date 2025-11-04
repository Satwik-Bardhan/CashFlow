package com.example.cashflow;

public class CashbookModel {
    private String cashbookId;
    private String name;
    private String description;
    private double totalBalance;
    private int transactionCount;
    private long createdDate;
    private long lastModified;
    private boolean isActive;
    private Boolean isCurrent;
    private boolean isFavorite;
    private String userId;
    private String currency;

    // Empty constructor for Firebase
    public CashbookModel() {
    }

    // Full constructor
    public CashbookModel(String cashbookId, String name, String description,
                         double totalBalance, int transactionCount, long createdDate,
                         long lastModified, boolean isActive, boolean isFavorite, String userId) {
        this.cashbookId = cashbookId;
        this.name = name;
        this.description = description;
        this.totalBalance = totalBalance;
        this.transactionCount = transactionCount;
        this.createdDate = createdDate;
        this.lastModified = lastModified;
        this.isActive = isActive;
        this.isFavorite = isFavorite;
        this.userId = userId;
        this.currency = "INR";
    }

    // NEW: Convenience constructor for simple creation
    public CashbookModel(String cashbookId, String name) {
        this.cashbookId = cashbookId;
        this.name = name;
        this.description = "";
        this.totalBalance = 0.0;
        this.transactionCount = 0;
        this.createdDate = System.currentTimeMillis();
        this.lastModified = System.currentTimeMillis();
        this.isActive = true;
        this.isCurrent = false;
        this.isFavorite = false;
        this.userId = "";
        this.currency = "INR";
    }

    // Getters and Setters
    public String getCashbookId() {
        return cashbookId;
    }

    public void setCashbookId(String cashbookId) {
        this.cashbookId = cashbookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getBalance() {
        return totalBalance;
    }

    public double getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(double totalBalance) {
        this.totalBalance = totalBalance;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(Boolean current) {
        isCurrent = current;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashbookModel cashbook = (CashbookModel) o;
        return cashbookId != null && cashbookId.equals(cashbook.cashbookId);
    }

    @Override
    public int hashCode() {
        return cashbookId != null ? cashbookId.hashCode() : 0;
    }

    public void setBalance(double v) {
    }
}
