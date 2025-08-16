package com.example.cashflow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DownloadOptionsActivity extends AppCompatActivity {

    private TextView startDateTextView, endDateTextView;
    private RadioGroup filterTypeToggle, filterModeToggle;
    private Button downloadReportButtonFinal;

    private long startDate = 0;
    private long endDate = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_options);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        initializeUI();
        setupClickListeners();
    }

    private void initializeUI() {
        startDateTextView = findViewById(R.id.startDateTextView);
        endDateTextView = findViewById(R.id.endDateTextView);
        filterTypeToggle = findViewById(R.id.filterTypeToggle);
        filterModeToggle = findViewById(R.id.filterModeToggle);
        downloadReportButtonFinal = findViewById(R.id.downloadReportButtonFinal);
    }

    private void setupClickListeners() {
        startDateTextView.setOnClickListener(v -> showDateRangePicker());
        endDateTextView.setOnClickListener(v -> showDateRangePicker());
        downloadReportButtonFinal.setOnClickListener(v -> applyAndReturnFilters());
    }

    private void applyAndReturnFilters() {
        // Get selected entry type
        int selectedTypeId = filterTypeToggle.getCheckedRadioButtonId();
        String entryType = "All";
        if (selectedTypeId == R.id.filterInType) {
            entryType = "IN";
        } else if (selectedTypeId == R.id.filterOutType) {
            entryType = "OUT";
        }

        // Get selected payment mode
        int selectedModeId = filterModeToggle.getCheckedRadioButtonId();
        String paymentMode = "All";
        if (selectedModeId == R.id.filterCashMode) {
            paymentMode = "Cash";
        } else if (selectedModeId == R.id.filterOnlineMode) {
            paymentMode = "Online";
        }

        // Create an intent to pass the data back
        Intent resultIntent = new Intent();
        resultIntent.putExtra("startDate", startDate);
        resultIntent.putExtra("endDate", endDate);
        resultIntent.putExtra("entryType", entryType);
        resultIntent.putExtra("paymentMode", paymentMode);

        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> dateRangePicker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select Date Range")
                        .build();

        dateRangePicker.addOnPositiveButtonClickListener(selection -> {
            // The selection comes in UTC, adjust to start and end of day
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startCal.setTimeInMillis(selection.first);
            startDate = startCal.getTimeInMillis();

            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endCal.setTimeInMillis(selection.second);
            endDate = endCal.getTimeInMillis();

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            startDateTextView.setText(sdf.format(new Date(startDate)));
            endDateTextView.setText(sdf.format(new Date(endDate)));
        });

        dateRangePicker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
