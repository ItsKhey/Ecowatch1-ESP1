package com.example.myecowatch1.ui.reports;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class ReportsFragment extends Fragment {
    private static final String ARG_OPEN_REPORT_ID = "open_report_id";

    private ReportsViewModel viewModel;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView reportsRecyclerView;
    private ReportAdapter adapter;
    
    private TextView tabAll, tabPending, tabInProgress, tabCompleted, tabDenied, tabArchived, tabTrashed;
    private EditText searchEditText;
    private ImageView filterBtn, selectModeBtn;
    private LinearLayout selectionControlLayout, bulkActionsBar;
    private CheckBox selectAllCheckbox;
    private TextView selectionCountText, noReportsText, filterResultsCountText;
    private MaterialButton bulkArchiveBtn, bulkRestoreBtn, bulkDeleteBtn;
    private HorizontalScrollView activeFiltersScrollView;
    private ChipGroup activeFiltersChipGroup;

    private List<Report> allCurrentReports = new ArrayList<>();
    private String currentView = "All";
    private String selectedStatusFilter = "All";
    private String currentCategoryFilter = "All";
    private int currentDateFilterId = -1;
    private int currentSortId = -1;
    private Calendar customFromDate, customToDate;
    private String pendingNotificationReportId;
    private boolean pendingReportShown;

    public static ReportsFragment forReportId(String reportId) {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_OPEN_REPORT_ID, reportId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report, container, false);

        if (getArguments() != null) {
            pendingNotificationReportId = getArguments().getString(ARG_OPEN_REPORT_ID);
        }
        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);

        initViews(view);
        setupRecyclerView();
        setupTabs();
        setupSearch();
        setupBulkActions();

        observeViewModel();

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.green, null));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshReports();
            Toast.makeText(getContext(), "Syncing...", Toast.LENGTH_SHORT).show();
            // Force hide after 2 seconds if no data change triggered it
            swipeRefreshLayout.postDelayed(() -> {
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 2000);
        });

        viewModel.refreshReports();
        viewModel.cleanupTrash(); // Auto-cleanup on load

        return view;
    }

    private void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        reportsRecyclerView = view.findViewById(R.id.reportsRecyclerView);
        
        tabAll = view.findViewById(R.id.tabAll);
        tabPending = view.findViewById(R.id.tabPending);
        tabInProgress = view.findViewById(R.id.tabInProgress);
        tabCompleted = view.findViewById(R.id.tabCompleted);
        tabDenied = view.findViewById(R.id.tabDenied);
        tabArchived = view.findViewById(R.id.tabArchived);
        tabTrashed = view.findViewById(R.id.tabTrashed);
        
        searchEditText = view.findViewById(R.id.searchEditText);
        filterBtn = view.findViewById(R.id.filterBtn);
        selectModeBtn = view.findViewById(R.id.selectModeBtn);
        selectionControlLayout = view.findViewById(R.id.selectionControlLayout);
        bulkActionsBar = view.findViewById(R.id.bulkActionsBar);
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox);
        selectionCountText = view.findViewById(R.id.selectionCountText);
        noReportsText = view.findViewById(R.id.noReportsText);
        filterResultsCountText = view.findViewById(R.id.filterResultsCountText);
        activeFiltersScrollView = view.findViewById(R.id.activeFiltersScrollView);
        activeFiltersChipGroup = view.findViewById(R.id.activeFiltersChipGroup);
        bulkArchiveBtn = view.findViewById(R.id.bulkArchiveBtn);
        bulkRestoreBtn = view.findViewById(R.id.bulkRestoreBtn);
        bulkDeleteBtn = view.findViewById(R.id.bulkDeleteBtn);

        selectModeBtn.setOnClickListener(v -> toggleSelectionMode());
    }

    private void toggleSelectionMode() {
        boolean isNow = !selectionControlLayout.isShown();
        adapter.setSelectionMode(isNow);
        selectionControlLayout.setVisibility(isNow ? View.VISIBLE : View.GONE);
        if (!isNow) {
            updateSelectionUI(0);
        } else {
            selectAllCheckbox.setChecked(false);
            selectionCountText.setText("0 selected");
        }
    }

    private void setupRecyclerView() {
        adapter = new ReportAdapter();
        adapter.setCurrentView("Reports");
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        reportsRecyclerView.setAdapter(adapter);

        adapter.setOnReportClickListener(new ReportAdapter.OnReportClickListener() {
            @Override
            public void onReportClick(Report report) {
                showReportDetail(report);
            }

            @Override
            public void onLongReportClick(Report report) {
                toggleSelectionMode();
            }

            @Override
            public void onDeleteClick(Report report) {
                boolean isTrash = "Trashed".equalsIgnoreCase(currentView);
                String title = isTrash ? "Permanent Delete" : "Delete Report";
                String msg = isTrash ? "Delete this report permanently? This action cannot be undone." : "Move this report to Trash?";
                
                new AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setMessage(msg)
                        .setPositiveButton("Confirm", (d, w) -> {
                            if (isTrash) {
                                viewModel.deleteReport(report);
                                Toast.makeText(getContext(), "Report permanently removed", Toast.LENGTH_SHORT).show();
                            } else {
                                viewModel.softDeleteReport(report);
                                Toast.makeText(getContext(), "Moved to Trash", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onEditClick(Report report) {
                CreateReportFragment editFragment = new CreateReportFragment();
                Bundle args = new Bundle();
                args.putSerializable("report", report);
                editFragment.setArguments(args);

                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, editFragment)
                            .addToBackStack(null)
                            .commit();
                }
            }

            @Override
            public void onRestoreClick(Report report) {
                viewModel.restoreReport(report);
                Toast.makeText(getContext(), "Report restored", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionUI(count);
            }
        });
    }

    private void setupTabs() {
        tabAll.setOnClickListener(v -> selectTab("All"));
        tabPending.setOnClickListener(v -> selectTab("Pending"));
        tabInProgress.setOnClickListener(v -> selectTab("In Progress"));
        tabCompleted.setOnClickListener(v -> selectTab("Completed"));
        tabDenied.setOnClickListener(v -> selectTab("Denied"));
        tabArchived.setOnClickListener(v -> selectTab("Archived"));
        tabTrashed.setOnClickListener(v -> selectTab("Trashed"));
    }

    private void selectTab(String tabName) {
        currentView = tabName;
        
        // Update UI states
        updateTabUI(tabAll, "All".equals(tabName));
        updateTabUI(tabPending, "Pending".equals(tabName));
        updateTabUI(tabInProgress, "In Progress".equals(tabName));
        updateTabUI(tabCompleted, "Completed".equals(tabName));
        updateTabUI(tabDenied, "Denied".equals(tabName));
        updateTabUI(tabArchived, "Archived".equals(tabName));
        updateTabUI(tabTrashed, "Trashed".equals(tabName));
        
        adapter.setCurrentView(currentView);
        adapter.setSelectionMode(false);
        updateSelectionUI(0);
        loadViewData();
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

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterReports(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        filterBtn.setOnClickListener(v -> showFilterDialog());
    }

    private void setupBulkActions() {
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) adapter.selectAll();
            else adapter.unselectAll();
        });

        bulkArchiveBtn.setOnClickListener(v -> {
            viewModel.bulkArchive(adapter.getSelectedIds());
            exitSelectionMode();
        });

        bulkRestoreBtn.setOnClickListener(v -> {
            viewModel.bulkRestore(adapter.getSelectedIds());
            exitSelectionMode();
        });

        bulkDeleteBtn.setOnClickListener(v -> {
            boolean isTrash = "Trashed".equalsIgnoreCase(currentView);
            String title = isTrash ? "Bulk Permanent Delete" : "Bulk Delete";
            String msg = isTrash ? "Permanently delete selected reports? This cannot be undone." : "Move selected reports to Trash?";
            
            new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton("Confirm", (d, w) -> {
                        if (isTrash) {
                            viewModel.bulkPermanentDelete(adapter.getSelectedIds());
                        } else {
                            viewModel.bulkSoftDelete(adapter.getSelectedIds());
                        }
                        exitSelectionMode();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        updateSelectionUI(0);
        selectAllCheckbox.setChecked(false);
        selectionControlLayout.setVisibility(View.GONE);
    }

    private void updateSelectionUI(int count) {
        selectionCountText.setText(count + " selected");
        if (count > 0) {
            bulkActionsBar.setVisibility(View.VISIBLE);
            
            if ("Trashed".equalsIgnoreCase(currentView)) {
                bulkArchiveBtn.setVisibility(View.GONE);
                bulkRestoreBtn.setVisibility(View.VISIBLE);
                bulkDeleteBtn.setVisibility(View.VISIBLE); // Permanent delete
            } else if ("Archived".equalsIgnoreCase(currentView)) {
                bulkArchiveBtn.setVisibility(View.GONE);
                bulkRestoreBtn.setVisibility(View.VISIBLE);
                bulkDeleteBtn.setVisibility(View.VISIBLE);
            } else {
                bulkArchiveBtn.setVisibility(View.VISIBLE);
                bulkRestoreBtn.setVisibility(View.GONE);
                bulkDeleteBtn.setVisibility(View.VISIBLE);
            }
        } else {
            bulkActionsBar.setVisibility(View.GONE);
        }
    }

    private void observeViewModel() {
        viewModel.getAllReports().observe(getViewLifecycleOwner(), reports -> {
            updateTabCounts(reports);
            loadViewData();
            showPendingReportIfReady();
        });
    }

    private void updateTabCounts(List<Report> reports) {
        if (reports == null) return;

        long pending = reports.stream().filter(r -> !r.isArchived() && !r.isDeleted() && "Pending".equalsIgnoreCase(r.getStatus())).count();
        long inProgress = reports.stream().filter(r -> !r.isArchived() && !r.isDeleted() && "In Progress".equalsIgnoreCase(r.getStatus())).count();
        long archived = reports.stream().filter(r -> r.isArchived() && !r.isDeleted()).count();
        long trashed = reports.stream().filter(Report::isDeleted).count();
        long denied = reports.stream().filter(r -> !r.isDeleted() && "Denied".equalsIgnoreCase(r.getStatus())).count();
        long completed = reports.stream().filter(r -> !r.isDeleted() && "Completed".equalsIgnoreCase(r.getStatus())).count();

        // Exclude trashed reports completely from the "All" total sum counter
        long nonDeletedCount = reports.stream().filter(r -> !r.isDeleted()).count();

        tabAll.setText("All (" + nonDeletedCount + ")");
        tabPending.setText("Pending (" + pending + ")");
        tabInProgress.setText("In Progress (" + inProgress + ")");
        tabArchived.setText("Archived (" + archived + ")");
        tabTrashed.setText("Trashed (" + trashed + ")");
        tabDenied.setText("Denied (" + denied + ")");
        tabCompleted.setText("Completed (" + completed + ")");
    }

    private void showPendingReportIfReady() {
        if (pendingReportShown
                || pendingNotificationReportId == null
                || pendingNotificationReportId.trim().isEmpty()) {
            return;
        }
        for (Report report : allCurrentReports) {
            String id = report.getReportId() == null
                    ? report.getMongoId() : report.getReportId();
            if (id != null && id.equalsIgnoreCase(pendingNotificationReportId)) {
                pendingReportShown = true;
                pendingNotificationReportId = null;
                requireView().post(() -> {
                    if (isAdded()) showReportDetail(report);
                });
                return;
            }
        }
    }

    private void loadViewData() {
        handleDataUpdate(viewModel.getAllReports().getValue());
    }

    private void handleDataUpdate(List<Report> reports) {
        // The Room query is already keyed on the resident and the table is
        // keyed on the local primary key, so the list cannot contain a second
        // copy of the same report. De-duplicating by server id here is a
        // belt-and-braces guard for the one case that can still collide: a
        // legacy row that kept a provisional id after the server confirmed a
        // different one. The surviving copy is the most recently updated.
        allCurrentReports = dedupeByServerId(reports != null ? reports : new ArrayList<>());
        filterReports(searchEditText.getText().toString());
    }

    private List<Report> dedupeByServerId(List<Report> reports) {
        Map<String, Report> latestByReportId = new LinkedHashMap<>();

        for (Report report : reports) {
            if (report == null) continue;
            String key = report.getReportId();
            if (key == null || key.trim().isEmpty()) {
                key = report.getStoredMongoId();
            }
            if (key == null || key.trim().isEmpty()) {
                // No usable identity: keep the row as-is rather than dropping it.
                latestByReportId.put("local-" + report.getId() + "-"
                        + latestByReportId.size(), report);
                continue;
            }

            String normalizedKey = key.trim();
            Report existing = latestByReportId.get(normalizedKey);
            if (existing == null || isNewerThan(report, existing)) {
                latestByReportId.put(normalizedKey, report);
            }
        }

        return new ArrayList<>(latestByReportId.values());
    }

    private boolean isNewerThan(Report candidate, Report existing) {
        Date candidateUpdated = parseReportDate(
                candidate.getUpdatedAt() != null ? candidate.getUpdatedAt() : candidate.getCreatedAt());
        Date existingUpdated = parseReportDate(
                existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt());
        if (candidateUpdated == null || existingUpdated == null) return false;
        return candidateUpdated.after(existingUpdated);
    }



    private Date parseReportDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "Just now".equalsIgnoreCase(dateStr.trim())) {
            return new Date(); // Treat null or "Just now" as current time
        }
        String s = dateStr.trim();

        // ISO 8601 with ms and Z
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(s);
        } catch (Exception ignored) {}

        // ISO 8601 without ms Z
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(s);
        } catch (Exception ignored) {}

        // ISO with timezone offset
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            return sdf.parse(s);
        } catch (Exception ignored) {}

        // Standard SQL yyyy-MM-dd HH:mm:ss
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(s);
        } catch (Exception ignored) {}

        // Simple yyyy-MM-dd
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return sdf.parse(s);
        } catch (Exception ignored) {}

        // Numeric epoch timestamp
        try {
            long epoch = Long.parseLong(s);
            return new Date(epoch);
        } catch (Exception ignored) {}

        return new Date(); // Fallback to current time
    }

    private void filterReports(String query) {
        Calendar calStart = Calendar.getInstance();
        Calendar calEnd = Calendar.getInstance();
        Date rangeStart = null;
        Date rangeEnd = null;

        if (currentDateFilterId == R.id.radioToday) {
            calStart.set(Calendar.HOUR_OF_DAY, 0);
            calStart.set(Calendar.MINUTE, 0);
            calStart.set(Calendar.SECOND, 0);
            calStart.set(Calendar.MILLISECOND, 0);
            rangeStart = calStart.getTime();

            calEnd.set(Calendar.HOUR_OF_DAY, 23);
            calEnd.set(Calendar.MINUTE, 59);
            calEnd.set(Calendar.SECOND, 59);
            calEnd.set(Calendar.MILLISECOND, 999);
            rangeEnd = calEnd.getTime();
        } else if (currentDateFilterId == R.id.radioLast7) {
            calStart.add(Calendar.DAY_OF_YEAR, -7);
            rangeStart = calStart.getTime();
        } else if (currentDateFilterId == R.id.radioLast30) {
            calStart.add(Calendar.DAY_OF_YEAR, -30);
            rangeStart = calStart.getTime();
        } else if (currentDateFilterId == R.id.radioLast90) {
            calStart.add(Calendar.DAY_OF_YEAR, -90);
            rangeStart = calStart.getTime();
        } else if (currentDateFilterId == R.id.radioLast180) {
            calStart.add(Calendar.DAY_OF_YEAR, -180);
            rangeStart = calStart.getTime();
        } else if (currentDateFilterId == R.id.radioCustom) {
            if (customFromDate != null) rangeStart = customFromDate.getTime();
            if (customToDate != null) rangeEnd = customToDate.getTime();
        }

        final Date finalRangeStart = rangeStart;
        final Date finalRangeEnd = rangeEnd;

        List<Report> filtered = allCurrentReports.stream()
                .filter(r -> {
                    // Search Filter (checks title, reportId, wasteType, description, status)
                    if (query == null || query.trim().isEmpty()) return true;
                    String q = query.trim().toLowerCase();
                    return (r.getTitle() != null && r.getTitle().toLowerCase().contains(q)) || 
                           (r.getReportId() != null && r.getReportId().toLowerCase().contains(q)) ||
                           (r.getWasteType() != null && r.getWasteType().toLowerCase().contains(q)) ||
                           (r.getDescription() != null && r.getDescription().toLowerCase().contains(q)) ||
                           (r.getStatus() != null && r.getStatus().toLowerCase().contains(q));
                })
                .filter(r -> {
                    // Current Tab View Filter
                    if ("All".equalsIgnoreCase(currentView)) return !r.isDeleted();
                    if ("Pending".equalsIgnoreCase(currentView)) return !r.isArchived() && !r.isDeleted() && "Pending".equalsIgnoreCase(r.getStatus());
                    if ("In Progress".equalsIgnoreCase(currentView)) return !r.isArchived() && !r.isDeleted() && "In Progress".equalsIgnoreCase(r.getStatus());
                    if ("Archived".equalsIgnoreCase(currentView)) return r.isArchived() && !r.isDeleted();
                    if ("Trashed".equalsIgnoreCase(currentView)) return r.isDeleted();
                    if ("Denied".equalsIgnoreCase(currentView)) return "Denied".equalsIgnoreCase(r.getStatus()) && !r.isDeleted();
                    if ("Completed".equalsIgnoreCase(currentView)) return "Completed".equalsIgnoreCase(r.getStatus()) && !r.isDeleted();
                    return true;
                })
                .filter(r -> {
                    // Status Filter
                    if ("All".equalsIgnoreCase(selectedStatusFilter)) return true;
                    return selectedStatusFilter.equalsIgnoreCase(r.getStatus());
                })
                .filter(r -> {
                    // Category Filter
                    if ("All".equalsIgnoreCase(currentCategoryFilter)) return true;
                    return currentCategoryFilter.equalsIgnoreCase(r.getWasteType());
                })
                .filter(report -> {
                    // Date Filter
                    if (currentDateFilterId == -1) return true;
                    
                    Date reportDate = parseReportDate(report.getCreatedAt());
                    if (reportDate == null) return false;

                    if (currentDateFilterId == R.id.radioToday) {
                        return !reportDate.before(finalRangeStart) && !reportDate.after(finalRangeEnd);
                    } else if (currentDateFilterId == R.id.radioCustom) {
                        if (finalRangeStart == null && finalRangeEnd == null) return true;
                        if (finalRangeStart != null && reportDate.before(finalRangeStart)) return false;
                        if (finalRangeEnd != null && reportDate.after(finalRangeEnd)) return false;
                        return true;
                    } else {
                        return finalRangeStart != null && !reportDate.before(finalRangeStart);
                    }
                })
                .collect(Collectors.toList());

        // Enhanced Sorting Logic
        Collections.sort(filtered, (r1, r2) -> {
            try {
                if (currentSortId == R.id.radioTitleAZ) {
                    String t1 = r1.getTitle() != null ? r1.getTitle() : "";
                    String t2 = r2.getTitle() != null ? r2.getTitle() : "";
                    return t1.compareToIgnoreCase(t2);
                } else if (currentSortId == R.id.radioTitleZA) {
                    String t1 = r1.getTitle() != null ? r1.getTitle() : "";
                    String t2 = r2.getTitle() != null ? r2.getTitle() : "";
                    return t2.compareToIgnoreCase(t1);
                } else if (currentSortId == R.id.radioOldest) {
                    Date d1 = parseReportDate(r1.getCreatedAt());
                    Date d2 = parseReportDate(r2.getCreatedAt());
                    return d1.compareTo(d2); // Oldest first
                } else if (currentSortId == R.id.radioUpdated) {
                    Date u1 = parseReportDate(r1.getUpdatedAt() != null ? r1.getUpdatedAt() : r1.getCreatedAt());
                    Date u2 = parseReportDate(r2.getUpdatedAt() != null ? r2.getUpdatedAt() : r2.getCreatedAt());
                    return u2.compareTo(u1); // Recently updated
                } else {
                    // Default: Newest First (currentSortId == R.id.radioNewest or -1)
                    Date d1 = parseReportDate(r1.getCreatedAt());
                    Date d2 = parseReportDate(r2.getCreatedAt());
                    return d2.compareTo(d1); // Newest first
                }
            } catch (Exception e) {
                return 0;
            }
        });
        
        adapter.setReports(filtered);
        noReportsText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        // Update active filter chip badges & summary count
        updateActiveFilterChips(query, filtered.size(), allCurrentReports.size());
    }

    private void updateActiveFilterChips(String query, int filteredCount, int totalCount) {
        if (activeFiltersChipGroup == null || activeFiltersScrollView == null) return;

        activeFiltersChipGroup.removeAllViews();
        boolean hasActiveFilter = false;

        // Search Chip
        if (query != null && !query.trim().isEmpty()) {
            addFilterChip("Search: \"" + query.trim() + "\"", () -> {
                searchEditText.setText("");
                filterReports("");
            });
            hasActiveFilter = true;
        }

        // Category Chip
        if (!"All".equalsIgnoreCase(currentCategoryFilter)) {
            addFilterChip("Category: " + currentCategoryFilter, () -> {
                currentCategoryFilter = "All";
                filterReports(searchEditText.getText().toString());
            });
            hasActiveFilter = true;
        }

        // Status Chip
        if (!"All".equalsIgnoreCase(selectedStatusFilter)) {
            addFilterChip("Status: " + selectedStatusFilter, () -> {
                selectedStatusFilter = "All";
                filterReports(searchEditText.getText().toString());
            });
            hasActiveFilter = true;
        }

        // Date Filter Chip
        if (currentDateFilterId != -1) {
            String dateLabel = "Date Filter";
            if (currentDateFilterId == R.id.radioToday) dateLabel = "Date: Today";
            else if (currentDateFilterId == R.id.radioLast7) dateLabel = "Date: Last 7 days";
            else if (currentDateFilterId == R.id.radioLast30) dateLabel = "Date: Last 30 days";
            else if (currentDateFilterId == R.id.radioLast90) dateLabel = "Date: Last 90 days";
            else if (currentDateFilterId == R.id.radioLast180) dateLabel = "Date: Last 180 days";
            else if (currentDateFilterId == R.id.radioCustom) dateLabel = "Date: Custom Range";

            addFilterChip(dateLabel, () -> {
                currentDateFilterId = -1;
                customFromDate = null;
                customToDate = null;
                filterReports(searchEditText.getText().toString());
            });
            hasActiveFilter = true;
        }

        // Sort Chip
        if (currentSortId != -1 && currentSortId != R.id.radioNewest) {
            String sortLabel = "Sort: Newest";
            if (currentSortId == R.id.radioOldest) sortLabel = "Sort: Oldest First";
            else if (currentSortId == R.id.radioUpdated) sortLabel = "Sort: Recently Updated";
            else if (currentSortId == R.id.radioTitleAZ) sortLabel = "Sort: Title (A-Z)";
            else if (currentSortId == R.id.radioTitleZA) sortLabel = "Sort: Title (Z-A)";

            addFilterChip(sortLabel, () -> {
                currentSortId = -1;
                filterReports(searchEditText.getText().toString());
            });
            hasActiveFilter = true;
        }

        // Clear All Chip
        if (hasActiveFilter) {
            addFilterChip("Clear All ✕", () -> {
                currentCategoryFilter = "All";
                selectedStatusFilter = "All";
                currentDateFilterId = -1;
                currentSortId = -1;
                customFromDate = null;
                customToDate = null;
                searchEditText.setText("");
                filterReports("");
            });
            activeFiltersScrollView.setVisibility(View.VISIBLE);
        } else {
            activeFiltersScrollView.setVisibility(View.GONE);
        }

        // Filter Results Summary Text
        if (filterResultsCountText != null) {
            if (hasActiveFilter) {
                filterResultsCountText.setText("Showing " + filteredCount + " of " + totalCount + " reports");
                filterResultsCountText.setVisibility(View.VISIBLE);
            } else {
                filterResultsCountText.setVisibility(View.GONE);
            }
        }
    }

    private void addFilterChip(String label, Runnable onClose) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCloseIconVisible(!label.startsWith("Clear All"));
        chip.setChipBackgroundColorResource(R.color.chip_background);
        chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_and_icon));
        chip.setCloseIconTint(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_and_icon));
        chip.setChipCornerRadius(24f);
        chip.setOnCloseIconClickListener(v -> onClose.run());
        chip.setOnClickListener(v -> onClose.run());
        activeFiltersChipGroup.addView(chip);
    }

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_main, null);
        MaterialButton btnCategory = dialogView.findViewById(R.id.btnFilterCategory);
        MaterialButton btnStatus = dialogView.findViewById(R.id.btnFilterStatus);
        MaterialButton btnDate = dialogView.findViewById(R.id.btnFilterDate);
        View btnClose = dialogView.findViewById(R.id.btnCloseFilter);
        View btnResetAll = dialogView.findViewById(R.id.btnResetCategory);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnResetAll.setOnClickListener(v -> {
            selectedStatusFilter = "All";
            currentCategoryFilter = "All";
            currentDateFilterId = -1;
            currentSortId = -1;
            customFromDate = null;
            customToDate = null;
            
            searchEditText.setText("");
            filterReports("");
            dialog.dismiss();
            Toast.makeText(getContext(), "All filters cleared", Toast.LENGTH_SHORT).show();
        });

        btnCategory.setOnClickListener(v -> {
            dialog.dismiss();
            showCategoryFilter();
        });

        btnStatus.setOnClickListener(v -> {
            dialog.dismiss();
            showStatusFilter();
        });

        btnDate.setOnClickListener(v -> {
            dialog.dismiss();
            showDateFilter();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showCategoryFilter() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category_filter, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.categoryRadioGroup);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyCategoryFilter);
        View btnBack = dialogView.findViewById(R.id.btnBackCategory);
        View btnReset = dialogView.findViewById(R.id.btnResetCategory);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Pre-check currently active category
        if ("Biodegradable".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioBiodegradable);
        else if ("Recyclable".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioRecyclable);
        else if ("Residual".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioResidual);
        else if ("Hazardous".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioHazardous);
        else if ("Electronic Waste (E-Waste)".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioEWaste);
        else if ("General Waste".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioGeneral);
        else radioGroup.check(R.id.radioAll);

        btnApply.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(getContext(), "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }

            String category = "All";
            if (checkedId == R.id.radioBiodegradable) category = "Biodegradable";
            else if (checkedId == R.id.radioRecyclable) category = "Recyclable";
            else if (checkedId == R.id.radioResidual) category = "Residual";
            else if (checkedId == R.id.radioHazardous) category = "Hazardous";
            else if (checkedId == R.id.radioEWaste) category = "Electronic Waste (E-Waste)";
            else if (checkedId == R.id.radioGeneral) category = "General Waste";

            currentCategoryFilter = category;
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Category filter applied: " + currentCategoryFilter, Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            showFilterDialog();
        });

        btnReset.setOnClickListener(v -> {
            currentCategoryFilter = "All";
            radioGroup.check(R.id.radioAll);
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Category filter reset", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showStatusFilter() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_status_filter, null);
        RadioGroup statusRadioGroup = dialogView.findViewById(R.id.statusRadioGroup);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyStatusFilter);
        View btnBack = dialogView.findViewById(R.id.btnBackStatus);
        View btnReset = dialogView.findViewById(R.id.btnResetStatus);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if ("Pending".equalsIgnoreCase(selectedStatusFilter)) statusRadioGroup.check(R.id.radioStatusPending);
        else if ("Completed".equalsIgnoreCase(selectedStatusFilter)) statusRadioGroup.check(R.id.radioStatusCompleted);
        else if ("Denied".equalsIgnoreCase(selectedStatusFilter)) statusRadioGroup.check(R.id.radioStatusDenied);
        else statusRadioGroup.check(R.id.radioStatusAll);

        btnApply.setOnClickListener(v -> {
            int checkedId = statusRadioGroup.getCheckedRadioButtonId();
            if (checkedId == R.id.radioStatusPending) selectedStatusFilter = "Pending";
            else if (checkedId == R.id.radioStatusCompleted) selectedStatusFilter = "Completed";
            else if (checkedId == R.id.radioStatusDenied) selectedStatusFilter = "Denied";
            else selectedStatusFilter = "All";

            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Status filter applied: " + selectedStatusFilter, Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            showFilterDialog();
        });

        btnReset.setOnClickListener(v -> {
            selectedStatusFilter = "All";
            statusRadioGroup.check(R.id.radioStatusAll);
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Status filter reset", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showDateFilter() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_date_filter, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.dateRadioGroup);
        RadioGroup sortRadioGroup = dialogView.findViewById(R.id.sortRadioGroup);
        LinearLayout customDateLayout = dialogView.findViewById(R.id.customDateLayout);
        TextView tvFromDate = dialogView.findViewById(R.id.tvFromDate);
        TextView tvToDate = dialogView.findViewById(R.id.tvToDate);
        View btnFromDate = dialogView.findViewById(R.id.btnFromDate);
        View btnToDate = dialogView.findViewById(R.id.btnToDate);
        View btnBack = dialogView.findViewById(R.id.btnBackDate);
        View btnReset = dialogView.findViewById(R.id.btnResetDate);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyFilter);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> 
            customDateLayout.setVisibility(checkedId == R.id.radioCustom ? View.VISIBLE : View.GONE));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        if (currentDateFilterId != -1) {
            radioGroup.check(currentDateFilterId);
            if (currentDateFilterId == R.id.radioCustom) {
                customDateLayout.setVisibility(View.VISIBLE);
                if (customFromDate != null) tvFromDate.setText(sdf.format(customFromDate.getTime()));
                if (customToDate != null) tvToDate.setText(sdf.format(customToDate.getTime()));
            }
        }

        if (currentSortId != -1) {
            sortRadioGroup.check(currentSortId);
        } else {
            sortRadioGroup.check(R.id.radioNewest);
        }

        btnFromDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                customFromDate = Calendar.getInstance();
                customFromDate.set(year, month, dayOfMonth, 0, 0, 0);
                tvFromDate.setText(sdf.format(customFromDate.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnToDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                customToDate = Calendar.getInstance();
                customToDate.set(year, month, dayOfMonth, 23, 59, 59);
                tvToDate.setText(sdf.format(customToDate.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnApply.setOnClickListener(v -> {
            currentDateFilterId = radioGroup.getCheckedRadioButtonId();
            currentSortId = sortRadioGroup.getCheckedRadioButtonId();
            
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            showFilterDialog();
        });

        btnReset.setOnClickListener(v -> {
            radioGroup.clearCheck();
            currentDateFilterId = -1;
            customFromDate = null;
            customToDate = null;
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
        });

        dialog.show();
    }



    private void showReportDetail(Report report) {
        View detailView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_report_detail, null);
        
        TextView detailReportId = detailView.findViewById(R.id.detailReportId);
        TextView detailTitle = detailView.findViewById(R.id.detailTitle);
        TextView detailStatus = detailView.findViewById(R.id.detailStatus);
        TextView detailDate = detailView.findViewById(R.id.detailDate);
        TextView detailTime = detailView.findViewById(R.id.detailTime);
        TextView detailCategory = detailView.findViewById(R.id.detailCategory);
        TextView detailDescription = detailView.findViewById(R.id.detailDescription);
        TextView detailLocation = detailView.findViewById(R.id.detailLocation);
        View closeBtn = detailView.findViewById(R.id.closeDetailBtn);

        // Each group of related fields sits in its own outlined card, so the
        // cards are toggled alongside the rows they contain. This keeps an
        // empty card from being left behind when a whole group is absent.
        View photoCard = detailView.findViewById(R.id.photoCard);
        View adminResponseCard = detailView.findViewById(R.id.adminResponseCard);

        String reportId = report.getReportId();
        if (reportId == null || reportId.isEmpty()) {
            reportId = report.getMongoId();
        }
        if (reportId == null || reportId.isEmpty()) {
            reportId = report.getId() > 0 ? "LOCAL-" + report.getId() : "N/A";
        }
        detailReportId.setText(reportId);

        detailTitle.setText(report.getTitle() != null ? report.getTitle() : "Untitled");
        detailStatus.setText(report.getStatus());

        // Admin Response
        TextView responseLabel = detailView.findViewById(R.id.adminResponseLabel);
        TextView responseText = detailView.findViewById(R.id.detailAdminResponse);
        if (report.getAdminResponse() != null && !report.getAdminResponse().isEmpty()) {
            responseText.setText(report.getAdminResponse());
            responseLabel.setVisibility(View.VISIBLE);
            responseText.setVisibility(View.VISIBLE);
            adminResponseCard.setVisibility(View.VISIBLE);
        } else {
            responseLabel.setVisibility(View.GONE);
            responseText.setVisibility(View.GONE);
            adminResponseCard.setVisibility(View.GONE);
        }

        bindAssignment(detailView, report);
        bindPersonnelResponse(detailView, report);


        detailCategory.setText(report.getWasteType());
        detailDescription.setText(report.getDescription());
        detailLocation.setText(String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", report.getLatitude(), report.getLongitude()));

        // Format Date and Time
        if (report.getCreatedAt() != null) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = inputFormat.parse(report.getCreatedAt());
                if (date != null) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
                    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                    TimeZone pht = TimeZone.getTimeZone("Asia/Manila");
                    dateFormat.setTimeZone(pht);
                    timeFormat.setTimeZone(pht);
                    detailDate.setText(dateFormat.format(date));
                    detailTime.setText(timeFormat.format(date));
                }
            } catch (Exception e) {
                detailDate.setText(report.getCreatedAt());
                detailTime.setText("N/A");
            }
        } else {
            detailDate.setText("Just now");
            detailTime.setText("Just now");
        }

        bindDetailPhotos(detailView, report, photoCard);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(detailView)
                .create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Shows the cleanup assignment, but only once there is one.
     *
     * <p>An unassigned report keeps the whole card hidden, so the dialog never
     * shows an empty "Cleanup Assignment" heading above a blank space.
     *
     * <p>The cleaner's identity is deliberately not rendered. The server sends
     * residents personnel identifiers only and withholds names, so the resident
     * is told a crew is on the job and when they took it, which is the part they
     * can act on.
     */
    private void bindAssignment(View detailView, Report report) {
        View assignmentCard = detailView.findViewById(R.id.assignmentCard);
        if (!report.isAssigned()) {
            assignmentCard.setVisibility(View.GONE);
            return;
        }

        TextView label = detailView.findViewById(R.id.assignmentLabel);
        TextView statusText = detailView.findViewById(R.id.detailAssignmentStatus);
        TextView assignedAtText = detailView.findViewById(R.id.detailAssignmentAt);
        TextView completedText = detailView.findViewById(R.id.detailAssignmentCompleted);

        statusText.setText(report.getResolvedAt() == null
                || report.getResolvedAt().trim().isEmpty()
                ? getString(R.string.report_detail_assignment_pending)
                : getString(R.string.report_detail_assignment_done));

        bindOptionalTimestamp(assignedAtText,
                report.getAssignedAt(), R.string.report_detail_assigned_on);

        // Completion is a separate line rather than a replacement for the
        // assignment: when the work was both taken and finished, both dates are
        // worth showing.
        bindOptionalTimestamp(completedText,
                report.getResolvedAt(), R.string.report_detail_completed_on);

        label.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        assignmentCard.setVisibility(View.VISIBLE);
    }

    /**
     * Writes a "on <date>" line, or hides the view when there is no timestamp.
     *
     * <p>An unreadable timestamp is left to the raw server text rather than
     * dropped, so a formatting gap never silently erases real information.
     */
    private void bindOptionalTimestamp(TextView view, String rawTimestamp, int labelRes) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(getString(labelRes, formatDetailTimestamp(rawTimestamp)));
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Renders a server timestamp as a readable local date and time.
     *
     * <p>Shares the same Manila timezone and formats as the submission date, so
     * every date in the dialog reads the same way.
     */
    private String formatDetailTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "";
        String value = rawTimestamp.trim();
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(value);
            if (date != null) return formatDetailDateTime(date);
        } catch (Exception ignored) {
            // Falls through to the looser parsers below.
        }
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(value);
            if (date != null) return formatDetailDateTime(date);
        } catch (Exception ignored) {
            // Falls through to the raw value.
        }
        return value;
    }

    private String formatDetailDateTime(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        TimeZone pht = TimeZone.getTimeZone("Asia/Manila");
        dateFormat.setTimeZone(pht);
        timeFormat.setTimeZone(pht);
        return dateFormat.format(date) + " at " + timeFormat.format(date);
    }

    /**
     * Shows the cleaner's own completion note, but only once there is one.
     *
     * <p>Kept apart from the administrator's answer because the two are
     * different voices and arrive at different times: the admin answers the
     * resident, the cleaner reports what they actually did.
     */
    private void bindPersonnelResponse(View detailView, Report report) {
        View personnelCard = detailView.findViewById(R.id.personnelResponseCard);
        TextView personnelLabel = detailView.findViewById(R.id.personnelResponseLabel);
        TextView personnelText = detailView.findViewById(R.id.detailPersonnelResponse);

        String personnelResponse = report.getPersonnelResponse();
        if (personnelResponse == null || personnelResponse.trim().isEmpty()) {
            personnelLabel.setVisibility(View.GONE);
            personnelText.setVisibility(View.GONE);
            personnelCard.setVisibility(View.GONE);
            return;
        }

        personnelText.setText(personnelResponse.trim());
        personnelLabel.setVisibility(View.VISIBLE);
        personnelText.setVisibility(View.VISIBLE);
        personnelCard.setVisibility(View.VISIBLE);
    }

    /**
     * Fills the dialog's photo strip with every image the report carries.
     *
     * <p>A local copy wins over the remote URL for the cover image, because a
     * report that has not finished uploading only exists on the device. The
     * remaining photos are the server list, resolved to absolute URLs.
     */
    private void bindDetailPhotos(View detailView, Report report, View photoCard) {
        RecyclerView photoStrip = detailView.findViewById(R.id.detailPhotoStrip);
        TextView photoHint = detailView.findViewById(R.id.detailPhotoHint);

        List<Object> sources = new ArrayList<>();

        // The cover image: prefer the on-device copy when it still exists.
        String coverUrl = report.getPhotoUrl();
        Object cover = null;
        String localPath = report.getLocalPhotoPath();
        if (localPath != null && !localPath.trim().isEmpty()) {
            File localFile = new File(localPath);
            if (localFile.exists()) cover = localFile;
        }
        if (cover == null && coverUrl != null && !coverUrl.trim().isEmpty()) {
            cover = resolveImageUrl(coverUrl);
        }
        if (cover != null) sources.add(cover);

        // The remaining photos come from the server's ordered list. The cover is
        // already shown, so it is skipped rather than repeated.
        for (String url : report.getPhotoUrls()) {
            if (url == null || url.trim().isEmpty()) continue;
            String trimmed = url.trim();
            if (coverUrl != null && trimmed.equals(coverUrl.trim())) continue;
            if (cover instanceof String && trimmed.equals(cover)) continue;
            sources.add(resolveImageUrl(trimmed));
        }

        if (sources.isEmpty()) {
            photoStrip.setVisibility(View.GONE);
            photoHint.setVisibility(View.GONE);
            photoCard.setVisibility(View.GONE);
            return;
        }

        ReportDetailPhotoAdapter adapter = new ReportDetailPhotoAdapter();
        adapter.setOnPhotoClickListener((position, source) -> showFullScreenImage(source));
        photoStrip.setAdapter(adapter);
        adapter.setPhotos(sources);
        photoStrip.setVisibility(View.VISIBLE);
        photoHint.setVisibility(sources.size() > 1 ? View.VISIBLE : View.GONE);
        photoCard.setVisibility(View.VISIBLE);
    }

    /** Expands a stored relative image path to a full URL. */
    private String resolveImageUrl(String url) {
        return ServerConfig.absoluteUrl(requireContext(), url);
    }

    private void showFullScreenImage(Object imageSource) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView fullScreenImage = new ImageView(requireContext());
        fullScreenImage.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullScreenImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullScreenImage.setBackgroundColor(getResources().getColor(android.R.color.black, null));
        dialog.setContentView(fullScreenImage);

        Glide.with(this).load(imageSource).into(fullScreenImage);

        fullScreenImage.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}