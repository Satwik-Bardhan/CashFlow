package com.example.cashflow;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class AppSettingsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private SwitchMaterial appLockSwitch, passbookSwitch, groupNotificationsSwitch, calculatorSwitch, darkThemeSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        initializeUI();
        setupToolbar();
        setupClickListeners();
    }

    private void initializeUI() {
        toolbar = findViewById(R.id.toolbar);
        appLockSwitch = findViewById(R.id.appLockSwitch);
        calculatorSwitch = findViewById(R.id.amountFieldCalculatorSwitch);
        darkThemeSwitch = findViewById(R.id.darkThemeSwitch);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        // Example click listener for a switch
        appLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "App Lock Enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "App Lock Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Add listeners for other switches and buttons as needed
        findViewById(R.id.dataBackup).setOnClickListener(v -> {
            Toast.makeText(this, "Data Backup Clicked", Toast.LENGTH_SHORT).show();
        });
    }
}
