package com.example.myecowatch1.ui.alerts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.Announcement;
import com.example.myecowatch1.data.repository.AnnouncementRepository;
import com.example.myecowatch1.utils.NotificationDateFormatter;

import java.util.List;

/**
 * Full announcement history for the signed-in resident.
 *
 * <p>Rendered from the same Room cache the dashboard preview uses, so the two
 * screens can never disagree and the list works offline.
 */
public class AnnouncementsFragment extends Fragment {

    /** Matches the collapsed maxLines on the shared announcement row. */
    private static final int COLLAPSED_MAX_LINES = 3;

    private LinearLayout announcementList;
    private TextView emptyText;
    private View emptyState;

    /**
     * Backs pull-to-refresh. The fragment owns the repository, so the default
     * implementation is what actually refreshes the feed.
     */
    public interface SwipeRefreshListener {
        void onRefreshRequested(AnnouncementsFragment fragment);

        SwipeRefreshListener INSTANCE = fragment -> fragment.refreshFromServer();
    }

    private AnnouncementRepository repository;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private SwipeRefreshListener refreshListener = SwipeRefreshListener.INSTANCE;

    public void setSwipeRefreshListener(SwipeRefreshListener listener) {
        this.refreshListener = listener != null ? listener : SwipeRefreshListener.INSTANCE;
    }

    /** Re-reads the feed and stops the spinner when the request settles. */
    public void refreshFromServer() {
        if (repository == null) {
            stopRefreshing();
            return;
        }
        repository.fetchAnnouncements(this::stopRefreshing);
    }

    private void stopRefreshing() {
        if (getView() == null) return;
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_announcements, container, false);

        announcementList = view.findViewById(R.id.announcementsList);
        emptyText = view.findViewById(R.id.announcementsEmptyText);
        emptyState = view.findViewById(R.id.announcementsEmptyState);
        swipeRefreshLayout = view.findViewById(R.id.announcementsSwipeRefresh);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(
                    getResources().getColor(R.color.green, null));
            swipeRefreshLayout.setOnRefreshListener(() -> refreshListener.onRefreshRequested(this));
        }

        View backBtn = view.findViewById(R.id.btnBack);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        // The repository is application-scoped, so recreating the fragment does
        // not drop the cached feed the user is already looking at.
        repository = new AnnouncementRepository(requireActivity().getApplication());

        return view;
    }

    /**
     * Observes the feed once the view exists.
     *
     * <p>This has to happen here rather than in {@code onCreateView}. The
     * fragment's view is only attached after {@code onCreateView} returns, so
     * {@code getView()} is still null at that point. Binding there bailed out
     * before registering the observer, which left the list permanently empty:
     * the card rendered with its default visible state and no rows inside it.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bind(repository);
    }

    /**
     * Binds the announcement feed. The repository is created here so the
     * fragment is self-contained: both the dashboard preview and this screen
     * read the same Room cache.
     */
    public void bind(@NonNull AnnouncementRepository repository) {
        this.repository = repository;
        if (getView() == null) return;

        // Room's LiveData replays its current contents on every new observer, so
        // re-binding after a configuration change re-reads the cache. Removing
        // the previous observer first keeps a single registration per view.
        LiveData<List<Announcement>> announcements = repository.getAnnouncements();
        announcements.removeObservers(getViewLifecycleOwner());
        announcements.observe(getViewLifecycleOwner(), this::render);

        repository.fetchAnnouncements(this::stopRefreshing);
    }

    /** Rebuilds the list; exposed so the activity can reuse the same renderer. */
    public void render(@Nullable List<Announcement> announcements) {
        if (announcementList == null) return;
        announcementList.removeAllViews();

        int count = announcements == null ? 0 : announcements.size();

        // The card and the empty state are swapped rather than shown together.
        // Leaving the empty card visible is what collapsed it into a thin bar:
        // with no rows inside, a wrap_content card is just its own padding.

        if (emptyState != null) {
            emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        }
        if (emptyText != null) {
            emptyText.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        }
        if (count == 0) return;

        for (int i = 0; i < count; i++) {
            addAnnouncementItem(announcements.get(i), i == count - 1);
        }
    }

    private void addAnnouncementItem(Announcement announcement, boolean isLast) {
        if (announcement == null) return;

        View itemView = getLayoutInflater().inflate(R.layout.item_announcement, announcementList, false);

        TextView titleView = itemView.findViewById(R.id.announcementItemTitle);
        TextView bodyView = itemView.findViewById(R.id.announcementItemBody);
        TextView timeView = itemView.findViewById(R.id.announcementItemTime);
        TextView badgeView = itemView.findViewById(R.id.announcementPriorityBadge);
        View divider = itemView.findViewById(R.id.announcementItemDivider);

        String title = announcement.getTitle();
        titleView.setText(title != null && !title.trim().isEmpty()
                ? title
                : getString(R.string.announcement_untitled));

        String message = announcement.getMessage();
        bodyView.setText(message != null && !message.trim().isEmpty()
                ? message
                : getString(R.string.announcement_no_body));

        timeView.setText(NotificationDateFormatter.formatRelative(announcement.getCreatedAt()));

        badgeView.setVisibility(announcement.isImportant() ? View.VISIBLE : View.GONE);
        if (divider != null) divider.setVisibility(isLast ? View.GONE : View.VISIBLE);

        attachExpandOnTap(itemView, bodyView);

        announcementList.addView(itemView);
    }

    /**
     * Lets a row reveal its whole message.
     *
     * <p>The shared row layout truncates the body to three lines, which is right
     * for the dashboard preview but hides the actual notice on this screen. A
     * tap expands the row to the full text and a second tap collapses it again,
     * so a long announcement can be read without leaving the feed.
     */
    private void attachExpandOnTap(@NonNull View itemView, @NonNull TextView bodyView) {
        itemView.setOnClickListener(v -> {
            boolean expanded = bodyView.getMaxLines() > COLLAPSED_MAX_LINES;
            bodyView.setMaxLines(expanded ? COLLAPSED_MAX_LINES : Integer.MAX_VALUE);
            // maxLines alone is capped by the layout's ellipsize, so the height
            // has to be re-measured for the newly revealed lines to appear.
            bodyView.requestLayout();
        });
    }
}
