package com.example.myecowatch1.ui.auth;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myecowatch1.R;

/**
 * Host for {@link VerifyEmailFragment}, and the target of the verification deep
 * link.
 *
 * <p>Opening the emailed link lands the resident straight on the "I have opened
 * the link" screen, so they never have to find the app themselves. The fragment
 * reads the pending registration from {@link
 * com.example.myecowatch1.utils.PendingRegistrationStore}, so the link needs to
 * carry nothing beyond the oobCode that Firebase appends.
 *
 * <p>Registration must be finished on the device it started on. A link opened on
 * a different device has no matching stored registration, and the screen says so
 * rather than failing silently.
 */
public class EmailVerificationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Matches RegisterActivity and LoginActivity: the auth screens are always
        // presented in light mode so the dark green palette reads correctly.
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_email_verification);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.verification_main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top,
                            systemBars.right, systemBars.bottom);
                    return insets;
                });

        // Only on a cold start. On a configuration change the fragment manager
        // restores the existing fragment and adding another would stack a second
        // copy of the screen.
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.verificationFragmentContainer, new VerifyEmailFragment())
                    .commit();
        }
    }
}
