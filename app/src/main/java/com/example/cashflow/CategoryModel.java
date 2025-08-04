package com.example.cashflow;

import java.io.Serializable;

public class CategoryModel implements Serializable {
    private String name;
    private String colorHex;
    private boolean isCustom;

    public CategoryModel() {
        // Default constructor required for Firebase
    }

    public CategoryModel(String name, String colorHex, boolean isCustom) {
        this.name = name;
        this.colorHex = colorHex;
        this.isCustom = isCustom;
    }

    public String getName() {
        return name;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }
}