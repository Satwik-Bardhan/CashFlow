package com.example.cashflow.models;

public class Cashbook {
    private String cashbookId;
    private String name;
    private String description;
    private double totalBalance;
    private int transactionCount;
    private long createdDate;
    private long lastModified;
    private boolean isActive;
    private boolean isCurrent;  // NEW: Indicates if this is the currently selected cashbook
    private boolean isFavorite;
    private String userId;
    private String currency;

    // Required empty constructor for Firebase
    public Cashbook() {
    }

    // Full constructor
    public Cashbook(String cashbookId, String name, String description,
                    double totalBalance, int transactionCount, long createdDate,
                    long lastModified, boolean isActive, boolean isCurrent,
                    boolean isFavorite, String userId, String currency) {
        this.cashbookId = cashbookId;
        this.name = name;
        this.description = description;
        this.totalBalance = totalBalance;
        this.transactionCount = transactionCount;
        this.createdDate = createdDate;
        this.lastModified = lastModified;
        this.isActive = isActive;
        this.isCurrent = isCurrent;
        this.isFavorite = isFavorite;
        this.userId = userId;
        this.currency = currency != null ? currency : "INR";
    }

    // Getters and Setters
    public String getCashbookId() { return cashbookId; }
    public void setCashbookId(String cashbookId) { this.cashbookId = cashbookId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getTotalBalance() { return totalBalance; }
    public void setTotalBalance(double totalBalance) { this.totalBalance = totalBalance; }

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

    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(boolean current) { isCurrent = current; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cashbook cashbook = (Cashbook) o;
        return cashbookId != null && cashbookId.equals(cashbook.cashbookId);
    }

    @Override
    public int hashCode() {
        return cashbookId != null ? cashbookId.hashCode() : 0;
    }
}
