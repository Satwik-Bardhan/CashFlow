package com.example.cashflow.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_THEME = "theme_mode";

    // Enum to represent the theme modes
    public enum ThemeMode {
        LIGHT, DARK
    }

    // Apply the saved theme on app startup
    public static void applyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default to DARK mode if no preference is saved
        String theme = prefs.getString(KEY_THEME, ThemeMode.DARK.name());

        if (ThemeMode.valueOf(theme) == ThemeMode.DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    // Toggle the theme and save the new preference
    public static void toggleTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentTheme = prefs.getString(KEY_THEME, ThemeMode.DARK.name());

        if (ThemeMode.valueOf(currentTheme) == ThemeMode.DARK) {
            // Switch to Light Mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            prefs.edit().putString(KEY_THEME, ThemeMode.LIGHT.name()).apply();
        } else {
            // Switch to Dark Mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            prefs.edit().putString(KEY_THEME, ThemeMode.DARK.name()).apply();
        }
    }

    // Get the current theme to set the switch state
    public static boolean isDarkTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, ThemeMode.DARK.name());
        return ThemeMode.valueOf(theme) == ThemeMode.DARK;
    }
}
