package com.example.myecowatch1.ui.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.History;
import com.example.myecowatch1.data.repository.ReportRepository;
import com.example.myecowatch1.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ReportRepository repository;
    private SessionManager sessionManager;
    private TextView tabAll, tabSubmitted, tabArchived, tabDenied, tabRestored, tabTrashed, tabDeleted, tabCompleted, tabUpdated, tabTrash;
    private TextView historyTitle, btnSelectAll;
    private EditText etSearch;
    private ImageButton btnSort, btnClearSearch;
    private LinearLayout tabPillContainer;
    private View bulkActionsLayout, tabContainer, searchSortLayout;
    private ImageButton btnDeleteBulk, btnRestoreBulk;
    private boolean isTrashView = false;
    private String currentFilter = "All";
    private String searchQuery = "";
    private boolean isSortNewest = true;
    private int historyCount = 0;
    private int trashCount = 0;
    private List<History> allHistoryList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        repository = new ReportRepository(requireActivity().getApplication());
        sessionManager = new SessionManager(requireContext());

        recyclerView = view.findViewById(R.id.historyRecyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        ImageButton btnBack = view.findViewById(R.id.btnBack);
        
        tabPillContainer = view.findViewById(R.id.tabPillContainer);
        tabAll = view.findViewById(R.id.tabAll);
        tabSubmitted = view.findViewById(R.id.tabSubmitted);
        tabArchived = view.findViewById(R.id.tabArchived);
        tabDenied = view.findViewById(R.id.tabDenied);
        tabRestored = view.findViewById(R.id.tabRestored);
        tabTrashed = view.findViewById(R.id.tabTrashed);
        tabDeleted = view.findViewById(R.id.tabDeleted);
        tabCompleted = view.findViewById(R.id.tabCompleted);
        tabUpdated = view.findViewById(R.id.tabUpdated);
        tabTrash = view.findViewById(R.id.tabTrash);
        
        historyTitle = view.findViewById(R.id.historyTitle);
        bulkActionsLayout = view.findViewById(R.id.bulkActionsLayout);
        tabContainer = view.findViewById(R.id.tabContainer);
        searchSortLayout = view.findViewById(R.id.searchSortLayout);
        etSearch = view.findViewById(R.id.etSearch);
        btnSort = view.findViewById(R.id.btnSort);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        btnSelectAll = view.findViewById(R.id.btnSelectAll);
        btnDeleteBulk = view.findViewById(R.id.btnDeleteBulk);
        btnRestoreBulk = view.findViewById(R.id.btnRestoreBulk);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        setupTabs();
        setupSearchAndSort();

        btnBack.setOnClickListener(v -> {
            if (bulkActionsLayout != null && bulkActionsLayout.getVisibility() == View.VISIBLE) {
                adapter.setSelectionMode(false);
            } else if (isAdded()) {
                getParentFragmentManager().popBackStack();
            }
        });

        adapter.setOnSelectionChangedListener((count, isModeActive) -> {
            if (bulkActionsLayout != null) bulkActionsLayout.setVisibility(isModeActive ? View.VISIBLE : View.GONE);
            if (tabContainer != null) tabContainer.setVisibility(isModeActive ? View.GONE : View.VISIBLE);
            if (searchSortLayout != null) searchSortLayout.setVisibility(isModeActive ? View.GONE : View.VISIBLE);
            
            if (isModeActive) {
                if (historyTitle != null) historyTitle.setText("Selected (" + count + ")");
                if (btnRestoreBulk != null) btnRestoreBulk.setVisibility(isTrashView ? View.VISIBLE : View.GONE);
            } else {
                updateHeaderTitle();
            }
        });

        if (btnSelectAll != null) {
            btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        }

        if (btnRestoreBulk != null) {
            btnRestoreBulk.setOnClickListener(v -> {
                List<Integer> ids = adapter.getSelectedIds();
                if (ids.isEmpty()) return;
                
                repository.restoreHistoryByIds(ids);
                adapter.setSelectionMode(false);
                Toast.makeText(getContext(), "Logs restored", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnDeleteBulk != null) {
            btnDeleteBulk.setOnClickListener(v -> {
                List<Integer> ids = adapter.getSelectedIds();
                if (ids.isEmpty()) return;

                String title = isTrashView ? "Permanent Delete" : "Move to Trash";
                String message = isTrashView 
                    ? "Are you sure you want to permanently delete these " + ids.size() + " logs?"
                    : "Move " + ids.size() + " logs to trash?";

                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(isTrashView ? "Delete" : "Move", (d, w) -> {
                            if (isTrashView) {
                                repository.deleteHistoryByIds(ids);
                            } else {
                                repository.softDeleteHistoryByIds(ids);
                            }
                            adapter.setSelectionMode(false);
                            Toast.makeText(getContext(), isTrashView ? "Logs deleted" : "Moved to trash", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        loadHistoryData();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Stop the spinner when the refresh actually settles. The previous
            // fixed 2s delay hid failures and could stop the spinner before the
            // history reconcile had even run.
            repository.fetchMyReports(() -> {
                if (isAdded() && swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        });

        return view;
    }

    private void setupTabs() {
        tabAll.setOnClickListener(v -> selectTab("All"));
        tabSubmitted.setOnClickListener(v -> selectTab("Submitted"));
        tabArchived.setOnClickListener(v -> selectTab("Archived"));
        tabDenied.setOnClickListener(v -> selectTab("Denied"));
        tabRestored.setOnClickListener(v -> selectTab("Restored"));
        tabTrashed.setOnClickListener(v -> selectTab("Trashed"));
        tabDeleted.setOnClickListener(v -> selectTab("Deleted"));
        tabCompleted.setOnClickListener(v -> selectTab("Completed"));
        tabUpdated.setOnClickListener(v -> selectTab("Updated"));
        tabTrash.setOnClickListener(v -> selectTab("Trash"));
    }

    private void updateTabUI(TextView tab, boolean isActive) {
        if (isActive) {
            tab.setBackgroundResource(R.drawable.active_pill_background);
            tab.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            tab.setBackground(null);
            tab.setTextColor(getResources().getColor(R.color.green, null));
        }
    }

    private void setupSearchAndSort() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().toLowerCase().trim();
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                }
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearch != null) etSearch.setText("");
            });
        }

        if (btnSort != null) {
            btnSort.setOnClickListener(v -> {
                isSortNewest = !isSortNewest;
                Toast.makeText(getContext(), isSortNewest ? "Sorting by Newest" : "Sorting by Oldest", Toast.LENGTH_SHORT).show();
                applyFilter();
            });
        }
    }

    private void selectTab(String filter) {
        currentFilter = filter;
        isTrashView = "Trash".equals(filter);
        
        updateTabUI(tabAll, "All".equals(filter));
        updateTabUI(tabSubmitted, "Submitted".equals(filter));
        updateTabUI(tabArchived, "Archived".equals(filter));
        updateTabUI(tabDenied, "Denied".equals(filter));
        updateTabUI(tabRestored, "Restored".equals(filter));
        updateTabUI(tabTrashed, "Trashed".equals(filter));
        updateTabUI(tabDeleted, "Deleted".equals(filter));
        updateTabUI(tabCompleted, "Completed".equals(filter));
        updateTabUI(tabUpdated, "Updated".equals(filter));
        updateTabUI(tabTrash, "Trash".equals(filter));
        
        applyFilter();
    }

    private void applyFilter() {
        List<History> filteredList = new ArrayList<>();
        List<History> targetSource = isTrashView ? lastFetchedTrashList : allHistoryList;

        if (targetSource == null) return;

        for (History h : targetSource) {
            if (h == null) continue;
            boolean statusMatch = false;
            String action = h.getAction();
            if (action == null) action = "";
            String normAction = action.toUpperCase().replace("_", " ");
            String newStatus = h.getNewStatus();
            boolean isStatusUpdate = normAction.equals("STATUS CHANGED") || normAction.equals("STATUS UPDATE");

            if ("All".equals(currentFilter) || "Trash".equals(currentFilter)) {
                statusMatch = true;
            } else if ("Submitted".equals(currentFilter) && normAction.equals("SUBMITTED")) {
                statusMatch = true;
            } else if ("Archived".equals(currentFilter) && normAction.equals("ARCHIVED")) {
                statusMatch = true;
            } else if ("Denied".equals(currentFilter) && ("Denied".equalsIgnoreCase(newStatus) || (isStatusUpdate && "Denied".equalsIgnoreCase(newStatus)))) {
                statusMatch = true;
            } else if ("Restored".equals(currentFilter) && normAction.equals("RESTORED")) {
                statusMatch = true;
            } else if ("Trashed".equals(currentFilter) && (normAction.equals("TRASHED") || normAction.equals("MOVED TO TRASH"))) {
                statusMatch = true;
            } else if ("Updated".equals(currentFilter) && normAction.equals("UPDATED")) {
                // One grouped entry per edit, written by the backend when the
                // resident saved a change to an existing report.
                statusMatch = true;
            } else if ("Deleted".equals(currentFilter) && (normAction.equals("DELETED") || normAction.equals("PERMANENTLY DELETED"))) {
                statusMatch = true;
            } else if ("Completed".equals(currentFilter) && ("Completed".equalsIgnoreCase(newStatus) || (isStatusUpdate && "Completed".equalsIgnoreCase(newStatus)))) {
                statusMatch = true;
            }

            if (statusMatch) {
                if (searchQuery.isEmpty()) {
                    filteredList.add(h);
                } else if (h.getDescription() != null && h.getDescription().toLowerCase().contains(searchQuery)) {
                    filteredList.add(h);
                } else if (h.getAction() != null && h.getAction().toLowerCase().contains(searchQuery)) {
                    filteredList.add(h);
                }
            }
        }

        // Sorting
        filteredList.sort((h1, h2) -> {
            Long t1 = (h1 != null && h1.getTimestampValue() != null) ? h1.getTimestampValue() : 0L;
            Long t2 = (h2 != null && h2.getTimestampValue() != null) ? h2.getTimestampValue() : 0L;
            return isSortNewest ? t2.compareTo(t1) : t1.compareTo(t2);
        });

        if (adapter != null) {
            adapter.setHistoryList(filteredList);
        }
    }

    private List<History> lastFetchedTrashList = new ArrayList<>();

    private void updateHeaderTitle() {
        if (historyTitle != null) {
            historyTitle.setText("Report History (" + (historyCount + trashCount) + ")");
        }
    }

    private void updateTabCounts() {
        long submittedCount = 0;
        long archivedCount = 0;
        long deniedCount = 0;
        long restoredCount = 0;
        long trashedCount = 0;
        long deletedCount = 0;
        long completedCount = 0;
        long updatedCount = 0;

        for (History h : allHistoryList) {
            if (h == null) continue;
            String action = h.getAction();
            if (action == null) action = "";
            String normAction = action.toUpperCase().replace("_", " ");
            String newStatus = h.getNewStatus();
            boolean isStatusUpdate = normAction.equals("STATUS CHANGED") || normAction.equals("STATUS UPDATE");

            if (normAction.equals("SUBMITTED")) submittedCount++;
            else if (normAction.equals("ARCHIVED")) archivedCount++;
            else if (normAction.equals("RESTORED")) restoredCount++;
            else if (normAction.equals("TRASHED") || normAction.equals("MOVED TO TRASH")) trashedCount++;
            else if (normAction.equals("DELETED") || normAction.equals("PERMANENTLY DELETED")) deletedCount++;
            else if (normAction.equals("UPDATED")) updatedCount++;
            
            if ("Denied".equalsIgnoreCase(newStatus) || (isStatusUpdate && "Denied".equalsIgnoreCase(newStatus))) {
                deniedCount++;
            }
            if ("Completed".equalsIgnoreCase(newStatus) || (isStatusUpdate && "Completed".equalsIgnoreCase(newStatus))) {
                completedCount++;
            }
        }

        if (tabAll != null) tabAll.setText("All (" + allHistoryList.size() + ")");
        if (tabSubmitted != null) tabSubmitted.setText("Submitted (" + submittedCount + ")");
        if (tabArchived != null) tabArchived.setText("Archived (" + archivedCount + ")");
        if (tabDenied != null) tabDenied.setText("Denied (" + deniedCount + ")");
        if (tabRestored != null) tabRestored.setText("Restored (" + restoredCount + ")");
        if (tabTrashed != null) tabTrashed.setText("Trashed (" + trashedCount + ")");
        if (tabDeleted != null) tabDeleted.setText("Deleted (" + deletedCount + ")");
        if (tabCompleted != null) tabCompleted.setText("Completed (" + completedCount + ")");
        if (tabUpdated != null) tabUpdated.setText("Updated (" + updatedCount + ")");
        if (tabTrash != null) tabTrash.setText("Logs Trash (" + trashCount + ")");
    }

    private void loadTrashData() {
        if (sessionManager == null) return;
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;
        repository.getTrashHistoryByResidentId(residentId).observe(getViewLifecycleOwner(), trashList -> {
            lastFetchedTrashList = trashList != null ? trashList : new ArrayList<>();
            trashCount = lastFetchedTrashList.size();
            updateHeaderTitle();
            updateTabCounts();
            if (isTrashView) {
                applyFilter();
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void loadHistoryData() {
        if (sessionManager == null) return;
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;

        repository.getHistoryByResidentId(residentId).observe(getViewLifecycleOwner(), historyList -> {
            allHistoryList = historyList != null ? historyList : new ArrayList<>();
            historyCount = allHistoryList.size();
            updateHeaderTitle();
            updateTabCounts();
            if (!isTrashView) {
                applyFilter();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        
        loadTrashData();
    }
}
