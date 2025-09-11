package com.example.cashflow;

public class Users {
    private String profile;
    private String mail;
    private String userName;
    private String userId;
    // --- [ADDED] New fields to store additional profile information ---
    private String phoneNumber;
    private long dateOfBirthTimestamp;

    public Users() {
        // Default constructor required for Firebase
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // --- [ADDED] Getters and setters for the new fields ---
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public long getDateOfBirthTimestamp() {
        return dateOfBirthTimestamp;
    }

    public void setDateOfBirthTimestamp(long dateOfBirthTimestamp) {
        this.dateOfBirthTimestamp = dateOfBirthTimestamp;
    }
}
