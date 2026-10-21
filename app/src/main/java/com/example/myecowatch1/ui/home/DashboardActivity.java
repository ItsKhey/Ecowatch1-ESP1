package com.example.myecowatch1.ui.home;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.data.repository.AnnouncementRepository;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.ui.alerts.AnnouncementsFragment;
import com.example.myecowatch1.ui.alerts.NotificationsFragment;
import com.example.myecowatch1.ui.auth.LoginActivity;
import com.example.myecowatch1.ui.profile.ProfileFragment;
import com.example.myecowatch1.ui.profile.SettingsMenuFragment;
import com.example.myecowatch1.ui.reports.ReportsFragment;
import com.example.myecowatch1.utils.LocaleHelper;
import com.example.myecowatch1.utils.SessionManager;

public class DashboardActivity extends AppCompatActivity {
    public static final String ACTION_OPEN_NOTIFICATION =
            "com.example.myecowatch1.action.OPEN_NOTIFICATION";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_REPORT_ID = "report_id";

    /**
     * Payload value that sends a tapped notification to the Announcements screen.
     * Sent by the backend alongside ANNOUNCEMENT broadcasts, which own no
     * report and so cannot be routed by report id.
     */
    public static final String TARGET_SCREEN_ANNOUNCEMENTS = "ANNOUNCEMENTS";

    /** Intent extra carrying {@link #TARGET_SCREEN_ANNOUNCEMENTS}. */
    public static final String EXTRA_TARGET_SCREEN = "targetScreen";

    private LinearLayout itemHome, itemInbox, itemCreate, itemNotifications, itemProfile;
    private ImageView iconHome, iconInbox, iconCreate, iconNotifications, iconProfile, pfpImage, backgroundImage;
    private TextView labelHome, labelInbox, labelCreate, labelNotifications, labelProfile;
    private TextView notificationBadge;
    private SessionManager sessionManager;
    private AuthRepository authRepository;
    private com.example.myecowatch1.data.repository.NotificationRepository notificationRepository;
    private AnnouncementRepository announcementRepository;
    private android.os.Handler pollerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pollerRunnable = new Runnable() {
        @Override
        public void run() {
            if (notificationRepository != null) {
                notificationRepository.fetchNotifications();
            }
            // FCM is the primary path; REST reconciliation remains as a
            // lightweight fallback for foreground state and token rotation.
            pollerHandler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        LocaleHelper.applyLocale(this, sessionManager.getLanguage());
        
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        authRepository = new AuthRepository(getApplication());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pfpImage = findViewById(R.id.pfpImage);
        pfpImage.setOnClickListener(v -> {
            updateNavUI(itemProfile, iconProfile, labelProfile);
            // Load SettingsMenuFragment as base and then ProfileFragment on top
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new SettingsMenuFragment())
                    .commit();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        });
        backgroundImage = findViewById(R.id.backgroundImage);
        updateBackground();

        initBottomNav();
        
        // Load default fragment and set UI
        loadFragment(new HomeFragment(), "Home");
        updateNavUI(itemHome, iconHome, labelHome);
        updateTopBarDate();

        // Sync profile from server
        authRepository.fetchMe(new AuthRepository.ApiCallback<User>() {
            @Override public void onSuccess(User result) {}
            @Override public void onError(String error) {}
        });

        // Observe user for PFP
        authRepository.getUser().observe(this, user -> {
            if (user != null && user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(ServerConfig.absoluteUrl(this, user.getProfileImageUrl()))
                        .placeholder(R.drawable.pfp)
                        .into(pfpImage);
            } else {
                pfpImage.setImageResource(R.drawable.pfp);
            }
        });

        notificationBadge = findViewById(R.id.badgeNotifications);
        notificationRepository =
                com.example.myecowatch1.data.repository.NotificationRepository
                        .getInstance(getApplication());
        // Used to refresh the feed when a tapped announcement notification
        // routes into the Announcements screen.
        announcementRepository = new AnnouncementRepository(getApplication());
        notificationRepository.getUnreadCount().observe(this, count -> {
            int unread = count == null ? 0 : Math.max(0, count);
            notificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            notificationBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        });
        checkNotificationPermission();
        com.example.myecowatch1.notifications.FcmTokenSyncScheduler.syncCurrentToken(this);
        pollerHandler.postDelayed(this::handleNotificationIntent, 250);
    }

    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent();
    }

    private void handleNotificationIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID);
        if (notificationId == null) notificationId = intent.getStringExtra("notificationId");
        String reportId = intent.getStringExtra(EXTRA_REPORT_ID);
        if (reportId == null) reportId = intent.getStringExtra("reportId");
        String targetScreen = intent.getStringExtra(EXTRA_TARGET_SCREEN);
        boolean notificationAction = ACTION_OPEN_NOTIFICATION.equals(intent.getAction())
                || "OPEN_ECOWATCH_NOTIFICATION".equals(intent.getAction())
                || notificationId != null
                || reportId != null;
        if (!notificationAction) return;

        if (notificationId != null) {
            notificationRepository.markAsReadByServerId(notificationId);
        }

        if (reportId != null && !reportId.trim().isEmpty()) {
            openReportById(reportId);
        } else if (TARGET_SCREEN_ANNOUNCEMENTS.equals(targetScreen)) {
            // A broadcast owns no report, so without this the tap would fall
            // through to the Alerts list instead of the announcement itself.
            openAnnouncementFeed();
        } else {
            itemNotifications.performClick();
        }

        intent.removeExtra(EXTRA_NOTIFICATION_ID);
        intent.removeExtra("notificationId");
        intent.removeExtra(EXTRA_REPORT_ID);
        intent.removeExtra("reportId");
        intent.removeExtra("targetScreen");
        intent.setAction(Intent.ACTION_MAIN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.isSessionExpired()) {
            performLogout("Session expired. Please login again.");
        } else {
            sessionManager.updateLastActive();
            // Global refresh when returning to app
            authRepository.fetchMe(null);
            com.example.myecowatch1.notifications.FcmTokenSyncScheduler
                    .syncCurrentToken(this);

            // Start poller
            pollerHandler.removeCallbacks(pollerRunnable);
            pollerHandler.post(pollerRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop poller to save battery/bandwidth in background
        pollerHandler.removeCallbacks(pollerRunnable);
    }

    private void performLogout(String message) {
        authRepository.logout();
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Signs the resident out and returns to the login screen. Used by the
     * account lifecycle actions on the profile screen, which must end the
     * session the same way an ordinary logout does.
     */
    public void signOutAndReturnToLogin(String message) {
        performLogout(message);
    }

    private void initBottomNav() {
        itemHome = findViewById(R.id.itemHome);
        itemInbox = findViewById(R.id.itemInbox);
        itemCreate = findViewById(R.id.itemCreate);
        itemNotifications = findViewById(R.id.itemNotifications);
        itemProfile = findViewById(R.id.itemProfile);

        iconHome = findViewById(R.id.iconHome);
        iconInbox = findViewById(R.id.iconInbox);
        iconCreate = findViewById(R.id.iconCreate);
        iconNotifications = findViewById(R.id.iconNotifications);
        iconProfile = findViewById(R.id.iconProfile);

        labelHome = findViewById(R.id.labelHome);
        labelInbox = findViewById(R.id.labelInbox);
        labelCreate = findViewById(R.id.labelCreate);
        labelNotifications = findViewById(R.id.labelNotifications);
        labelProfile = findViewById(R.id.labelProfile);

        itemHome.setOnClickListener(v -> {
            updateNavUI(itemHome, iconHome, labelHome);
            loadFragment(new HomeFragment(), "Home");
        });

        itemInbox.setOnClickListener(v -> {
            updateNavUI(itemInbox, iconInbox, labelInbox);
            loadFragment(new ReportsFragment(), "Reports");
        });

        itemNotifications.setOnClickListener(v -> {
            updateNavUI(itemNotifications, iconNotifications, labelNotifications);
            loadFragment(new NotificationsFragment(), "Alerts");
        });

        itemProfile.setOnClickListener(v -> {
            updateNavUI(itemProfile, iconProfile, labelProfile);
            loadFragment(new SettingsMenuFragment(), "Profile");
        });
        
        itemCreate.setOnClickListener(v -> {
            updateNavUI(itemCreate, iconCreate, labelCreate);
            loadFragment(new com.example.myecowatch1.ui.reports.CreateReportFragment(), "Create Report");
        });
    }

    public void navigateToReports() {
        if (itemInbox != null) {
            itemInbox.performClick();
        }
    }

    /**
     * Opens the resident announcement feed. Backs the "View all" action on the
     * dashboard Announcements card.
     */
    public void navigateToAnnouncements() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new AnnouncementsFragment())
                .addToBackStack(null)
                .commit();
    }

    /**
     * Opens the announcement feed, refreshing it first so the announcement a
     * resident just tapped is actually there to read.
     */
    public void openAnnouncementFeed() {
        if (announcementRepository != null) {
            announcementRepository.fetchAnnouncements();
        }
        navigateToAnnouncements();
    }

    public void openReportById(String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) return;
        if (itemInbox == null) return;
        updateNavUI(itemInbox, iconInbox, labelInbox);
        loadFragment(ReportsFragment.forReportId(reportId.trim()), "Reports");
    }

    private void updateTopBarDate() {
        TextView topBarTitle = findViewById(R.id.topBarTitle);
        if (topBarTitle != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMMM d yyyy", Locale.getDefault());
            String currentDate = sdf.format(new Date());
            topBarTitle.setText(currentDate);
        }
    }

    private void updateNavUI(LinearLayout activeItem, ImageView activeIcon, TextView activeLabel) {
        // Reset all to idle
        resetNavItem(itemHome, iconHome, labelHome);
        resetNavItem(itemInbox, iconInbox, labelInbox);
        resetNavItem(itemNotifications, iconNotifications, labelNotifications);
        resetNavItem(itemProfile, iconProfile, labelProfile);
        resetNavItem(itemCreate, iconCreate, labelCreate);

        if (activeItem != null) {
            // Set active
            if (activeItem == itemCreate) {
                activeItem.setBackgroundResource(R.drawable.circular_green_background);
            } else {
                activeItem.setBackgroundResource(R.drawable.active_pill_background);
            }
            activeIcon.setColorFilter(getResources().getColor(R.color.white, getTheme()));
            if (activeLabel != null) activeLabel.setVisibility(View.VISIBLE);
        }
    }

    private void resetNavItem(LinearLayout item, ImageView icon, TextView label) {
        if (item == itemCreate) {
            item.setBackgroundResource(R.drawable.circular_outlined_background);
            icon.setColorFilter(getResources().getColor(R.color.green, getTheme()));
        } else {
            item.setBackground(null);
            icon.setColorFilter(getResources().getColor(R.color.green, getTheme()));
        }
        if (label != null) label.setVisibility(View.GONE);
    }

    private void updateBackground() {
        if (backgroundImage == null) return;
        
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            backgroundImage.setImageResource(R.drawable.darktheme);
        } else {
            backgroundImage.setImageResource(R.drawable.bg4);
        }
    }

    private void loadFragment(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        
        // Date is now permanent in the top bar pill, so we don't update it here
    }
}