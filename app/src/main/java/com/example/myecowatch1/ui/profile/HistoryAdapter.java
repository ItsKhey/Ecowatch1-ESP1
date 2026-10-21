package com.example.myecowatch1.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.History;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<History> historyList = new ArrayList<>();
    private final List<Integer> selectedIds = new ArrayList<>();
    private boolean isSelectionMode = false;
    private OnSelectionChangedListener selectionListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count, boolean isModeActive);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void setHistoryList(List<History> historyList) {
        this.historyList = historyList;
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean active) {
        this.isSelectionMode = active;
        if (!active) selectedIds.clear();
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedIds.size(), isSelectionMode);
    }

    public void selectAll() {
        selectedIds.clear();
        for (History h : historyList) {
            selectedIds.add(h.getId());
        }
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedIds.size(), isSelectionMode);
    }

    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        History history = historyList.get(position);
        
        String icon = "🔄";
        String action = history.getAction() == null ? "" : history.getAction();
        switch (action) {
            case "SUBMITTED": icon = "📝"; break;
            case "MOVED_TO_TRASH": icon = "🗑️"; break;
            case "ARCHIVED": icon = "📦"; break;
            case "RESTORED": icon = "📤"; break;
            case "STATUS_CHANGED": icon = "⚙️"; break;
            case "UPDATED": icon = "✏️"; break;
            case "PERMANENTLY_DELETED": icon = "❌"; break;
        }

        holder.actionIcon.setText(icon);
        holder.actionTitle.setText(action.replace("_", " "));
        holder.description.setText(history.getDescription());
        
        if (history.getTimestampValue() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
            holder.timestamp.setText(sdf.format(new Date(history.getTimestampValue())));
        } else {
            holder.timestamp.setText("Unknown time");
        }

        // Selection UI
        holder.itemView.setSelected(selectedIds.contains(history.getId()));
        holder.itemView.setAlpha(selectedIds.contains(history.getId()) ? 0.6f : 1.0f);

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                setSelectionMode(true);
                toggleSelection(history.getId());
                return true;
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(history.getId());
            }
        });
    }

    private void toggleSelection(int id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(Integer.valueOf(id));
        } else {
            selectedIds.add(id);
        }
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedIds.size(), isSelectionMode);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView actionIcon, actionTitle, description, timestamp;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            actionIcon = itemView.findViewById(R.id.historyActionIcon);
            actionTitle = itemView.findViewById(R.id.historyActionTitle);
            description = itemView.findViewById(R.id.historyDescription);
            timestamp = itemView.findViewById(R.id.historyTimestamp);
        }
    }
}
