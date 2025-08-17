package com.example.cashflow;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DownloadOptionsActivity extends AppCompatActivity {

    private TextView startDateTextView, endDateTextView;
    private RadioGroup filterTypeToggle, filterModeToggle;
    private MaterialButton downloadReportButtonFinal;
    private ImageView backButton; // Added back button

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_options);

        // Hide the default action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUI();
        setupInitialDates();
        setupClickListeners();
    }

    private void initializeUI() {
        startDateTextView = findViewById(R.id.startDateTextView);
        endDateTextView = findViewById(R.id.endDateTextView);
        filterTypeToggle = findViewById(R.id.filterTypeToggle);
        filterModeToggle = findViewById(R.id.filterModeToggle);
        downloadReportButtonFinal = findViewById(R.id.downloadReportButtonFinal);
        backButton = findViewById(R.id.backButton); // Initialize back button
    }

    private void setupInitialDates() {
        // Set start date to the beginning of the current month
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        updateDateLabel(startDateTextView, startCalendar);

        // Set end date to the current day
        updateDateLabel(endDateTextView, endCalendar);
    }

    private void setupClickListeners() {
        // Set up the new back button to finish the activity
        backButton.setOnClickListener(v -> finish());

        startDateTextView.setOnClickListener(v -> showDatePickerDialog(startDateTextView, startCalendar));
        endDateTextView.setOnClickListener(v -> showDatePickerDialog(endDateTextView, endCalendar));

        downloadReportButtonFinal.setOnClickListener(v -> {
            if (startCalendar.getTimeInMillis() > endCalendar.getTimeInMillis()) {
                Toast.makeText(this, "Start date cannot be after end date.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get selected filters
            long startDate = startCalendar.getTimeInMillis();
            // Set end time to the very end of the selected day
            endCalendar.set(Calendar.HOUR_OF_DAY, 23);
            endCalendar.set(Calendar.MINUTE, 59);
            endCalendar.set(Calendar.SECOND, 59);
            long endDate = endCalendar.getTimeInMillis();

            RadioButton selectedTypeButton = findViewById(filterTypeToggle.getCheckedRadioButtonId());
            String entryType = selectedTypeButton.getText().toString();

            RadioButton selectedModeButton = findViewById(filterModeToggle.getCheckedRadioButtonId());
            String paymentMode = selectedModeButton.getText().toString();

            // Return filters to the previous activity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("startDate", startDate);
            resultIntent.putExtra("endDate", endDate);
            resultIntent.putExtra("entryType", entryType);
            resultIntent.putExtra("paymentMode", paymentMode);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    private void showDatePickerDialog(TextView dateTextView, Calendar calendar) {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateLabel(dateTextView, calendar);
        };

        new DatePickerDialog(this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel(TextView dateTextView, Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        dateTextView.setText(sdf.format(calendar.getTime()));
    }
}
