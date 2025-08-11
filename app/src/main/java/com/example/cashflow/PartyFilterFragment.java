package com.example.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;

public class PartyFilterFragment extends Fragment {

    private TextView noPartiesMessage;
    private MaterialButton addNewPartyButton;
    private RecyclerView partiesRecyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_party_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        noPartiesMessage = view.findViewById(R.id.no_parties_message);
        addNewPartyButton = view.findViewById(R.id.add_new_party_button);
        partiesRecyclerView = view.findViewById(R.id.parties_recycler_view);

        // In a real app, you would load the list of parties from your database here.
        // For now, we will just show the "No Parties" message.

        addNewPartyButton.setOnClickListener(v -> {
            // Logic to add a new party would go here.
            Toast.makeText(getContext(), "Add New Party clicked", Toast.LENGTH_SHORT).show();
        });
    }
}
