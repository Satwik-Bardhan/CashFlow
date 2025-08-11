package com.example.cashflow;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.Calendar;

public class DateFilterFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_date_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RadioGroup dateOptionsGroup = view.findViewById(R.id.date_options_group);
        dateOptionsGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_single_day) {
                showDatePicker();
            } else if (checkedId == R.id.radio_date_range) {
                // Logic for date range picker will be added here
                Toast.makeText(getContext(), "Date Range selected", Toast.LENGTH_SHORT).show();
            }
            // Handle other date options (Today, Yesterday, etc.)
        });
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (datePicker, year, month, day) -> {
                    // Logic to handle the selected date will be added here
                    String selectedDate = day + "/" + (month + 1) + "/" + year;
                    Toast.makeText(getContext(), "Selected Date: " + selectedDate, Toast.LENGTH_SHORT).show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }
}
