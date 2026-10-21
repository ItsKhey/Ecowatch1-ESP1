package com.example.myecowatch1.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.utils.PhotoCountBadge;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reports = new ArrayList<>();
    private OnReportClickListener listener;
    private boolean isSelectionMode = false;
    private Set<Integer> selectedIds = new HashSet<>();
    private String currentView = "Inbox"; // Inbox, Archive, Trash

    public interface OnReportClickListener {
        void onReportClick(Report report);
        void onLongReportClick(Report report);
        void onDeleteClick(Report report);
        void onEditClick(Report report);
        void onRestoreClick(Report report);
        void onSelectionChanged(int count);
    }

    public void setCurrentView(String view) {
        this.currentView = view;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        isSelectionMode = selectionMode;
        if (!selectionMode) selectedIds.clear();
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedIds.clear();
        for (Report r : reports) selectedIds.add(r.getId());
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedIds.size());
    }

    public void unselectAll() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public void setOnReportClickListener(OnReportClickListener listener) {
        this.listener = listener;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
        notifyDataSetChanged();
    }

    public List<Report> getReports() {
        return reports;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reports.get(position);
        holder.title.setText(report.getTitle() != null ? report.getTitle() : "Untitled Report");
        
        String reportId = report.getReportId();
        if (reportId == null || reportId.isEmpty()) {
            reportId = report.getMongoId();
        }
        if (reportId == null || reportId.isEmpty()) {
            reportId = report.getId() > 0 ? "LOCAL-" + report.getId() : "N/A";
        }
        holder.reportId.setText("ID: " + reportId);

        String category = report.getWasteType() != null ? report.getWasteType() : "Unknown";
        String dateStr = "Just now";
        
        if (report.getCreatedAt() != null) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = inputFormat.parse(report.getCreatedAt());

                if (date != null) {
                    SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.US);
                    displayFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
                    dateStr = displayFormat.format(date);
                }
            } catch (Exception e) {
                dateStr = report.getCreatedAt();
            }
        }
        
        holder.typeDate.setText(category + " • " + dateStr);
        String statusText = report.getStatus();
        holder.status.setText("Status: " + (statusText != null ? statusText : "Pending"));

        // Multi-select handling
        holder.checkbox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selectedIds.contains(report.getId()));
        holder.checkbox.setOnClickListener(v -> {
            if (selectedIds.contains(report.getId())) {
                selectedIds.remove(report.getId());
            } else {
                selectedIds.add(report.getId());
            }
            if (listener != null) listener.onSelectionChanged(selectedIds.size());
        });

        // UI adjustments based on View (Trash/Archive)
        if ("Trash".equals(currentView)) {
            holder.editBtn.setVisibility(View.GONE);
            holder.restoreBtn.setVisibility(View.VISIBLE);
            holder.deleteBtn.setVisibility(View.VISIBLE); // Permanent delete
        } else if ("Archive".equals(currentView)) {
            holder.restoreBtn.setVisibility(View.VISIBLE);
            holder.deleteBtn.setVisibility(View.VISIBLE);
            if ("Denied".equalsIgnoreCase(report.getStatus())) {
                holder.editBtn.setVisibility(View.VISIBLE);
            } else {
                holder.editBtn.setVisibility(View.GONE);
            }
        } else {
            holder.editBtn.setVisibility(View.VISIBLE);
            holder.restoreBtn.setVisibility(View.GONE);
            holder.deleteBtn.setVisibility(View.VISIBLE);
        }

        String photoUrl = report.getPhotoUrl();
        Object imageSource = null;

        if (report.getLocalPhotoPath() != null) {
            File localFile = new File(report.getLocalPhotoPath());
            if (localFile.exists()) {
                imageSource = localFile;
            }
        }

        if (imageSource == null && photoUrl != null && !photoUrl.isEmpty()) {
            // Relative path, expanded against whichever server this build targets.
            imageSource = ServerConfig.absoluteUrl(holder.itemView.getContext(), photoUrl);
        }

        Glide.with(holder.itemView.getContext())
                .load(imageSource)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(holder.thumb);

        // Multi-photo indicator. Bound unconditionally so a recycled row cannot
        // keep the badge when it is reused for a single-photo report.
        PhotoCountBadge.bind(holder.photoCountBadge, holder.photoCountText,
                report.getPhotoUrls());

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                holder.checkbox.performClick();
            } else if (listener != null) {
                listener.onReportClick(report);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode && listener != null) {
                listener.onLongReportClick(report);
                return true;
            }
            return false;
        });

        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(report);
            }
        });

        holder.editBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(report);
            }
        });

        holder.restoreBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRestoreClick(report);
            }
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView thumb;
        TextView title, typeDate, status, reportId, photoCountText;
        View editBtn, deleteBtn, restoreBtn, photoCountBadge;
        CheckBox checkbox;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.reportThumb);
            title = itemView.findViewById(R.id.reportTitle);
            reportId = itemView.findViewById(R.id.reportIdText);
            typeDate = itemView.findViewById(R.id.reportTypeDate);
            status = itemView.findViewById(R.id.reportStatus);
            
            photoCountBadge = itemView.findViewById(R.id.photoCountBadge);
            photoCountText = itemView.findViewById(R.id.photoCountText);
            editBtn = itemView.findViewById(R.id.editReportBtn);
            deleteBtn = itemView.findViewById(R.id.deleteReportBtn);
            restoreBtn = itemView.findViewById(R.id.restoreReportBtn);
            checkbox = itemView.findViewById(R.id.reportCheckbox);
        }
    }
}