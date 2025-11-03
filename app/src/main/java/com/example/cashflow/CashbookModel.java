package com.example.cashflow;

public class CashbookModel {
    private String cashbookId;
    private String name;
    private String description;
    private double balance;
    private int transactionCount;
    private long createdDate;
    private long lastModified;
    private boolean isActive;
    private boolean isFavorite;
    private String userId;

    public CashbookModel() {
        // Required empty constructor for Firebase
    }

    public CashbookModel(String cashbookId, String name, String description,
                         double balance, int transactionCount, long createdDate,
                         long lastModified, boolean isActive, boolean isFavorite,
                         String userId) {
        this.cashbookId = cashbookId;
        this.name = name;
        this.description = description;
        this.balance = balance;
        this.transactionCount = transactionCount;
        this.createdDate = createdDate;
        this.lastModified = lastModified;
        this.isActive = isActive;
        this.isFavorite = isFavorite;
        this.userId = userId;
    }

    // Getters and Setters
    public String getCashbookId() { return cashbookId; }
    public void setCashbookId(String cashbookId) { this.cashbookId = cashbookId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
