package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataSelectionAdapter extends RecyclerView.Adapter<DataSelectionAdapter.ViewHolder> {

    private List<File> directories;
    private List<Boolean> uploadedStatusList;
    private final Set<File> selectedDirectories = new HashSet<>();

    public DataSelectionAdapter(List<File> dirs, List<Boolean> uploadedList) {
        this.directories = new ArrayList<>(dirs);
        this.uploadedStatusList = new ArrayList<>(uploadedList);
    }


    public void updateDirectories(List<File> dirs, List<Boolean> uploadedList) {
        this.directories = new ArrayList<>(dirs);
        this.uploadedStatusList = new ArrayList<>(uploadedList);

        // 如果某个文件夹变为了已上传，自动取消它的勾选状态
        Set<File> toRemove = new HashSet<>();
        for (File file : selectedDirectories) {
            int index = directories.indexOf(file);
            if (index != -1 && uploadedStatusList.get(index)) {
                toRemove.add(file);
            }
        }
        selectedDirectories.removeAll(toRemove);

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_data_directory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File dir = directories.get(position);
        boolean isUploaded = uploadedStatusList.get(position);
        boolean isSelected = selectedDirectories.contains(dir);

        holder.tvDirectoryName.setText(dir.getName());

        // 关键：先移除监听器，防止在设置 checked 状态时触发旧的逻辑
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setOnClickListener(null);

        if (isUploaded) {
            // 已上传状态
            holder.tvDirectoryName.setTextColor(0xFF999999);
            holder.checkBox.setEnabled(false);
            holder.checkBox.setChecked(false);
            holder.itemView.setAlpha(0.5f);
            holder.itemView.setOnClickListener(null);
        } else {
            // 未上传状态
            holder.tvDirectoryName.setTextColor(0xFF000000);
            holder.checkBox.setEnabled(true);
            holder.itemView.setAlpha(1.0f);

            // 根据集合中的状态设置勾选
            holder.checkBox.setChecked(isSelected);

            // 点击整行或 CheckBox 都会触发切换逻辑
            View.OnClickListener toggleListener = v -> {
                toggleSelection(dir, position);
            };

            holder.itemView.setOnClickListener(toggleListener);
            holder.checkBox.setOnClickListener(toggleListener);
        }
    }

    // 内部切换选中的私有方法
    private void toggleSelection(File dir, int position) {
        if (selectedDirectories.contains(dir)) {
            selectedDirectories.remove(dir);
        } else {
            selectedDirectories.add(dir);
        }
        // 关键：通知该条目数据改变，重新触发 onBindViewHolder
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return directories.size();
    }

    public List<File> getSelectedDirectories() {
        // 返回当前勾选的列表给 Activity
        return new ArrayList<>(selectedDirectories);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDirectoryName;
        CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDirectoryName = itemView.findViewById(R.id.tv_directory_name);
            checkBox = itemView.findViewById(R.id.checkbox_select);
        }
    }
}