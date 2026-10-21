package com.example.ecowatchpersonnel1.ui.home;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.Glide;
import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.Personnel;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ServerConfig;
import com.example.ecowatchpersonnel1.data.repository.PersonnelAuthRepository;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationRepository;
import com.example.ecowatchpersonnel1.notifications.FcmTokenSyncScheduler;
import com.example.ecowatchpersonnel1.ui.alerts.NotificationsFragment;
import com.example.ecowatchpersonnel1.ui.auth.LoginActivity;
import com.example.ecowatchpersonnel1.ui.profile.ProfileFragment;
import com.example.ecowatchpersonnel1.ui.profile.SettingsMenuFragment;
import com.example.ecowatchpersonnel1.ui.tasks.TaskDetailActivity;
import com.example.ecowatchpersonnel1.ui.tasks.TasksFragment;
import com.example.ecowatchpersonnel1.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The cleaner's home: three tabs over one fragment container.
 *
 * <p>Also the destination for a tapped task push. The backend's click action is
 * {@code com.example.ecowatchpersonnel1.action.OPEN_NOTIFICATION}, and the data
 * payload carries the assignment id, so a tap lands on the task rather than on a
 * generic list.
 */
public class DashboardActivity extends AppCompatActivity {

    /** Must match {@code PERSONNEL_CLICK_ACTION} on the backend. */
    public static final String ACTION_OPEN_NOTIFICATION =
            "com.example.ecowatchpersonnel1.action.OPEN_NOTIFICATION";

    // These names are the FCM data payload keys verbatim, so extras survive the
    // trip from the system tray untouched.
    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";
    public static final String EXTRA_REPORT_ID = "reportId";

    private static final long UNREAD_POLL_MS = 30_000L;

    private LinearLayout itemTasks, itemNotifications, itemProfile;
    private ImageView iconTasks, iconNotifications, iconProfile, pfpImage, backgroundImage;
    private TextView labelTasks, labelNotifications, labelProfile, notificationBadge;

    private SessionManager sessionManager;
    private PersonnelAuthRepository authRepository;
    private PersonnelNotificationRepository notificationRepository;

    private final Handler pollerHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollerRunnable = new Runnable() {
        @Override
        public void run() {
            if (notificationRepository != null) {
                notificationRepository.refreshUnreadCount();
            }
            pollerHandler.postDelayed(this, UNREAD_POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        authRepository = new PersonnelAuthRepository(getApplication());
        notificationRepository = PersonnelNotificationRepository.getInstance(getApplication());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        backgroundImage = findViewById(R.id.backgroundImage);
        updateBackground();

        pfpImage = findViewById(R.id.pfpImage);
        pfpImage.setOnClickListener(v -> selectTab(Tab.MENU));

        initBottomNav();

        notificationBadge = findViewById(R.id.badgeNotifications);
        notificationRepository.getUnreadCount().observe(this, count -> {
            int unread = count == null ? 0 : Math.max(0, count);
            notificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            notificationBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        });

        requestNotificationPermission();
        FcmTokenSyncScheduler.syncCurrentToken(this);

        loadProfileImage();

        // Default tab, unless the activity was opened by a task notification.
        if (!handleNotificationIntent(getIntent())) {
            selectTab(Tab.TASKS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The dashboard asks for no scan of its own. Identity is settled once, on
        // the login screen: a password or a fingerprint there produces the session
        // this screen then trusts for its whole lifetime. Re-prompting on every
        // foreground meant a scan every time the cleaner came back from a task, a
        // settings screen or another app, which trains people to stop using it.
        //
        // What is required here is a session, not a scan. That still closes the
        // route the biometric lock was really there for: a tapped task
        // notification opens this activity directly and never passes the login
        // screen, so a cold start with no session is sent back there to sign in.
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        // Checked before the last-active stamp is refreshed, so time spent on the
        // login screen is not counted as use of the session.
        if (sessionManager.isSessionExpired()) {
            performLogout("Your session expired. Please sign in again.");
            return;
        }

        resumeSignedInWork();
    }

    /**
     * Sends the cleaner back to the login screen when this activity was reached
     * without a session.
     *
     * <p>CLEAR_TOP so the dashboard does not pile up behind the login screen, and
     * {@code finish()} so backing out of login cannot return here. Re-entering
     * through the launcher is what should bring the app back, not this stale
     * instance.
     */
    private void redirectToLogin() {
        pollerHandler.removeCallbacks(pollerRunnable);
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    /** Resumes normal activity once the session is known to be valid. */
    private void resumeSignedInWork() {
        sessionManager.updateLastActive();
        notificationRepository.refreshUnreadCount();
        pollerHandler.removeCallbacks(pollerRunnable);
        pollerHandler.post(pollerRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollerHandler.removeCallbacks(pollerRunnable);
    }

    // ---------------------------------------------------------------- tabs ---

    public enum Tab { TASKS, NOTIFICATIONS, MENU, PROFILE }

    private void initBottomNav() {
        itemTasks = findViewById(R.id.itemTasks);
        itemNotifications = findViewById(R.id.itemNotifications);
        itemProfile = findViewById(R.id.itemProfile);
        iconTasks = findViewById(R.id.iconTasks);
        iconNotifications = findViewById(R.id.iconNotifications);
        iconProfile = findViewById(R.id.iconProfile);
        labelTasks = findViewById(R.id.labelTasks);
        labelNotifications = findViewById(R.id.labelNotifications);
        labelProfile = findViewById(R.id.labelProfile);

        itemTasks.setOnClickListener(v -> selectTab(Tab.TASKS));
        itemNotifications.setOnClickListener(v -> selectTab(Tab.NOTIFICATIONS));
        itemProfile.setOnClickListener(v -> selectTab(Tab.MENU));
    }

    public void selectTab(Tab tab) {
        int titleRes;
        Fragment fragment;
        switch (tab) {
            case NOTIFICATIONS:
                titleRes = R.string.nav_alerts;
                fragment = new NotificationsFragment();
                break;
            case MENU:
                titleRes = R.string.nav_menu;
                fragment = new SettingsMenuFragment();
                break;
            case PROFILE:
                titleRes = R.string.nav_profile;
                fragment = new ProfileFragment();
                break;
            case TASKS:
            default:
                titleRes = R.string.nav_tasks;
                fragment = new TasksFragment();
                break;
        }

        styleNavItem(itemTasks, iconTasks, labelTasks, tab == Tab.TASKS);
        styleNavItem(itemNotifications, iconNotifications, labelNotifications,
                tab == Tab.NOTIFICATIONS);
        styleNavItem(itemProfile, iconProfile, labelProfile, tab == Tab.MENU || tab == Tab.PROFILE);

        TextView topBarTitle = findViewById(R.id.topBarTitle);
        if (topBarTitle != null) {
            topBarTitle.setText(titleRes);
        }

        // Switching tabs clears whatever the previous tab had pushed. Without
        // this, opening Task History from the menu and then tapping My Tasks
        // would leave the history fragment on the back stack, and a back press
        // would drop the cleaner into the menu they had already left.
        getSupportFragmentManager().popBackStackImmediate(null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    /**
     * Styles one bottom navigation item for the active or idle state.
     *
     * <p>Only the active destination shows its label, matching the resident and
     * admin bars. Idle destinations collapse to the icon alone so the bar keeps
     * one label at a time instead of three stacked names.
     */
    private void styleNavItem(LinearLayout item, ImageView icon, TextView label, boolean active) {
        if (item == null) return;
        if (active) {
            item.setBackgroundResource(R.drawable.active_pill_background);
            icon.setColorFilter(ContextCompat.getColor(this, R.color.white));
            label.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            item.setBackground(null);
            icon.setColorFilter(ContextCompat.getColor(this, R.color.green));
            label.setTextColor(ContextCompat.getColor(this, R.color.green));
        }
        if (label != null) label.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------- intent ---

    /**
     * Routes a task notification.
     *
     * @return true when the intent was a notification, so the caller skips the
     *         default tab.
     */
    private boolean handleNotificationIntent(Intent intent) {
        if (intent == null) return false;

        String notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID);
        String assignmentId = intent.getStringExtra(EXTRA_ASSIGNMENT_ID);
        String reportId = intent.getStringExtra(EXTRA_REPORT_ID);

        boolean isNotification = ACTION_OPEN_NOTIFICATION.equals(intent.getAction())
                || notificationId != null
                || assignmentId != null;
        if (!isNotification) return false;

        if (notificationId != null) {
            notificationRepository.markAsReadById(notificationId);
        }

        if (assignmentId != null && !assignmentId.trim().isEmpty()) {
            startActivity(TaskDetailActivity.intentFor(this, assignmentId.trim()));
        } else if (reportId != null) {
            // Personnel pushes always carry an assignment id; a report-only push
            // would not be addressable to a task, so land on the list.
            selectTab(Tab.NOTIFICATIONS);
        } else {
            selectTab(Tab.NOTIFICATIONS);
        }

        intent.removeExtra(EXTRA_NOTIFICATION_ID);
        intent.removeExtra(EXTRA_ASSIGNMENT_ID);
        intent.removeExtra(EXTRA_REPORT_ID);
        intent.setAction(Intent.ACTION_MAIN);
        return true;
    }

    // ------------------------------------------------------------- profile ---

    private void loadProfileImage() {
        authRepository.fetchMe(new ApiCallback<Personnel>() {
            @Override
            public void onSuccess(Personnel personnel) {
                if (personnel == null || pfpImage == null) return;
                String url = personnel.profileImageUrl;
                if (url == null || url.trim().isEmpty()) {
                    url = personnel.profileImage;
                }
                if (url == null || url.trim().isEmpty()) {
                    pfpImage.setImageResource(R.drawable.pfp);
                    return;
                }
                Glide.with(DashboardActivity.this)
                        .load(ServerConfig.absoluteUrl(DashboardActivity.this, url))
                        .placeholder(R.drawable.pfp)
                        .error(R.drawable.pfp)
                        .into(pfpImage);
            }

            @Override
            public void onError(String message) {
                // The avatar is decoration; the tasks tab still works.
            }
        });
    }

    // ------------------------------------------------------------- logout ---

    public void signOutAndReturnToLogin(String message) {
        performLogout(message);
    }

    private void performLogout(String message) {
        // Only announced when the cleaner asked to leave. A forced sign-out carries
        // its own explanation, and "Logging out" over the top of it would read as
        // though their own action caused it.
        if (message == null) {
            Toast.makeText(this, R.string.toast_logging_out, Toast.LENGTH_SHORT).show();
        }

        notificationRepository.clearFcmTokenBestEffort();
        authRepository.logout();
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ------------------------------------------------------ misc plumbing ---

    private void updateBackground() {
        if (backgroundImage == null) return;
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            backgroundImage.setImageResource(R.drawable.darktheme);
        } else {
            backgroundImage.setImageResource(R.drawable.bg4);
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }
}
