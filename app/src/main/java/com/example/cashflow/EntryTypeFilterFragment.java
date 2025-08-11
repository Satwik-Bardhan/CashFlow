package com.example.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class EntryTypeFilterFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_entry_type_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RadioGroup entryTypeGroup = view.findViewById(R.id.entry_type_group);
        entryTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selection;
            if (checkedId == R.id.radio_cash_in) {
                selection = "Cash In";
            } else if (checkedId == R.id.radio_cash_out) {
                selection = "Cash Out";
            } else {
                selection = "All";
            }
            // In a real app, you would pass this selection back to the Activity
            Toast.makeText(getContext(), "Selected: " + selection, Toast.LENGTH_SHORT).show();
        });
    }
}
