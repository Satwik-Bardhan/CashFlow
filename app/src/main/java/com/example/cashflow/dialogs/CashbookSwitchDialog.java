package com.example.cashflow.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cashflow.R;
import com.example.cashflow.adapters.CashbookAdapter;
import com.example.cashflow.models.Cashbook;
import com.example.cashflow.CashbookModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CashbookSwitchDialog extends DialogFragment {

    // UI Components
    private RecyclerView cashbookRecyclerView;
    private Button cancelButton, addNewButton;
    private ImageView closeButton;
    private LinearLayout emptyStateLayout, loadingLayout;

    // Data & Adapter
    private CashbookAdapter cashbookAdapter;
    private List<Cashbook> cashbookList;

    // Firebase
    private DatabaseReference databaseReference;
    private String currentUserId;

    public interface CashbookSwitchListener {
        void onCashbookSwitched(Cashbook cashbook);
        void onCashbookAdded(Cashbook cashbook);
    }

    private CashbookSwitchListener switchListener;

    public void setCashbookSwitchListener(CashbookSwitchListener listener) {
        this.switchListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_CashFlow);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("cashbooks");
        }
        cashbookList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_switch_cashbook, container, false);

        initViews(view);
        setupRecyclerView();
        setupClickListeners();
        showLoading(true);
        loadCashbooks();

        return view;
    }

    private void initViews(View view) {
        cashbookRecyclerView = view.findViewById(R.id.cashbookRecyclerView);
        cancelButton = view.findViewById(R.id.cancelButton);
        addNewButton = view.findViewById(R.id.addNewButton);
        closeButton = view.findViewById(R.id.closeButton);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        loadingLayout = view.findViewById(R.id.loadingLayout);
    }

    private void setupClickListeners() {
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dismiss());
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismiss());
        }
        if (addNewButton != null) {
            addNewButton.setOnClickListener(v -> showAddCashbookDialog());
        }
    }

    private void setupRecyclerView() {
        cashbookRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        cashbookAdapter = new CashbookAdapter(cashbookList, new CashbookAdapter.OnCashbookClickListener() {
            @Override
            public void onCashbookClick(Cashbook cashbook) {
                if (!cashbook.isCurrent()) {
                    switchToCashbook(cashbook);
                }
            }

            @Override
            public void onMenuClick(Cashbook cashbook, View anchorView) {
                showCashbookOptionsMenu(cashbook, anchorView);
            }
        });

        cashbookRecyclerView.setAdapter(cashbookAdapter);
    }

    private void showLoading(boolean show) {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        if (show) {
            if (cashbookRecyclerView != null) {
                cashbookRecyclerView.setVisibility(View.GONE);
            }
            if (emptyStateLayout != null) {
                emptyStateLayout.setVisibility(View.GONE);
            }
        } else {
            if (cashbookList.isEmpty()) {
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                }
                if (cashbookRecyclerView != null) {
                    cashbookRecyclerView.setVisibility(View.GONE);
                }
            } else {
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(View.GONE);
                }
                if (cashbookRecyclerView != null) {
                    cashbookRecyclerView.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void loadCashbooks() {
        if (databaseReference == null) {
            showLoading(false);
            Toast.makeText(getContext(), "Please log in to access cashbooks", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                cashbookList.clear();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    CashbookModel cashbookModel = snapshot.getValue(CashbookModel.class);
                    if (cashbookModel != null) {
                        // Convert CashbookModel to Cashbook for dialog
                        Cashbook cashbook = new Cashbook();
                        cashbook.setId(snapshot.getKey());
                        cashbook.setName(cashbookModel.getName());
                        cashbook.setCurrent(false); // You'll need to determine current status
                        cashbook.setTotalBalance(0.0); // Calculate if needed
                        cashbookList.add(cashbook);
                    }
                }

                cashbookAdapter.updateCashbooks(cashbookList);
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showLoading(false);
                Toast.makeText(getContext(), "Error loading cashbooks: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void switchToCashbook(Cashbook cashbook) {
        showLoading(true);

        if (switchListener != null) {
            switchListener.onCashbookSwitched(cashbook);
        }

        Toast.makeText(getContext(), "Switched to " + cashbook.getName(), Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void showCashbookOptionsMenu(Cashbook cashbook, View anchorView) {
        PopupMenu popupMenu = new PopupMenu(getContext(), anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.menu_cashbook_options, popupMenu.getMenu());

        // Disable delete for current cashbook or if it's the only one
        if (cashbook.isCurrent() || cashbookList.size() <= 1) {
            popupMenu.getMenu().findItem(R.id.action_delete_cashbook).setEnabled(false);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_add_cashbook) {
                showAddCashbookDialog();
                return true;
            } else if (itemId == R.id.action_duplicate_cashbook) {
                duplicateCashbook(cashbook);
                return true;
            } else if (itemId == R.id.action_delete_cashbook) {
                showDeleteConfirmation(cashbook);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showAddCashbookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_cashbook, null);
        EditText nameEditText = dialogView.findViewById(R.id.cashbookNameEditText);

        builder.setView(dialogView)
                .setTitle("Add New Cashbook")
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = nameEditText.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createNewCashbook(name);
                    } else {
                        Toast.makeText(getContext(), "Please enter a cashbook name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void duplicateCashbook(Cashbook originalCashbook) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_cashbook, null);
        EditText nameEditText = dialogView.findViewById(R.id.cashbookNameEditText);
        nameEditText.setText(originalCashbook.getName() + " - Copy");

        builder.setView(dialogView)
                .setTitle("Duplicate Cashbook")
                .setPositiveButton("Duplicate", (dialog, which) -> {
                    String name = nameEditText.getText().toString().trim();
                    if (!name.isEmpty()) {
                        duplicateCashbookWithName(originalCashbook, name);
                    } else {
                        Toast.makeText(getContext(), "Please enter a cashbook name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation(Cashbook cashbook) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Cashbook")
                .setMessage("Are you sure you want to delete \"" + cashbook.getName() + "\"?\n\nThis will permanently delete all transactions in this cashbook. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteCashbook(cashbook))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewCashbook(String name) {
        if (databaseReference == null) return;

        String cashbookId = databaseReference.push().getKey();
        if (cashbookId == null) return;

        CashbookModel newCashbook = new CashbookModel(cashbookId, name);

        databaseReference.child(cashbookId).setValue(newCashbook)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Cashbook created successfully", Toast.LENGTH_SHORT).show();
                    if (switchListener != null) {
                        Cashbook dialogCashbook = new Cashbook();
                        dialogCashbook.setId(cashbookId);
                        dialogCashbook.setName(name);
                        switchListener.onCashbookAdded(dialogCashbook);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to create cashbook: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void duplicateCashbookWithName(Cashbook original, String newName) {
        if (databaseReference == null) return;

        String cashbookId = databaseReference.push().getKey();
        if (cashbookId == null) return;

        CashbookModel duplicatedCashbook = new CashbookModel(cashbookId, newName);

        databaseReference.child(cashbookId).setValue(duplicatedCashbook)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Cashbook duplicated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to duplicate cashbook: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteCashbook(Cashbook cashbook) {
        if (databaseReference == null) return;

        databaseReference.child(cashbook.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Cashbook deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to delete cashbook: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }
}
