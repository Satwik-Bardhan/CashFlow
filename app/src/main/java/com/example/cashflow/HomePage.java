package com.example.cashflow;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class HomePage extends AppCompatActivity {

    // Balance Card
    private TextView uidText, balanceText, moneyIn, moneyOut, userNameBottom;
    // Bottom Navigation
    private LinearLayout btnHome, btnTransactions, btnSettings;
    private ImageView iconHome, iconTransactions, iconSettings;
    private TextView textHome, textTransactions, textSettings;

    // Top Info
    private TextView userNameTop;
    private LinearLayout cashInButton, cashOutButton, userBox, viewFullTransactionsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page); // Change to your layout file

        // ----- BALANCE CARD (from <include layout="@layout/balance_card" />) -----
        CardView balanceCardContainer = findViewById(R.id.balanceCardView);
        View balanceCard = findViewById(R.id.balanceCard);
        uidText = balanceCard.findViewById(R.id.uidText);
        balanceText = balanceCard.findViewById(R.id.balanceText);
        moneyIn = balanceCard.findViewById(R.id.moneyIn);
        moneyOut = balanceCard.findViewById(R.id.moneyOut);
        userNameBottom = balanceCard.findViewById(R.id.userNameBottom);

        // Set dummy data for illustration (replace with your back-end data calls)
        uidText.setText("UID: 1234ABCD");
        balanceText.setText("₹12,500.75");
        moneyIn.setText("₹21,000.00");
        moneyOut.setText("₹8,499.25");
        userNameBottom.setText("Jane Smith");

        // ----- BOTTOM NAVIGATION (from <include layout="@layout/layout_bottom_nav" />) -----
        btnHome = findViewById(R.id.btnHome);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnSettings = findViewById(R.id.btnSettings);

        iconHome = findViewById(R.id.iconHome);
        iconTransactions = findViewById(R.id.iconTransactions);
        iconSettings = findViewById(R.id.iconSettings);

        textHome = findViewById(R.id.textHome);
        textTransactions = findViewById(R.id.textTransactions);
        textSettings = findViewById(R.id.textSettings);

        // ----- TOP INFO and ACTIONS -----
        userNameTop = findViewById(R.id.userNameTop);
        cashInButton = findViewById(R.id.cashInButton);
        cashOutButton = findViewById(R.id.cashOutButton);
        userBox = findViewById(R.id.userBox);
        viewFullTransactionsButton = findViewById(R.id.viewFullTransactionsButton);

        userNameTop.setText("My Main Book");

        setupActionButtons();
        setupBottomNavigation();

        highlightBottomNav("home");
    }

    private void setupActionButtons() {
        cashInButton.setOnClickListener(v -> {
            Toast.makeText(this, "CASH IN", Toast.LENGTH_SHORT).show();
            // startActivity(new Intent(this, CashInOutActivity.class).putExtra("type", "IN"));
        });

        cashOutButton.setOnClickListener(v -> {
            Toast.makeText(this, "CASH OUT", Toast.LENGTH_SHORT).show();
            // startActivity(new Intent(this, CashInOutActivity.class).putExtra("type", "OUT"));
        });

        userBox.setOnClickListener(v -> {
            Toast.makeText(this, "User Book Menu", Toast.LENGTH_SHORT).show();
            // Show cashbook switcher or profile menu dialog here
        });

        viewFullTransactionsButton.setOnClickListener(v -> {
            Toast.makeText(this, "View All Transactions", Toast.LENGTH_SHORT).show();
            // startActivity(new Intent(this, TransactionListActivity.class));
        });
    }

    private void setupBottomNavigation() {
        btnHome.setOnClickListener(v -> {
            Toast.makeText(this, "Home (already selected)", Toast.LENGTH_SHORT).show();
            highlightBottomNav("home");
        });

        btnTransactions.setOnClickListener(v -> {
            Toast.makeText(this, "Transactions", Toast.LENGTH_SHORT).show();
            highlightBottomNav("transactions");
            // startActivity(new Intent(this, TransactionsActivity.class));
        });

        btnSettings.setOnClickListener(v -> {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
            highlightBottomNav("settings");
            // startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private void highlightBottomNav(String tab) {
        int activeColor = getResources().getColor(R.color.colorPrimary, getTheme());
        int inactiveColor = Color.WHITE;

        iconHome.setColorFilter(inactiveColor);
        textHome.setTextColor(inactiveColor);
        iconTransactions.setColorFilter(inactiveColor);
        textTransactions.setTextColor(inactiveColor);
        iconSettings.setColorFilter(inactiveColor);
        textSettings.setTextColor(inactiveColor);

        switch (tab) {
            case "home":
                iconHome.setColorFilter(activeColor);
                textHome.setTextColor(activeColor);
                break;
            case "transactions":
                iconTransactions.setColorFilter(activeColor);
                textTransactions.setTextColor(activeColor);
                break;
            case "settings":
                iconSettings.setColorFilter(activeColor);
                textSettings.setTextColor(activeColor);
                break;
        }
    }
}
