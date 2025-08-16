package com.example.cashflow;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.util.Calendar;
import java.util.TimeZone;

public class DateFilterFragment extends Fragment {

    private RadioGroup dateOptionsGroup;
    private long currentStartDate = 0;
    private long currentEndDate = 0;

    public interface DateFilterListener {
        void onDateRangeSelected(long startDate, long endDate);
    }

    private DateFilterListener listener;

    // Correction: Added the newInstance method to pass data to the fragment
    public static DateFilterFragment newInstance(long startDate, long endDate) {
        DateFilterFragment fragment = new DateFilterFragment();
        Bundle args = new Bundle();
        args.putLong("startDate", startDate);
        args.putLong("endDate", endDate);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof DateFilterListener) {
            listener = (DateFilterListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement DateFilterListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentStartDate = getArguments().getLong("startDate", 0);
            currentEndDate = getArguments().getLong("endDate", 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_date_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dateOptionsGroup = view.findViewById(R.id.date_options_group);
        // Logic to set the initial radio button selection based on currentStartDate/endDate would go here

        dateOptionsGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            if (checkedId == R.id.radio_all_time) {
                listener.onDateRangeSelected(0, 0);
            } else if (checkedId == R.id.radio_today) {
                listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_yesterday) {
                startCal.add(Calendar.DAY_OF_YEAR, -1);
                endCal.add(Calendar.DAY_OF_YEAR, -1);
                listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_this_month) {
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_last_month) {
                startCal.add(Calendar.MONTH, -1);
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                endCal.add(Calendar.MONTH, -1);
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
            } else if (checkedId == R.id.radio_single_day) {
                showSingleDatePicker();
            } else if (checkedId == R.id.radio_date_range) {
                showDateRangePicker();
            }
        });
    }

    private void showSingleDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startCal.setTimeInMillis(selection);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);

            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endCal.setTimeInMillis(selection);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);

            listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> dateRangePicker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select dates")
                        .build();

        dateRangePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startCal.setTimeInMillis(selection.first);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);

            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endCal.setTimeInMillis(selection.second);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);

            listener.onDateRangeSelected(startCal.getTimeInMillis(), endCal.getTimeInMillis());
        });

        dateRangePicker.show(getParentFragmentManager(), "DATE_RANGE_PICKER");
    }
}
