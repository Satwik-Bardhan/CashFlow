package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final List<CategoryModel> categoryList;
    private final Context context;
    private final OnCategoryClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryModel category);
    }

    public CategoryAdapter(List<CategoryModel> categoryList, Context context, OnCategoryClickListener listener) {
        this.categoryList = categoryList;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryModel category = categoryList.get(position);
        holder.bind(category, position);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public void setSelectedPosition(int position) {
        int oldSelected = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldSelected);
        notifyItemChanged(selectedPosition);
    }

    public void clearSelection() {
        int oldSelected = selectedPosition;
        selectedPosition = RecyclerView.NO_POSITION;
        notifyItemChanged(oldSelected);
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryNameTextView;
        View categoryColorDot;
        LinearLayout categoryChipLayout;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
            categoryColorDot = itemView.findViewById(R.id.categoryColorDot);
            categoryChipLayout = itemView.findViewById(R.id.categoryChipLayout);
        }

        void bind(final CategoryModel category, final int position) {
            categoryNameTextView.setText(category.getName());

            try {
                int color = Color.parseColor(category.getColorHex());
                // The view is a simple View, setting its background color is sufficient.
                categoryColorDot.getBackground().mutate().setTint(color);
            } catch (Exception e) {
                categoryColorDot.getBackground().mutate().setTint(ContextCompat.getColor(context, R.color.category_default));
            }

            if (selectedPosition == position) {
                categoryChipLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.balance_blue));
                categoryNameTextView.setTextColor(Color.WHITE);
            } else {
                categoryChipLayout.setBackgroundColor(Color.WHITE);
                categoryNameTextView.setTextColor(Color.BLACK);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                    // The activity will finish, so no need to update selection state here.
                }
            });
        }
    }
}
