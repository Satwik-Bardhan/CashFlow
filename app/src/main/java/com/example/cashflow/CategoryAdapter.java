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

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, @SuppressLint("RecyclerView") int position) {
        CategoryModel category = categoryList.get(position);

        holder.categoryNameTextView.setText(category.getName());

        int color = Color.parseColor(category.getColorHex());
        GradientDrawable drawable = (GradientDrawable) holder.categoryColorDot.getBackground();
        if (drawable != null) {
            drawable.setColor(color);
        } else {
            holder.categoryColorDot.setBackgroundColor(color);
        }

        if (selectedPosition == position) {
            holder.categoryChipLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.balance_blue));
            holder.categoryNameTextView.setTextColor(Color.WHITE);
        } else {
            holder.categoryChipLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.white));
            holder.categoryNameTextView.setTextColor(Color.BLACK);
        }


        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
                notifyItemChanged(selectedPosition);
                selectedPosition = position;
                notifyItemChanged(selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public void setSelectedPosition(int position) {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(selectedPosition);
        }
        this.selectedPosition = position;
        notifyItemChanged(position);
    }

    public void clearSelection() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            int oldSelected = selectedPosition;
            selectedPosition = RecyclerView.NO_POSITION;
            notifyItemChanged(oldSelected);
        }
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryNameTextView;
        View categoryColorDot;
        LinearLayout categoryChipLayout;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
            categoryColorDot = itemView.findViewById(R.id.categoryColorDot);
            categoryChipLayout = itemView.findViewById(R.id.categoryChipLayout);
        }
    }
}