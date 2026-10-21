package com.example.ecowatchpersonnel1.ui.alerts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationIds;
import com.example.ecowatchpersonnel1.utils.NotificationDateFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders one personnel notification per row with multi-selection support. */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

    /** The tab the list is currently rendering; decides which row actions apply. */
    public static final String TAB_ALL = "All";
    public static final String TAB_TRASH = "Trash";

    public interface Listener {
        void onNotificationClick(PersonnelNotification notification);

        /**
         * The row's delete was tapped.
         *
         * <p>What that means depends on the tab and is resolved by the caller: in
         * All it trashes the row, in Trash it destroys it for good.
         */
        void onDelete(PersonnelNotification notification);

        /** The row's restore was tapped. Only reachable from the Trash tab. */
        void onRestore(PersonnelNotification notification);

        void onSelectionChanged(int selectedCount);
    }

    private final List<PersonnelNotification> items = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final Listener listener;
    private boolean multiSelectMode = false;
    private String currentTab = TAB_ALL;

    public NotificationsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<PersonnelNotification> notifications) {
        items.clear();
        if (notifications != null) {
            items.addAll(notifications);
        }
        notifyDataSetChanged();
    }

    /**
     * Switches the tab the row actions apply to.
     *
     * <p>The selected set is dropped on a tab change: ids from the previous tab
     * are not valid selections in the new one, and keeping them would make the
     * selection count and the next bulk action operate on rows that are not shown.
     */
    public void setTab(String tab) {
        String next = TAB_TRASH.equals(tab) ? TAB_TRASH : TAB_ALL;
        if (next.equals(this.currentTab)) return;
        this.currentTab = next;
        multiSelectMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    public String getTab() {
        return currentTab;
    }

    public boolean isTrashTab() {
        return TAB_TRASH.equals(currentTab);
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public void setMultiSelectMode(boolean enabled) {
        if (this.multiSelectMode != enabled) {
            this.multiSelectMode = enabled;
            if (!enabled) {
                selectedIds.clear();
            }
            notifyDataSetChanged();
            if (listener != null) {
                listener.onSelectionChanged(selectedIds.size());
            }
        }
    }

    public void toggleMultiSelectMode() {
        setMultiSelectMode(!multiSelectMode);
    }

    public void selectAll() {
        selectedIds.clear();
        for (PersonnelNotification item : items) {
            String id = notificationIdOf(item);
            if (id != null) selectedIds.add(id);
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedIds.size());
        }
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedIds.size());
        }
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PersonnelNotification notification = items.get(position);
        String id = notificationIdOf(notification);

        holder.unreadDot.setVisibility(notification.isRead ? View.GONE : View.VISIBLE);

        holder.title.setText(notification.title == null || notification.title.trim().isEmpty()
                ? "EcoWatch Personnel" : notification.title.trim());
        holder.message.setText(notification.message == null ? "" : notification.message.trim());
        holder.time.setText(NotificationDateFormatter.formatRelative(notification.createdAt));

        if (notification.opensTask()) {
            holder.reference.setVisibility(View.VISIBLE);
            holder.reference.setText("Task " + notification.assignmentId);
        } else {
            holder.reference.setVisibility(View.GONE);
        }

        if (multiSelectMode) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(id != null && selectedIds.contains(id));
        } else {
            holder.checkBox.setVisibility(View.GONE);
            holder.checkBox.setChecked(false);
        }

        // Restore only makes sense for a row in Trash; delete means trashing in
        // All and destroying in Trash. Row actions are hidden while selecting so a
        // tap cannot race the bulk bar for the same row.
        boolean trashTab = isTrashTab();
        boolean rowActionsEnabled = !multiSelectMode && id != null;
        holder.deleteBtn.setVisibility(rowActionsEnabled ? View.VISIBLE : View.GONE);
        holder.restoreBtn.setVisibility(rowActionsEnabled && trashTab ? View.VISIBLE : View.GONE);

        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(notification);
        });
        holder.restoreBtn.setOnClickListener(v -> {
            if (listener != null) listener.onRestore(notification);
        });

        holder.itemView.setOnClickListener(v -> {
            if (multiSelectMode) {
                if (id != null) {
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id);
                    } else {
                        selectedIds.add(id);
                    }
                    notifyItemChanged(position);
                    if (listener != null) {
                        listener.onSelectionChanged(selectedIds.size());
                    }
                }
            } else {
                if (listener != null) listener.onNotificationClick(notification);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!multiSelectMode) {
                setMultiSelectMode(true);
                if (id != null) {
                    selectedIds.add(id);
                    notifyItemChanged(position);
                    if (listener != null) {
                        listener.onSelectionChanged(selectedIds.size());
                    }
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String notificationIdOf(PersonnelNotification notification) {
        return PersonnelNotificationIds.resolve(notification);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final View unreadDot;
        final ImageView icon;
        final TextView title;
        final TextView message;
        final TextView reference;
        final TextView time;
        final ImageButton deleteBtn;
        final ImageButton restoreBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.notifCheckBox);
            unreadDot = itemView.findViewById(R.id.notifUnreadDot);
            icon = itemView.findViewById(R.id.notifIcon);
            title = itemView.findViewById(R.id.notifTitle);
            message = itemView.findViewById(R.id.notifMessage);
            reference = itemView.findViewById(R.id.notifReportId);
            time = itemView.findViewById(R.id.notifTime);
            deleteBtn = itemView.findViewById(R.id.notifDeleteBtn);
            restoreBtn = itemView.findViewById(R.id.notifRestoreBtn);
        }
    }
}
