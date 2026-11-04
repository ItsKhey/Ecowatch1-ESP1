package com.example.ecowatchpersonnel1.ui.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.ecowatchpersonnel1.data.local.TaskHistoryEntity;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.models.TaskStatusRules;
import com.example.ecowatchpersonnel1.data.repository.TaskHistoryRepository;
import com.example.ecowatchpersonnel1.ui.home.DashboardActivity;
import com.example.ecowatchpersonnel1.ui.tasks.TaskDetailActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every task this cleaner has been given, read from the device database.
 *
 * <p>The screen reads from Room and never from a response directly, which is
 * what makes it useful in the field: rows are on screen before the network call
 * returns, and they are still there with no connection. The pull at
 * {@link TaskHistoryRepository#refresh} is what fills the table; its result only
 * matters for the error message and the spinner.
 *
 * <p>Filters run in memory over the saved rows. The table is one cleaner's own
 * assignments, bounded by what they were ever given, so a tab tap stays instant
 * instead of costing a request. The four pills group the statuses so every saved
 * row stays reachable: Revisions covers work an administrator sent back for
 * another pass, while Completed includes finished work still waiting for approval.
 */
public class HistoryFragment extends Fragment {

    private TaskHistoryRepository repository;
    private TaskHistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView emptyText;
    private TextView titleText;
    private EditText searchInput;
    private ImageButton clearSearchBtn;
    private ImageButton sortBtn;

    private TextView tabAll, tabRevisions, tabCompleted, tabCancelled;

    /** Every saved row, unfiltered. The tabs and the search read from here. */
    private final List<TaskHistoryEntity> allEntries = new ArrayList<>();

    private String activeFilter = FILTER_ALL;
    private String searchQuery = "";
    private boolean sortNewestFirst = true;
    private String personnelId;

    /** The four visible pills. Each one is a group, not a single server status. */
    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_REVISIONS = "REVISIONS";
    private static final String FILTER_COMPLETED = "COMPLETED";
    private static final String FILTER_CANCELLED = PersonnelTask.STATUS_CANCELLED;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new TaskHistoryRepository(requireContext());
        personnelId = repository.currentPersonnelId();

        recyclerView = view.findViewById(R.id.historyRecyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        progress = view.findViewById(R.id.historyProgress);
        emptyText = view.findViewById(R.id.historyEmpty);
        titleText = view.findViewById(R.id.historyTitle);
        searchInput = view.findViewById(R.id.etSearch);
        clearSearchBtn = view.findViewById(R.id.btnClearSearch);
        sortBtn = view.findViewById(R.id.btnSort);

        adapter = new TaskHistoryAdapter(this::openTask);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        setupBack(view);
        setupTabs(view);
        setupSearchAndSort();

        swipeRefresh.setOnRefreshListener(() -> refresh(true));

        observeDatabase();
        refresh(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a task detail screen, the status may have moved. Pull
        // again so the row the cleaner just acted on is not left stale.
        if (repository != null) {
            refresh(false);
        }
    }

    // ------------------------------------------------------------- wiring ---

    private void setupBack(View view) {
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack == null) return;

        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else if (requireActivity() instanceof DashboardActivity) {
                ((DashboardActivity) requireActivity()).selectTab(DashboardActivity.Tab.MENU);
            }
        });
    }

    private void setupTabs(View view) {
        tabAll = view.findViewById(R.id.tabHistoryAll);
        tabRevisions = view.findViewById(R.id.tabHistoryRevisions);
        tabCompleted = view.findViewById(R.id.tabHistoryCompleted);
        tabCancelled = view.findViewById(R.id.tabHistoryCancelled);

        if (tabAll != null) tabAll.setOnClickListener(v -> selectTab(FILTER_ALL));
        if (tabRevisions != null) tabRevisions.setOnClickListener(v -> selectTab(FILTER_REVISIONS));
        if (tabCompleted != null) tabCompleted.setOnClickListener(v -> selectTab(FILTER_COMPLETED));
        if (tabCancelled != null) tabCancelled.setOnClickListener(v -> selectTab(FILTER_CANCELLED));

        updateTabPillUI();
    }

    private void selectTab(String filter) {
        activeFilter = filter;
        updateTabPillUI();
        applyFilter();
    }

    private void updateTabPillUI() {
        updateSingleTabUI(tabAll, FILTER_ALL.equals(activeFilter));
        updateSingleTabUI(tabRevisions, FILTER_REVISIONS.equals(activeFilter));
        updateSingleTabUI(tabCompleted, FILTER_COMPLETED.equals(activeFilter));
        updateSingleTabUI(tabCancelled, FILTER_CANCELLED.equals(activeFilter));
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

    private void setupSearchAndSort() {
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s == null ? "" : s.toString().trim().toLowerCase();
                    if (clearSearchBtn != null) {
                        clearSearchBtn.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        if (clearSearchBtn != null) {
            clearSearchBtn.setOnClickListener(v -> {
                if (searchInput != null) searchInput.setText("");
            });
        }

        if (sortBtn != null) {
            sortBtn.setOnClickListener(v -> {
                sortNewestFirst = !sortNewestFirst;
                Toast.makeText(requireContext(),
                        sortNewestFirst ? R.string.history_sort_newest : R.string.history_sort_oldest,
                        Toast.LENGTH_SHORT).show();
                applyFilter();
            });
        }
    }

    // ------------------------------------------------------------ database ---

    /**
     * Subscribes to the saved rows.
     *
     * <p>Bound to the view lifecycle so a rotation mid-request cannot deliver
     * into a destroyed view.
     */
    private void observeDatabase() {
        repository.observeHistory(personnelId).observe(getViewLifecycleOwner(), entries -> {
            allEntries.clear();
            if (entries != null) {
                allEntries.addAll(entries);
            }
            applyFilter();
        });
    }

    /**
     * Pulls every task from the server into the database.
     *
     * @param userInitiated true when a pull-to-refresh asked for it, which is the
     *                     only case that reports a failure. A background sync on
     *                     open stays quiet so a flaky connection does not greet
     *                     the cleaner with a toast over their own history.
     */
    private void refresh(boolean userInitiated) {
        if (repository == null) return;

        repository.refresh(personnelId, savedCount -> {
            if (!isAdded()) return;

            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            if (progress != null) progress.setVisibility(View.GONE);

            if (savedCount < 0) {
                if (userInitiated) {
                    Toast.makeText(requireContext(),
                            R.string.history_offline_notice, Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (titleText != null) {
                titleText.setText(getString(R.string.history_count_title, savedCount));
            }
        });

        if (userInitiated && progress != null) {
            progress.setVisibility(allEntries.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // ------------------------------------------------------------ filtering ---

    private boolean matchesFilter(TaskHistoryEntity entry) {
        if (entry == null) return false;
        if (FILTER_ALL.equals(activeFilter)) return true;
        return activeFilter.equals(groupOf(entry));
    }

    /**
     * The one pill a saved row belongs to, or null for work still in progress.
     *
     * <p>Cancelled wins over revision, and revision wins over completed, so a
     * task that was both sent back and resubmitted is counted once rather than
     * appearing in two pills. Rows that are still assigned, accepted or in
     * progress have no pill of their own anymore and stay reachable under All.
     */
    private String groupOf(TaskHistoryEntity entry) {
        if (entry == null) return null;
        if (PersonnelTask.STATUS_CANCELLED.equalsIgnoreCase(entry.status)) {
            return FILTER_CANCELLED;
        }
        if (isRevision(entry)) return FILTER_REVISIONS;
        if (isCompleted(entry)) return FILTER_COMPLETED;
        return null;
    }

    private boolean isRevision(TaskHistoryEntity entry) {
        return entry.revisionRequested
                || TaskStatusRules.isRevision(entry.status, entry.reportStatus);
    }

    /**
     * Finished work, including a completion whose report is still under review.
     * Those rows have no dedicated pill anymore, so leaving them out would make
     * them visible only under All.
     */
    private boolean isCompleted(TaskHistoryEntity entry) {
        return PersonnelTask.STATUS_COMPLETED.equalsIgnoreCase(entry.status)
                || isUnderApproval(entry);
    }

    /**
     * True when the work is finished but no administrator has signed it off yet.
     *
     * <p>Delegated to {@link TaskStatusRules} rather than reimplemented, so this
     * screen and the tasks tab cannot disagree about the same assignment.
     */
    private boolean isUnderApproval(TaskHistoryEntity entry) {
        return TaskStatusRules.isAwaitingApproval(entry.status, entry.reportStatus);
    }

    private boolean matchesSearch(TaskHistoryEntity entry) {
        if (searchQuery.isEmpty()) return true;
        return contains(entry.reportTitle, searchQuery)
                || contains(entry.reportDescription, searchQuery)
                || contains(entry.reportCategory, searchQuery)
                || contains(entry.reportId, searchQuery)
                || contains(entry.completionNotes, searchQuery)
                || contains(entry.reportStatus, searchQuery)
                || contains(entry.status, searchQuery);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    private void applyFilter() {
        List<TaskHistoryEntity> filtered = new ArrayList<>();
        for (TaskHistoryEntity entry : allEntries) {
            if (matchesFilter(entry) && matchesSearch(entry)) {
                filtered.add(entry);
            }
        }

        sort(filtered);
        if (adapter != null) adapter.submit(filtered);
        updateTabCounts();
        renderEmptyState(filtered.isEmpty());
    }

    /**
     * Orders by the moment the task last moved, falling back to when it was
     * created. Done on the ISO strings rather than parsed dates: the server
     * timestamps are all UTC with the same shape, so they compare correctly as
     * text and this cannot fail on an unparseable value.
     */
    private void sort(List<TaskHistoryEntity> entries) {
        Collections.sort(entries, (a, b) -> {
            String left = lastMovement(a);
            String right = lastMovement(b);
            int comparison = compareTimestamps(left, right);
            return sortNewestFirst ? -comparison : comparison;
        });
    }

    private static String lastMovement(TaskHistoryEntity entry) {
        if (entry == null) return "";
        String value = firstNonBlank(entry.completedAt, entry.startedAt, entry.acceptedAt,
                entry.assignedAt, entry.updatedAt, entry.createdAt);
        // The server sends UTC ISO-8601; drop the zone suffix so a value with an
        // offset does not sort after one with a Z for the wrong reason.
        if (value == null) return "";
        int plus = value.indexOf('+');
        if (plus > 0) return value.substring(0, plus);
        return value.endsWith("Z") ? value.substring(0, value.length() - 1) : value;
    }

    private static int compareTimestamps(String left, String right) {
        String l = left == null ? "" : left;
        String r = right == null ? "" : right;
        if (l.equals(r)) return 0;
        if (l.isEmpty()) return -1;
        if (r.isEmpty()) return 1;
        return l.compareTo(r);
    }

    /**
     * Labels every pill with the number of saved rows behind it.
     *
     * <p>Counted from the same list and by the same grouping the filter uses, so
     * a pill's number is always the number of rows a tap will produce. Revisions
     * follows the revision rule in {@link TaskStatusRules}; Completed includes
     * work whose report is still under approval. Each saved row therefore
     * appears under All and at most one of the other three pills.
     */
    private void updateTabCounts() {
        int total = allEntries.size();
        int revisions = 0;
        int completed = 0;
        int cancelled = 0;

        for (TaskHistoryEntity entry : allEntries) {
            String group = groupOf(entry);
            if (FILTER_REVISIONS.equals(group)) {
                revisions++;
            } else if (FILTER_COMPLETED.equals(group)) {
                completed++;
            } else if (FILTER_CANCELLED.equals(group)) {
                cancelled++;
            }
        }

        setTabCount(tabAll, R.string.filter_all, total);
        setTabCount(tabRevisions, R.string.filter_revisions, revisions);
        setTabCount(tabCompleted, R.string.filter_completed, completed);
        setTabCount(tabCancelled, R.string.filter_cancelled, cancelled);
    }

    private void setTabCount(TextView tab, int labelRes, int count) {
        if (tab == null || !isAdded()) return;
        String label = getString(labelRes);
        tab.setText(getString(R.string.filter_count_format, label, count));
    }

    /**
     * Distinguishes "you have no tasks" from "this filter matched nothing",
     * which are different problems and need different words.
     */
    private void renderEmptyState(boolean listIsEmpty) {
        boolean nothingSaved = allEntries.isEmpty();

        if (emptyText == null || recyclerView == null) return;

        if (!listIsEmpty) {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            return;
        }

        emptyText.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        if (nothingSaved) {
            emptyText.setText(R.string.history_empty);
        } else if (!searchQuery.isEmpty()) {
            emptyText.setText(R.string.history_empty_search);
        } else {
            emptyText.setText(R.string.history_empty_filter);
        }
    }

    // -------------------------------------------------------------- actions ---

    /** Opens the task the row refers to, so history is a way into the work. */
    private void openTask(TaskHistoryEntity entry) {
        if (entry == null || entry.assignmentId == null || entry.assignmentId.trim().isEmpty()) {
            return;
        }
        startActivity(TaskDetailActivity.intentFor(requireContext(), entry.assignmentId));
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
}