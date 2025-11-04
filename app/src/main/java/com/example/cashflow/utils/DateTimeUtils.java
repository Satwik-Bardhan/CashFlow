package com.example.cashflow.utils;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * DateTimeUtils - Utility class for date formatting and relative time display
 *
 * Provides methods to format dates and show relative time spans
 * (e.g., "3 hours ago", "2 days ago")
 */
public class DateTimeUtils {

    /**
     * Get relative time span from a timestamp
     * @param context Application context
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted relative time string (e.g., "3 hours ago")
     */
    public static String getRelativeTimeSpan(Context context, long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        // Less than a minute
        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return "Just now";
        }

        // Less than an hour
        if (diff < TimeUnit.HOURS.toMillis(1)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }

        // Less than a day
        if (diff < TimeUnit.DAYS.toMillis(1)) {
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }

        // Less than a week
        if (diff < TimeUnit.DAYS.toMillis(7)) {
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            return days + (days == 1 ? " day ago" : " days ago");
        }

        // Less than a month
        if (diff < TimeUnit.DAYS.toMillis(30)) {
            long weeks = TimeUnit.MILLISECONDS.toDays(diff) / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }

        // Less than a year
        if (diff < TimeUnit.DAYS.toMillis(365)) {
            long months = TimeUnit.MILLISECONDS.toDays(diff) / 30;
            return months + (months == 1 ? " month ago" : " months ago");
        }

        // Years
        long years = TimeUnit.MILLISECONDS.toDays(diff) / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }

    /**
     * Format timestamp to readable date string
     * @param timestamp Unix timestamp in milliseconds
     * @param pattern Date pattern (e.g., "MMM dd, yyyy")
     * @return Formatted date string
     */
    public static String formatDate(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Format timestamp to default date format
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date string (MMM dd, yyyy)
     */
    public static String formatDate(long timestamp) {
        return formatDate(timestamp, "MMM dd, yyyy");
    }

    /**
     * Format timestamp to date and time
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date and time string
     */
    public static String formatDateTime(long timestamp) {
        return formatDate(timestamp, "MMM dd, yyyy hh:mm a");
    }

    /**
     * Get month and year from timestamp
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted month-year string (e.g., "January 2025")
     */
    public static String formatMonthYear(long timestamp) {
        return formatDate(timestamp, "MMMM yyyy");
    }

    /**
     * Get short date format
     * @param timestamp Unix timestamp in milliseconds
     * @return Short date format (e.g., "01/15/2025")
     */
    public static String formatShortDate(long timestamp) {
        return formatDate(timestamp, "MM/dd/yyyy");
    }

    /**
     * Check if timestamp is today
     * @param timestamp Unix timestamp in milliseconds
     * @return true if timestamp is today, false otherwise
     */
    public static boolean isToday(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String timestampDate = sdf.format(new Date(timestamp));
        String todayDate = sdf.format(new Date());
        return timestampDate.equals(todayDate);
    }

    /**
     * Check if timestamp is yesterday
     * @param timestamp Unix timestamp in milliseconds
     * @return true if timestamp is yesterday, false otherwise
     */
    public static boolean isYesterday(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String timestampDate = sdf.format(new Date(timestamp));
        long yesterdayMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        String yesterdayDate = sdf.format(new Date(yesterdayMillis));
        return timestampDate.equals(yesterdayDate);
    }

    /**
     * Get display date with "Today", "Yesterday" for recent dates
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date string with context
     */
    public static String getDisplayDate(long timestamp) {
        if (isToday(timestamp)) {
            return "Today";
        } else if (isYesterday(timestamp)) {
            return "Yesterday";
        } else {
            return formatDate(timestamp);
        }
    }
}
