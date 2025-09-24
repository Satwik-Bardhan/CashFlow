package com.example.cashflow.models;

import java.util.Date;

public class Cashbook {
    private String id;
    private String name;
    private boolean isCurrent;
    private Date createdDate;
    private String userId;
    private double totalBalance;

    // Constructors
    public Cashbook() {
        // Default constructor required for Firebase
    }

    public Cashbook(String name) {
        this.name = name;
        this.isCurrent = false;
        this.createdDate = new Date();
        this.totalBalance = 0.0;
    }

    // Copy constructor for duplication
    public Cashbook(Cashbook original) {
        this.name = original.name;
        this.isCurrent = false;
        this.createdDate = new Date();
        this.userId = original.userId;
        this.totalBalance = original.totalBalance;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(double totalBalance) {
        this.totalBalance = totalBalance;
    }

    @Override
    public String toString() {
        return name;
    }
}
