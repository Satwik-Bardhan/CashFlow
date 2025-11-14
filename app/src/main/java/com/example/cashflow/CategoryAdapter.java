package com.example.cashflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue; // [FIX] Added for ThemeUtil
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
        // [FIX] Inflates the correct layout file name from your list
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

    // Call this method from your activity to update the selection
    @SuppressLint("NotifyDataSetChanged")
    public void setSelectedCategory(CategoryModel category) {
        if (category == null || category.getName() == null) {
            selectedPosition = RecyclerView.NO_POSITION;
            notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryList.get(i).getName().equals(category.getName())) {
                selectedPosition = i;
                notifyDataSetChanged(); // Refresh the whole list to show selection
                return;
            }
        }
        // If category not found, deselect all
        selectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
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

            // Set the color of the dot
            try {
                int color = Color.parseColor(category.getColorHex());
                Drawable background = categoryColorDot.getBackground();
                if (background instanceof GradientDrawable) {
                    ((GradientDrawable) background.mutate()).setColor(color);
                }
            } catch (Exception e) {
                // Fallback to a default color if parsing fails
                int defaultColor = ContextCompat.getColor(context, R.color.category_default);
                Drawable background = categoryColorDot.getBackground();
                if (background instanceof GradientDrawable) {
                    ((GradientDrawable) background.mutate()).setColor(defaultColor);
                }
            }

            // --- [IMPROVED] Update background and text color based on selection state ---
            if (selectedPosition == position) {
                // Selected state: Use theme's balance color
                categoryChipLayout.setBackgroundColor(ThemeUtil.getThemeAttrColor(context, R.attr.balanceColor));
                categoryNameTextView.setTextColor(Color.WHITE);
            } else {
                // Unselected state: Use theme-aware drawable and text color
                categoryChipLayout.setBackground(ContextCompat.getDrawable(context, R.drawable.rounded_input_background));
                categoryNameTextView.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.textColorPrimary));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);

                    int previousSelectedPosition = selectedPosition;
                    selectedPosition = getAdapterPosition();

                    // [FIX] Efficiently update only the changed items
                    if (previousSelectedPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(previousSelectedPosition);
                    }
                    notifyItemChanged(selectedPosition);
                }
            });
        }
    }

    // [FIX] Added a simple helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}