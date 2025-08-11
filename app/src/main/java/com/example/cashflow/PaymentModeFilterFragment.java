package com.example.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PaymentModeFilterFragment extends Fragment {

    private EditText searchPaymentMode;
    private CheckBox checkboxNoMode, checkboxCash, checkboxOnline;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_payment_mode_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        searchPaymentMode = view.findViewById(R.id.search_payment_mode);
        checkboxNoMode = view.findViewById(R.id.checkbox_no_mode);
        checkboxCash = view.findViewById(R.id.checkbox_cash);
        checkboxOnline = view.findViewById(R.id.checkbox_online);

        // In a real app, you would add listeners to these checkboxes
        // to collect the user's selections.

        checkboxCash.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(getContext(), "Cash selected", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
