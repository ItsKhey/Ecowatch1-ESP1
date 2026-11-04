package com.example.ecowatchpersonnel1.ui.profile;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.local.TaskHistoryEntity;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.models.TaskStatusRules;
import com.example.ecowatchpersonnel1.utils.NotificationDateFormatter;
import com.example.ecowatchpersonnel1.utils.WasteScaleDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * One saved assignment per row, with the whole timeline of it.
 *
 * <p>History is about when things happened, so this shows every milestone the
 * task passed rather than the single date the tasks list shows. A milestone that
 * never happened stays hidden instead of printing an empty line: an assigned
 * task has no accepted date, and four lines where two are blank reads as broken
 * data rather than as work still to do.
 */
public class TaskHistoryAdapter extends RecyclerView.Adapter<TaskHistoryAdapter.ViewHolder> {

    public interface Listener {
        void onHistoryClick(TaskHistoryEntity entry);
    }

    private final List<TaskHistoryEntity> items = new ArrayList<>();
    private final Listener listener;

    public TaskHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TaskHistoryEntity> entries) {
        items.clear();
        if (entries != null) {
            items.addAll(entries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TaskHistoryEntity entry = items.get(position);

        holder.status.setText(statusLabel(entry));
        holder.status.setBackgroundTintList(ColorStateList.valueOf(statusColor(entry)));

        holder.title.setText(title(entry));

        setOptional(holder.category, entry.reportCategory);
        setOptional(holder.description, entry.reportDescription);

        holder.scale.setText(WasteScaleDisplay.labelFor(
                holder.itemView.getContext(), entry.wasteScale));

        // The row's headline date is the moment the task last moved, which is the
        // same ordering the list itself uses.
        String lastMovement = firstNonBlank(
                entry.completedAt, entry.startedAt, entry.acceptedAt, entry.assignedAt,
                entry.updatedAt, entry.createdAt);
        if (lastMovement == null) {
            holder.date.setVisibility(View.GONE);
        } else {
            holder.date.setVisibility(View.VISIBLE);
            holder.date.setText(NotificationDateFormatter.format(lastMovement));
        }

        bindMilestone(holder.assignedAt, entry.assignedAt, R.string.history_assigned_format);
        bindMilestone(holder.acceptedAt, entry.acceptedAt, R.string.history_accepted_format);
        bindMilestone(holder.startedAt, entry.startedAt, R.string.history_started_format);
        // Shown only when this row counts as a revision; older saved rows have
        // no timestamp and stay hidden.
        bindMilestone(holder.revisionStartedAt, isRevision(entry)
                ? entry.revisionRequestedAt : null, R.string.history_revision_started_format);
        bindMilestone(holder.completedAt, entry.completedAt, R.string.history_completed_format);

        setOptional(holder.notes, entry.completionNotes);

        holder.evidenceCount.setText(holder.itemView.getContext().getString(
                R.string.history_evidence_format, entry.beforeCount, entry.afterCount));

        if (entry.reportId == null || entry.reportId.trim().isEmpty()) {
            holder.reportId.setVisibility(View.GONE);
        } else {
            holder.reportId.setVisibility(View.VISIBLE);
            holder.reportId.setText(holder.itemView.getContext()
                    .getString(R.string.history_report_format, entry.reportId));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onHistoryClick(entry);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void bindMilestone(TextView view, String raw, int formatRes) {
        if (raw == null || raw.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(view.getContext().getString(
                formatRes, NotificationDateFormatter.format(raw)));
    }

    private void setOptional(TextView view, String value) {
        if (value == null || value.trim().isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(value.trim());
        }
    }

    private static String title(TaskHistoryEntity entry) {
        if (entry.reportTitle != null && !entry.reportTitle.trim().isEmpty()) {
            return entry.reportTitle.trim();
        }
        return entry.reportId != null && !entry.reportId.trim().isEmpty()
                ? entry.reportId
                : "Cleaning task";
    }

    /**
     * A completed task whose report is still under review is shown as Under
     * Approval rather than Completed: that is the state a cleaner checking
     * "did anyone look at my work yet?" is actually asking about. The rule lives
     * in {@link TaskStatusRules} so this screen and the tasks tab agree.
     */
    private static String statusLabel(TaskHistoryEntity entry) {
        if (isRevision(entry)) {
            return "Revision";
        }
        if (TaskStatusRules.isAwaitingApproval(entry.status, entry.reportStatus)) {
            return "Under Approval";
        }
        return TaskStatusRules.label(entry.status);
    }

    private static boolean isRevision(TaskHistoryEntity entry) {
        return entry.revisionRequested
                || TaskStatusRules.isRevision(entry.status, entry.reportStatus);
    }

    private static int statusColor(TaskHistoryEntity entry) {
        if (isRevision(entry)) {
            return Color.parseColor("#EF6C00");
        }
        if (entry.status == null) return Color.parseColor("#607D8B");
        switch (entry.status) {
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

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView status;
        final TextView date;
        final TextView title;
        final TextView category;
        final TextView scale;
        final TextView description;
        final TextView assignedAt;
        final TextView acceptedAt;
        final TextView startedAt;
        final TextView revisionStartedAt;
        final TextView completedAt;
        final TextView notes;
        final TextView evidenceCount;
        final TextView reportId;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            status = itemView.findViewById(R.id.historyStatus);
            date = itemView.findViewById(R.id.historyDate);
            title = itemView.findViewById(R.id.historyTitle);
            category = itemView.findViewById(R.id.historyCategory);
            scale = itemView.findViewById(R.id.historyScale);
            description = itemView.findViewById(R.id.historyDescription);
            assignedAt = itemView.findViewById(R.id.historyAssignedAt);
            acceptedAt = itemView.findViewById(R.id.historyAcceptedAt);
            startedAt = itemView.findViewById(R.id.historyStartedAt);
            revisionStartedAt = itemView.findViewById(R.id.historyRevisionStartedAt);
            completedAt = itemView.findViewById(R.id.historyCompletedAt);
            notes = itemView.findViewById(R.id.historyNotes);
            evidenceCount = itemView.findViewById(R.id.historyEvidenceCount);
            reportId = itemView.findViewById(R.id.historyReportId);
        }
    }
}