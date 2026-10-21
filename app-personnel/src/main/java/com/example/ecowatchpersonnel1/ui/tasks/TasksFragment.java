package com.example.ecowatchpersonnel1.ui.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The cleaner's task list: every assignment, narrowed by a status tab pill.
 *
 * <p>The server returns the whole (bounded) list in one page, so the tabs filter
 * in memory. That keeps a tab tap instant and avoids a request per filter, which
 * matters on the flaky network this app is built for. It also means the count on
 * each tab can be computed from the same list the filter uses, so the number is
 * always the number of rows a tap will produce.
 */
public class TasksFragment extends Fragment {

    private TasksViewModel viewModel;
    private TaskAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView emptyText;

    private TextView tabAll, tabActive, tabAssigned, tabAccepted, tabInProgress, tabUnderApproval, tabCompleted;

    private final List<PersonnelTask> allTasks = new ArrayList<>();
    private String activeFilter = null; // null = all

    /**
     * Not a real assignment status: the server has no {@code UNDER_APPROVAL}
     * status, so this key stands for {@link PersonnelTask#awaitingApproval()} in
     * {@link #matches(PersonnelTask)} instead of {@link #equals} against
     * {@code status}.
     */
    private static final String FILTER_UNDER_APPROVAL = "UNDER_APPROVAL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TasksViewModel.class);

        recyclerView = view.findViewById(R.id.tasksRecyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        progress = view.findViewById(R.id.tasksProgress);
        emptyText = view.findViewById(R.id.tasksEmpty);

        adapter = new TaskAdapter(task -> {
            if (task == null || task.assignmentId == null) return;
            startActivity(TaskDetailActivity.intentFor(requireContext(), task.assignmentId));
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> viewModel.loadTasks());

        setupTabs(view);

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean busy = loading != null && loading;
            if (progress != null) progress.setVisibility(busy && allTasks.isEmpty() ? View.VISIBLE : View.GONE);
            if (!busy && swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && getContext() != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getTasks().observe(getViewLifecycleOwner(), tasks -> {
            allTasks.clear();
            if (tasks != null) {
                allTasks.addAll(tasks);
            }
            applyFilter();
        });

        viewModel.loadTasks();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Return to this list after accepting, starting or completing a task and
        // the row should already show the new status.
        if (viewModel != null) {
            viewModel.loadTasks();
        }
    }

    private void setupTabs(View view) {
        tabAll = view.findViewById(R.id.tabAll);
        tabActive = view.findViewById(R.id.tabActive);
        tabAssigned = view.findViewById(R.id.tabAssigned);
        tabAccepted = view.findViewById(R.id.tabAccepted);
        tabInProgress = view.findViewById(R.id.tabInProgress);
        tabUnderApproval = view.findViewById(R.id.tabUnderApproval);
        tabCompleted = view.findViewById(R.id.tabCompleted);

        if (tabAll != null) tabAll.setOnClickListener(v -> selectTab(null));
        if (tabActive != null) tabActive.setOnClickListener(v -> selectTab("ACTIVE"));
        if (tabAssigned != null) tabAssigned.setOnClickListener(v -> selectTab(PersonnelTask.STATUS_ASSIGNED));
        if (tabAccepted != null) tabAccepted.setOnClickListener(v -> selectTab(PersonnelTask.STATUS_ACCEPTED));
        if (tabInProgress != null) tabInProgress.setOnClickListener(v -> selectTab(PersonnelTask.STATUS_IN_PROGRESS));
        if (tabUnderApproval != null) tabUnderApproval.setOnClickListener(v -> selectTab(FILTER_UNDER_APPROVAL));
        if (tabCompleted != null) tabCompleted.setOnClickListener(v -> selectTab(PersonnelTask.STATUS_COMPLETED));

        updateTabPillUI();
    }

    private void selectTab(String filter) {
        activeFilter = filter;
        updateTabPillUI();
        applyFilter();
    }

    private void updateTabPillUI() {
        updateSingleTabUI(tabAll, activeFilter == null);
        updateSingleTabUI(tabActive, "ACTIVE".equalsIgnoreCase(activeFilter));
        updateSingleTabUI(tabAssigned, PersonnelTask.STATUS_ASSIGNED.equalsIgnoreCase(activeFilter));
        updateSingleTabUI(tabAccepted, PersonnelTask.STATUS_ACCEPTED.equalsIgnoreCase(activeFilter));
        updateSingleTabUI(tabInProgress, PersonnelTask.STATUS_IN_PROGRESS.equalsIgnoreCase(activeFilter));
        updateSingleTabUI(tabUnderApproval, FILTER_UNDER_APPROVAL.equalsIgnoreCase(activeFilter));
        updateSingleTabUI(tabCompleted, PersonnelTask.STATUS_COMPLETED.equalsIgnoreCase(activeFilter));
    }

    private void updateSingleTabUI(TextView tab, boolean isActive) {
        if (tab == null || !isAdded()) return;
        if (isActive) {
            tab.setBackgroundResource(R.drawable.active_pill_background);
            tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        } else {
            tab.setBackground(null);
            tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
        }
    }

    /**
     * Rewrites every tab as "Label (count)" from the unfiltered list.
     *
     * <p>Counted against {@code allTasks} rather than the visible rows, so a tab
     * never advertises zero rows of its own category just because another tab is
     * currently selected. "All" is the size of the list itself.
     */
    private void updateTabCounts() {
        int all = allTasks.size();
        int active = 0;
        int underApproval = 0;
        int assigned = 0;
        int accepted = 0;
        int inProgress = 0;
        int completed = 0;

        for (PersonnelTask task : allTasks) {
            if (task == null) continue;
            if (task.isActive()) active++;
            if (task.awaitingApproval()) underApproval++;
            if (task.status == null) continue;
            if (PersonnelTask.STATUS_ASSIGNED.equals(task.status)) assigned++;
            else if (PersonnelTask.STATUS_ACCEPTED.equals(task.status)) accepted++;
            else if (PersonnelTask.STATUS_IN_PROGRESS.equals(task.status)) inProgress++;
            else if (PersonnelTask.STATUS_COMPLETED.equals(task.status)) completed++;
        }

        setTabCount(tabAll, R.string.filter_all, all);
        setTabCount(tabActive, R.string.filter_active, active);
        setTabCount(tabAssigned, R.string.filter_assigned, assigned);
        setTabCount(tabAccepted, R.string.filter_accepted, accepted);
        setTabCount(tabInProgress, R.string.filter_in_progress, inProgress);
        setTabCount(tabUnderApproval, R.string.filter_under_approval, underApproval);
        setTabCount(tabCompleted, R.string.filter_completed, completed);
    }

    private void setTabCount(TextView tab, int labelRes, int count) {
        if (tab == null || !isAdded()) return;
        String label = getString(labelRes);
        tab.setText(getString(R.string.filter_count_format, label, count));
    }

    private void applyFilter() {
        List<PersonnelTask> filtered = new ArrayList<>();
        for (PersonnelTask task : allTasks) {
            if (matches(task)) filtered.add(task);
        }
        if (adapter != null) adapter.submit(filtered);
        updateTabCounts();

        if (emptyText == null || recyclerView == null) return;

        boolean empty = filtered.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (FILTER_UNDER_APPROVAL.equals(activeFilter)) {
            emptyText.setText(R.string.tasks_empty_for_approval);
        } else {
            emptyText.setText("ACTIVE".equals(activeFilter)
                    ? getString(R.string.tasks_empty_active)
                    : getString(R.string.tasks_empty));
        }
    }

    private boolean matches(PersonnelTask task) {
        if (task == null) return false;
        if (activeFilter == null) return true;
        if ("ACTIVE".equalsIgnoreCase(activeFilter)) return task.isActive();
        if (FILTER_UNDER_APPROVAL.equalsIgnoreCase(activeFilter)) return task.awaitingApproval();
        if (PersonnelTask.STATUS_COMPLETED.equalsIgnoreCase(activeFilter)) return task.isCompleted() && !task.awaitingApproval();
        return activeFilter.equalsIgnoreCase(task.status);
    }
}