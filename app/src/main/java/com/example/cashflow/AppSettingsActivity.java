package com.example.cashflow;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.cashflow.utils.ThemeManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class AppSettingsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private SwitchMaterial darkThemeSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        initializeUI();
        setupToolbar();

        // Set the switch to the correct state based on the saved theme
        darkThemeSwitch.setChecked(ThemeManager.isDarkTheme(this));

        // Set the listener to toggle the theme when clicked
        darkThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ThemeManager.toggleTheme(this);
        });
    }

    private void initializeUI() {
        toolbar = findViewById(R.id.toolbar);
        darkThemeSwitch = findViewById(R.id.darkThemeSwitch);
        // ... initialize other views from your layout
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
