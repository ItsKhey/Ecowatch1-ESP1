package com.example.myecowatch1.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Reads the Firebase action link.
 *
 * <p>Both the verification link and the password reset link point at the same
 * HTTPS App Link ({@code https://<project>.firebaseapp.com/finishSignUp}) because
 * that is what the backend asks Firebase for. Firebase distinguishes them with a
 * {@code mode} query parameter and carries the credential as {@code oobCode}, so
 * one intent can arrive for either journey.
 *
 * <p>Two ways the code can reach the app are supported:
 *
 * <ul>
 *   <li>as a query parameter on the deep link, which is how Firebase delivers it
 *       when Android resolves the App Link straight into the app, and
 *   <li>as an intent extra, which is how {@link
 *       com.example.myecowatch1.ui.auth.EmailActionActivity} forwards it after
 *       deciding which screen the {@code mode} belongs to.
 * </ul>
 *
 * The oobCode is a credential, so it is never logged and never persisted.
 */
public final class EmailActionLink {

    /** Firebase's values for the {@code mode} query parameter. */
    public static final String MODE_RESET_PASSWORD = "resetPassword";
    public static final String MODE_VERIFY_EMAIL = "verifyEmail";

    private static final String QUERY_OOB_CODE = "oobCode";
    private static final String QUERY_MODE = "mode";

    private static final String EXTRA_OOB_CODE = "ecowatch.extra.OOB_CODE";
    private static final String EXTRA_MODE = "ecowatch.extra.MODE";

    private EmailActionLink() {
    }

    /**
     * Returns the action code, or an empty string when the link carried none.
     *
     * <p>The extra is checked first so a value forwarded by the router is not
     * re-parsed out of a URI it did not come from.
     */
    @Nullable
    public static String readOobCode(Intent intent) {
        if (intent == null) {
            return null;
        }

        String forwarded = intent.getStringExtra(EXTRA_OOB_CODE);
        if (!TextUtils.isEmpty(forwarded)) {
            return forwarded.trim();
        }

        return readQueryParameter(intent, QUERY_OOB_CODE);
    }

    /**
     * Returns {@link #MODE_RESET_PASSWORD} or {@link #MODE_VERIFY_EMAIL}, or an
     * empty string when the link named neither.
     */
    @Nullable
    public static String readMode(Intent intent) {
        if (intent == null) {
            return null;
        }

        String forwarded = intent.getStringExtra(EXTRA_MODE);
        if (!TextUtils.isEmpty(forwarded)) {
            return forwarded;
        }

        return readQueryParameter(intent, QUERY_MODE);
    }

    @Nullable
    private static String readQueryParameter(Intent intent, String name) {
        Uri data = intent.getData();
        if (data == null) {
            return null;
        }

        String value = data.getQueryParameter(name);
        return TextUtils.isEmpty(value) ? null : value.trim();
    }

    public static boolean isResetPassword(Intent intent) {
        return MODE_RESET_PASSWORD.equals(readMode(intent));
    }

    public static boolean isVerifyEmail(Intent intent) {
        return MODE_VERIFY_EMAIL.equals(readMode(intent));
    }

    /**
     * Carries the action code from the incoming link onto the screen that needs
     * it.
     *
     * <p>Only the opaque code crosses this boundary. Nothing about the resident
     * is attached, and no password is ever present in the intent.
     */
    public static void forwardTo(Context context, Class<?> target, Intent incoming) {
        Intent intent = new Intent(context, target);

        String oobCode = readOobCode(incoming);
        if (oobCode != null) {
            intent.putExtra(EXTRA_OOB_CODE, oobCode);
        }

        String mode = readMode(incoming);
        if (mode != null) {
            intent.putExtra(EXTRA_MODE, mode);
        }

        context.startActivity(intent);
    }
}
