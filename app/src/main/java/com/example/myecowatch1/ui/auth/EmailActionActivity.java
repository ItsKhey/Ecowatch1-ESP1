package com.example.myecowatch1.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.myecowatch1.utils.EmailActionLink;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.ActionCodeResult;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Entry point for the emailed links.
 *
 * <p>Registration verification and password reset both continue at the same App
 * Link ({@code https://<project>.firebaseapp.com/finishSignUp}), because that is
 * the single continue URL the backend hands Firebase. This activity exists only
 * to read the {@code mode} parameter and forward the link to the screen that
 * actually needs it, so the two journeys can keep separate UI without asking
 * Firebase for two different URLs.
 *
 * <p>It has no layout of its own. It is declared {@code noHistory} and
 * {@code excludeFromRecents} so it never appears in the back stack: pressing back
 * from the screen it opened should leave the app, not land here.
 *
 * <p>Nothing is authenticated here. The activity forwards an opaque code and the
 * backend decides what it means.
 */
public class EmailActionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        route(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        route(intent);
    }

    /**
     * Decides which screen the incoming link belongs to.
     *
     * <p>Two shapes arrive here, and both must be handled:
     *
     * <ul>
     *   <li><b>With parameters.</b> When Firebase redirects straight into the app
     *       (handleCodeInApp), the link carries {@code mode} and {@code oobCode}.
     *       {@code mode=resetPassword} opens the new-password screen.
     *   <li><b>With no parameters.</b> Firebase can instead complete the action on
     *       its own hosted page and then redirect to the continue URL carrying
     *       nothing at all. That is still a verification landing, so it must reach
     *       the verification screen rather than dead-end.
     * </ul>
     *
     * <p>The {@code mode} parameter is a hint, not the authority. It is supplied
     * by the email template rather than by this app, so it can be absent or
     * misspelled while the code itself is perfectly valid — and a password-reset
     * link misread as a verification link drops the resident on a screen that
     * cannot help them. So whenever a code is present but {@code mode} does not
     * name a journey, the code is asked what it actually is.
     */
    private void route(Intent incoming) {
        String oobCode = EmailActionLink.readOobCode(incoming);

        if (EmailActionLink.isResetPassword(incoming)) {
            forward(incoming, ResetPasswordActivity.class);
            return;
        }

        if (oobCode != null && !oobCode.isEmpty()) {
            // An unrecognised mode with a real code behind it: ask Firebase
            // directly instead of guessing. Falls back to verification if the
            // code is expired or already used, which is where a code that cannot
            // be used belongs.
            resolveByActionCode(incoming, oobCode);
            return;
        }

        // Either an explicit verifyEmail mode or a bare landing. Both are
        // verification.
        forward(incoming, EmailVerificationActivity.class);
    }

    /**
     * Asks Firebase what an action code was issued for.
     *
     * <p>{@code checkActionCode} is the authority here: it reports the operation
     * the code was actually minted for, which survives the {@code mode} parameter
     * being dropped or altered anywhere between the template and the device.
     */
    private void resolveByActionCode(Intent incoming, String oobCode) {
        FirebaseAuth.getInstance()
                .checkActionCode(oobCode)
                .addOnCompleteListener(task -> {
                    if (resolveOperation(task) == ActionCodeResult.PASSWORD_RESET) {
                        forward(incoming, ResetPasswordActivity.class);
                    } else {
                        // Verification, or a code that could not be read. An
                        // expired or already-used code belongs on the verification
                        // screen, which explains the situation, rather than on a
                        // new-password form that would only fail on submit.
                        forward(incoming, EmailVerificationActivity.class);
                    }
                });
    }

    /**
     * The operation the code was minted for, or {@link ActionCodeResult#ERROR}
     * when Firebase could not read it at all.
     */
    private int resolveOperation(Task<ActionCodeResult> task) {
        if (task == null || !task.isSuccessful() || task.getResult() == null) {
            return ActionCodeResult.ERROR;
        }
        return task.getResult().getOperation();
    }

    private void forward(Intent incoming, Class<?> target) {
        EmailActionLink.forwardTo(this, target, incoming);
        finish();
    }
}
