package com.example.ecowatchpersonnel1.ui.alerts;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationIds;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationRepository;
import com.example.ecowatchpersonnel1.ui.tasks.TaskDetailActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The cleaner's alerts: assignment events, each row leading back to its task.
 *
 * <p>Two tabs over one cached list. Deleting from All trashes the row, which is
 * reversible; deleting from Trash destroys it, which is not. The two are kept
 * visually and behaviourally distinct because a user who taps delete twice, on
 * two screens, should not get two different surprises.
 */
public class NotificationsFragment extends Fragment {

    private PersonnelNotificationRepository repository;
    private NotificationsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView emptyText;
    private ImageButton checkboxBtn;
    private View bulkActionsBar;
    private MaterialButton bulkDeleteBtn;
    private MaterialButton bulkRestoreBtn;
    private TextView tabAll;
    private TextView tabTrash;

    private String currentTab = NotificationsAdapter.TAB_ALL;

    /** Latest list per tab, held so a tab switch can render before Room re-emits. */
    private List<PersonnelNotification> allItems = Collections.emptyList();
    private List<PersonnelNotification> trashItems = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = PersonnelNotificationRepository.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.notificationsRecyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        progress = view.findViewById(R.id.notificationsProgress);
        emptyText = view.findViewById(R.id.notificationsEmpty);
        checkboxBtn = view.findViewById(R.id.checkboxBtn);
        bulkActionsBar = view.findViewById(R.id.bulkActionsBar);
        bulkDeleteBtn = view.findViewById(R.id.bulkDeleteBtn);
        bulkRestoreBtn = view.findViewById(R.id.bulkRestoreBtn);
        tabAll = view.findViewById(R.id.tabAll);
        tabTrash = view.findViewById(R.id.tabTrash);

        adapter = new NotificationsAdapter(new NotificationsAdapter.Listener() {
            @Override
            public void onNotificationClick(PersonnelNotification notification) {
                openNotification(notification);
            }

            @Override
            public void onDelete(PersonnelNotification notification) {
                if (isTrashTab()) confirmPermanentDelete(singleId(notification));
                else repository.softDelete(singleId(notification));
            }

            @Override
            public void onRestore(PersonnelNotification notification) {
                repository.restore(singleId(notification));
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateSelectionState(selectedCount);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> {
            adapter.setMultiSelectMode(false);
            repository.refresh();
        });

        view.findViewById(R.id.markAllReadBtn).setOnClickListener(v -> repository.markAllAsRead());

        if (checkboxBtn != null) {
            checkboxBtn.setOnClickListener(v -> adapter.toggleMultiSelectMode());
        }

        if (bulkDeleteBtn != null) {
            bulkDeleteBtn.setOnClickListener(v -> {
                Set<String> selected = adapter.getSelectedIds();
                if (selected.isEmpty()) {
                    Toast.makeText(requireContext(), "No notifications selected.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isTrashTab()) confirmPermanentDelete(selected);
                else confirmSoftDelete(selected);
            });
        }

        if (bulkRestoreBtn != null) {
            bulkRestoreBtn.setOnClickListener(v -> {
                Set<String> selected = adapter.getSelectedIds();
                if (selected.isEmpty()) {
                    Toast.makeText(requireContext(), "No notifications selected.", Toast.LENGTH_SHORT).show();
                    return;
                }
                repository.restore(selected);
                adapter.setMultiSelectMode(false);
                Toast.makeText(requireContext(),
                        selected.size() == 1
                                ? "Notification restored."
                                : selected.size() + " notifications restored.",
                        Toast.LENGTH_SHORT).show();
            });
        }

        setupTabs();

        repository.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean busy = loading != null && loading;
            progress.setVisibility(busy && adapter.getItemCount() == 0
                    ? View.VISIBLE : View.GONE);
            if (!busy) {
                swipeRefresh.setRefreshing(false);
            }
        });

        repository.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && getContext() != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });

        repository.getNotifications().observe(getViewLifecycleOwner(), notifications -> {
            allItems = notifications == null ? Collections.emptyList() : notifications;
            if (tabAll != null) {
                tabAll.setText("All (" + allItems.size() + ")");
            }
            renderCurrentTab();
        });

        repository.getTrashNotifications().observe(getViewLifecycleOwner(), trashed -> {
            trashItems = trashed == null ? Collections.emptyList() : trashed;
            renderCurrentTab();
        });

        repository.getTrashCount().observe(getViewLifecycleOwner(), count -> {
            if (tabTrash != null) {
                tabTrash.setText("Trash (" + (count == null ? 0 : count) + ")");
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null) {
            repository.refresh();
        }
    }

    private void setupTabs() {
        if (tabAll == null || tabTrash == null) return;

        tabAll.setOnClickListener(v -> selectTab(NotificationsAdapter.TAB_ALL));
        tabTrash.setOnClickListener(v -> selectTab(NotificationsAdapter.TAB_TRASH));
    }

    /**
     * Switches tabs and re-renders from the cached list for that tab.
     *
     * <p>Both lists are already observed, so the switch is a local repaint. That is
     * what lets Trash show cached rows on a cold, offline start instead of an
     * empty list while a network call is in flight.
     */
    private void selectTab(String tab) {
        currentTab = tab;
        adapter.setTab(tab);
        updateTabPills();
        renderCurrentTab();
    }

    private void updateTabPills() {
        if (tabAll == null || tabTrash == null) return;

        boolean allActive = !isTrashTab();
        tabAll.setBackgroundResource(allActive ? R.drawable.active_pill_background : 0);
        tabAll.setTextColor(ContextCompat.getColor(requireContext(),
                allActive ? R.color.white : R.color.green));
        tabTrash.setBackgroundResource(allActive ? 0 : R.drawable.active_pill_background);
        tabTrash.setTextColor(ContextCompat.getColor(requireContext(),
                allActive ? R.color.green : R.color.white));

        // Restore only applies to trashed rows, and selecting is only meaningful
        // where rows exist, so the bar is re-evaluated per tab rather than once.
        updateSelectionState(adapter.isMultiSelectMode() ? adapter.getSelectedIds().size() : 0);
    }

    private boolean isTrashTab() {
        return NotificationsAdapter.TAB_TRASH.equals(currentTab);
    }

    /** Renders whichever list belongs to the current tab. */
    private void renderCurrentTab() {
        List<PersonnelNotification> items = isTrashTab() ? trashItems : allItems;
        adapter.submit(items);

        boolean empty = items.isEmpty();
        emptyText.setText(isTrashTab()
                ? R.string.notifications_trash_empty
                : R.string.notifications_empty);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        // The spinner is left entirely to the loading observer, which already hides
        // it when a refresh settles. Forcing it here would fight that observer and
        // can leave it stuck on screen.
    }

    private void updateSelectionState(int selectedCount) {
        boolean inSelectionMode = adapter.isMultiSelectMode();
        boolean trashTab = isTrashTab();

        if (bulkActionsBar != null) {
            bulkActionsBar.setVisibility(inSelectionMode && selectedCount > 0 ? View.VISIBLE : View.GONE);
        }
        if (bulkDeleteBtn != null) {
            bulkDeleteBtn.setText(selectedCount > 0 ? "Delete (" + selectedCount + ")" : "Delete");
        }
        if (bulkRestoreBtn != null) {
            // Hidden outside Trash: there is nothing to restore in the All tab.
            bulkRestoreBtn.setVisibility(trashTab ? View.VISIBLE : View.GONE);
        }
        if (checkboxBtn != null) {
            checkboxBtn.setAlpha(inSelectionMode ? 1.0f : 0.7f);
        }
    }

    /** Trashes a selection from the All tab. Reversible from Trash. */
    private void confirmSoftDelete(Set<String> selectedIds) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Move to Trash")
                .setMessage("Move " + selectedIds.size()
                        + (selectedIds.size() == 1 ? " notification" : " notifications")
                        + " to Trash? You can restore "
                        + (selectedIds.size() == 1 ? "it" : "them") + " later.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Move", (dialog, which) -> {
                    repository.softDelete(selectedIds);
                    adapter.setMultiSelectMode(false);
                    Toast.makeText(requireContext(),
                            selectedIds.size() == 1
                                    ? "Moved to Trash."
                                    : selectedIds.size() + " notifications moved to Trash.",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Destroys a selection from the Trash tab. Irreversible. */
    private void confirmPermanentDelete(Set<String> selectedIds) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Notifications")
                .setMessage("Permanently delete " + selectedIds.size()
                        + (selectedIds.size() == 1 ? " notification" : " notifications")
                        + "? This cannot be undone.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteNotificationsPermanently(selectedIds, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (!isAdded()) return;
                            adapter.setMultiSelectMode(false);
                            Toast.makeText(requireContext(), "Notifications deleted.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded()) return;
                            adapter.setMultiSelectMode(false);
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    /** The server id for one row, or null when the row carries neither id field. */
    private Set<String> singleId(PersonnelNotification notification) {
        String id = PersonnelNotificationIds.resolve(notification);
        return id == null ? Collections.emptySet() : Collections.singleton(id);
    }

    private void openNotification(PersonnelNotification notification) {
        if (notification == null) return;
        // A trashed row has nothing left to open: the task it referenced may be
        // long finished, and the All tab is where an alert is acted on.
        if (isTrashTab()) return;

        repository.markAsRead(notification);

        if (notification.opensTask()) {
            startActivity(TaskDetailActivity.intentFor(
                    requireContext(), notification.assignmentId.trim()));
        }
    }
}
