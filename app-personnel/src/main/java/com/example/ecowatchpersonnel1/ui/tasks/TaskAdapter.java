package com.example.ecowatchpersonnel1.ui.tasks;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.utils.NotificationDateFormatter;
import com.example.ecowatchpersonnel1.utils.WasteScaleDisplay;

import java.util.ArrayList;
import java.util.List;

/** Renders one cleaning assignment per row. */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {

    public interface Listener {
        void onTaskClick(PersonnelTask task);
    }

    private final List<PersonnelTask> items = new ArrayList<>();
    private final Listener listener;

    public TaskAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<PersonnelTask> tasks) {
        items.clear();
        if (tasks != null) {
            items.addAll(tasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PersonnelTask task = items.get(position);

        holder.status.setText(task.statusLabel());
        holder.status.setBackgroundTintList(ColorStateList.valueOf(statusColor(task)));

        holder.title.setText(task.reportTitle());

        String category = task.report == null ? null : task.report.category;
        if (category == null || category.trim().isEmpty()) {
            holder.category.setVisibility(View.GONE);
        } else {
            holder.category.setVisibility(View.VISIBLE);
            holder.category.setText(category.trim());
        }

        holder.scale.setText(WasteScaleDisplay.labelFor(
                holder.itemView.getContext(), task.getWasteScale()));

        String description = task.report == null ? null : task.report.description;
        if (description == null || description.trim().isEmpty()) {
            holder.description.setVisibility(View.GONE);
        } else {
            holder.description.setVisibility(View.VISIBLE);
            holder.description.setText(description.trim());
        }

        holder.evidenceCount.setText(
                "Before " + task.beforeCount + " · After " + task.afterCount);

        if (task.reportId == null || task.reportId.trim().isEmpty()) {
            holder.reportId.setVisibility(View.GONE);
        } else {
            holder.reportId.setVisibility(View.VISIBLE);
            holder.reportId.setText("Report " + task.reportId);
        }

        String when = task.assignedAt != null ? task.assignedAt : task.createdAt;
        holder.date.setText(NotificationDateFormatter.format(when));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTaskClick(task);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int statusColor(PersonnelTask task) {
        if (task == null) return Color.parseColor("#607D8B");
        if (task.awaitingApproval()
                || PersonnelTask.STATUS_UNDER_APPROVAL.equalsIgnoreCase(task.status)
                || "UNDER APPROVAL".equalsIgnoreCase(task.status)) {
            return Color.parseColor("#8E24AA");
        }
        String status = task.status;
        if (status == null) return Color.parseColor("#607D8B");
        switch (status) {
            case PersonnelTask.STATUS_ASSIGNED:
                return Color.parseColor("#F57C00");
            case PersonnelTask.STATUS_ACCEPTED:
                return Color.parseColor("#1976D2");
            case PersonnelTask.STATUS_IN_PROGRESS:
                return Color.parseColor("#F9A825");
            case PersonnelTask.STATUS_UNDER_APPROVAL:
                return Color.parseColor("#8E24AA");
            case PersonnelTask.STATUS_COMPLETED:
                return Color.parseColor("#2E7D32");
            case PersonnelTask.STATUS_CANCELLED:
                return Color.parseColor("#C62828");
            default:
                return Color.parseColor("#607D8B");
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView status;
        final TextView date;
        final TextView title;
        final TextView category;
        final TextView scale;
        final TextView description;
        final TextView evidenceCount;
        final TextView reportId;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            status = itemView.findViewById(R.id.taskStatus);
            date = itemView.findViewById(R.id.taskDate);
            title = itemView.findViewById(R.id.taskTitle);
            category = itemView.findViewById(R.id.taskCategory);
            scale = itemView.findViewById(R.id.taskScale);
            description = itemView.findViewById(R.id.taskDescription);
            evidenceCount = itemView.findViewById(R.id.taskEvidenceCount);
            reportId = itemView.findViewById(R.id.taskReportId);
        }
    }
}
