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

public class CategoryFilterFragment extends Fragment {

    private TextView noCategoriesMessage;
    private MaterialButton addNewCategoryButton;
    private RecyclerView categoriesRecyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_category_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        noCategoriesMessage = view.findViewById(R.id.no_categories_message);
        addNewCategoryButton = view.findViewById(R.id.add_new_category_button);
        categoriesRecyclerView = view.findViewById(R.id.categories_recycler_view);

        // In a real app, you would load the list of categories from your database here.
        // For now, we will just show the "No Categories" message.

        addNewCategoryButton.setOnClickListener(v -> {
            // Logic to add a new category would go here.
            Toast.makeText(getContext(), "Add New Category clicked", Toast.LENGTH_SHORT).show();
        });
    }
}
