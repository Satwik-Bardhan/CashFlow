package com.example.cashflow;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryFilterFragment extends Fragment {

    private RecyclerView categoriesRecyclerView;
    private CategorySelectionAdapter adapter;
    private List<String> allCategories = new ArrayList<>();
    private List<String> filteredCategories = new ArrayList<>();
    private Set<String> selectedCategories = new HashSet<>();

    private LinearLayout noCategoriesLayout;
    private EditText searchEditText;

    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;

    // Interface to communicate back to the FiltersActivity
    public interface CategoryFilterListener {
        void onCategoriesSelected(Set<String> categories);
    }

    private CategoryFilterListener listener;

    public static CategoryFilterFragment newInstance(ArrayList<String> currentCategories) {
        CategoryFilterFragment fragment = new CategoryFilterFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("currentCategories", currentCategories);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof CategoryFilterListener) {
            listener = (CategoryFilterListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement CategoryFilterListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ArrayList<String> currentCategories = getArguments().getStringArrayList("currentCategories");
            if (currentCategories != null) {
                selectedCategories = new HashSet<>(currentCategories);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        initializeUI(view);
        setupRecyclerView();
        setupSearchListener();

        loadCategories();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Send the final selection back to the activity when the fragment is paused (e.g., when switching tabs)
        if (listener != null) {
            listener.onCategoriesSelected(selectedCategories);
        }
    }

    private void initializeUI(View view) {
        categoriesRecyclerView = view.findViewById(R.id.categories_recycler_view);
        noCategoriesLayout = view.findViewById(R.id.no_categories_layout);
        searchEditText = view.findViewById(R.id.search_category);
        MaterialButton addNewCategoryButton = view.findViewById(R.id.add_new_category_button);
        addNewCategoryButton.setOnClickListener(v -> Toast.makeText(getContext(), "Add New Category Clicked", Toast.LENGTH_SHORT).show());
    }

    private void setupRecyclerView() {
        adapter = new CategorySelectionAdapter(filteredCategories, selectedCategories);
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        categoriesRecyclerView.setAdapter(adapter);
    }

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCategories(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCategories() {
        if (currentUser == null) {
            updateUI();
            return;
        }
        String cashbookId = getActivity().getIntent().getStringExtra("cashbook_id");
        if (cashbookId == null) {
            updateUI();
            return;
        }

        DatabaseReference transactionsRef = mDatabase.child("users").child(currentUser.getUid()).child("cashbooks").child(cashbookId).child("transactions");

        transactionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Set<String> categorySet = new HashSet<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TransactionModel transaction = snapshot.getValue(TransactionModel.class);
                    if (transaction != null && transaction.getTransactionCategory() != null && !transaction.getTransactionCategory().equals("No Category")) {
                        categorySet.add(transaction.getTransactionCategory());
                    }
                }
                allCategories.clear();
                allCategories.addAll(categorySet);
                filterCategories("");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getContext(), "Failed to load categories.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterCategories(String query) {
        filteredCategories.clear();
        if (query.isEmpty()) {
            filteredCategories.addAll(allCategories);
        } else {
            for (String category : allCategories) {
                if (category.toLowerCase().contains(query.toLowerCase())) {
                    filteredCategories.add(category);
                }
            }
        }
        updateUI();
    }

    private void updateUI() {
        if (allCategories.isEmpty()) {
            noCategoriesLayout.setVisibility(View.VISIBLE);
            categoriesRecyclerView.setVisibility(View.GONE);
            searchEditText.setVisibility(View.GONE);
        } else {
            noCategoriesLayout.setVisibility(View.GONE);
            categoriesRecyclerView.setVisibility(View.VISIBLE);
            searchEditText.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }
}
