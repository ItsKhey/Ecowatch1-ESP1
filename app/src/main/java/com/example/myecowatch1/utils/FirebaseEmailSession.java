package com.example.myecowatch1.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.ActionCodeSettings;

/**
 * Owns the short-lived Firebase session used to send EcoWatch's emails.
 *
 * <p>Why a session exists at all: Firebase will not send a verification email
 * unless a user is signed in, and EcoWatch never signs anyone in. So the app
 * creates the Firebase user during registration purely so Firebase can deliver
 * and track that one email, and signs out immediately once the EcoWatch account
 * exists.
 *
 * <p>What this session is NOT, and the reason the architecture still holds
 * together:
 *
 *   - It is not an EcoWatch login. The app never asks Firebase whether a
 *     credential is valid. Sign-in to EcoWatch is username + password against
 *     Supabase, and the JWT from this backend is the only session EcoWatch has.
 *   - It grants no access to anything. The account it describes does not exist in
 *     Supabase until the backend confirms the address was verified, and the
 *     backend derives the uid itself from the token rather than trusting the app.
 *   - It is not long-lived. {@link #signOut()} runs as soon as the account is
 *     created, and also on any failure path, so a device is not left holding a
 *     Firebase session for a resident who is not mid-registration.
 *
 * <p>The session is therefore a delivery mechanism, not an authentication
 * authority.
 */
public final class FirebaseEmailSession {

    /**
     * The App Link the emailed link continues to.
     *
     * <p>Must match the {@code android:host} and {@code pathPrefix} declared in
     * AndroidManifest.xml, and the host must be listed as an authorized domain in
     * the Firebase console. A mismatch means the link opens a browser instead of
     * the app.
     */
    private static final String CONTINUE_URL =
            "https://ecowatch1-4445f.firebaseapp.com/finishSignUp";

    /**
     * Matches android:package in the manifest and the package_name registered in
     * the Firebase console. Firebase uses it to route the emailed action back to
     * this app, so a mismatch here would send the link to the wrong one.
     */
    private static final String ANDROID_PACKAGE = "com.example.myecowatch1";

    private FirebaseEmailSession() {
    }

    /**
     * Continues the emailed action inside the app rather than on a hosted page.
     *
     * <p>No Activity is needed: every value here is plain data describing where
     * the link should go, which also means this can be called from a ViewModel
     * without holding an Activity reference across configuration changes.
     */
    public static ActionCodeSettings actionCodeSettings() {
        return ActionCodeSettings.newBuilder()
                .setUrl(CONTINUE_URL)
                .setHandleCodeInApp(true)
                .setAndroidPackageName(ANDROID_PACKAGE, false, null)
                .build();
    }

    /**
     * Creates the Firebase user for a new registration and asks Firebase to send
     * the verification email.
     *
     * <p>Call order matters. Firebase needs a signed-in user before it will send
     * anything, so the user is created first and the email is requested from the
     * session that creates.
     *
     * <p>If an unverified Firebase user already exists for the address, from an
     * abandoned earlier attempt, this signs into it instead of failing. That is
     * safe because an unverified identity owns nothing: no Supabase account can
     * reference it, and the backend only ever writes the uid after a successful
     * verification.
     */
    public static void createAndSendVerificationEmail(@NonNull String email,
                                                      @NonNull String password,
                                                      @NonNull Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        FirebaseAuthException failure = unwrap(task);
                        String code = failure == null ? "" : failure.getErrorCode();

                        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                            // An identity from a previous abandoned attempt. Sign
                            // into it rather than leaving the address unregisterable.
                            auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(signIn -> {
                                        if (!signIn.isSuccessful()) {
                                            signOutQuietly();
                                            callback.onError(
                                                    describeSignInFailure(signIn.getException()),
                                                    false);
                                            return;
                                        }
                                        sendVerification(auth, callback);
                                    });
                            return;
                        }

                        callback.onError(describeSignInFailure(task.getException()), false);
                    }
                });
    }

    /**
     * Re-sends the verification email for a registration still in progress.
     *
     * <p>This is the "Resend Verification Email" button. Firebase throttles
     * repeated sends itself, so there is no local cooldown to keep.
     */
    public static void resendVerificationEmail(@NonNull Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            // Expected after a process death: the Firebase session is not
            // persisted because the app signs out on every exit path, and
            // EcoWatch deliberately keeps no Firebase credentials on disk.
            callback.onError(
                    "This registration is no longer active on this device. Please "
                            + "register again to get a new verification email.",
                    false);
            return;
        }

        sendVerification(FirebaseAuth.getInstance(), callback);
    }

    private static void sendVerification(@NonNull FirebaseAuth auth,@NonNull Callback callback) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError("Your verification session has expired. Please register again.", false);
            return;
        }

        user.sendEmailVerification(actionCodeSettings())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onError(describeVerificationFailure(task.getException()), true);
                    }
                });
    }

    /**
     * Confirms with the backend that the resident has actually opened the link.
     *
     * <p>The Firebase session is refreshed from the server first, because
     * {@code emailVerified} on the cached local user does not update on its own.
     * The resulting ID token is then handed to the backend, which is what
     * actually decides whether the EcoWatch account may be created.
     *
     * <p>On success the Firebase session is signed out. From that point EcoWatch
     * knows nothing about Firebase and the resident signs in normally.
     */
    public static void confirmVerification(@NonNull Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            callback.onError(
                    "This registration is no longer active on this device. Please "
                            + "register again to get a new verification email.",
                    false);
            return;
        }

        user.reload()
                .addOnCompleteListener(reload -> {
                    if (!reload.isSuccessful()) {
                        callback.onError("Could not reach the verification service. "
                                + "Check your connection and try again.", true);
                        return;
                    }

                    FirebaseUser refreshed = FirebaseAuth.getInstance().getCurrentUser();

                    if (refreshed == null) {
                        callback.onError("Your verification session has expired. "
                                + "Please register again.", false);
                        return;
                    }

                    if (!refreshed.isEmailVerified()) {
                        // Not an error: the resident may simply not have opened
                        // the link yet.
                        callback.onNotYetVerified();
                        return;
                    }

                    refreshed.getIdToken(true)
                            .addOnCompleteListener(token -> {
                                if (!token.isSuccessful() || token.getResult() == null) {
                                    callback.onError("Could not confirm your verification. "
                                            + "Please try again.", true);
                                    return;
                                }

                                // A forced refresh, so the token reflects
                                // emailVerified and is not a cached older one.
                                callback.onVerifiedToken(token.getResult().getToken());
                            });
                });
    }

    /**
     * Ends the Firebase session.
     *
     * <p>Called once the EcoWatch account exists, and from every failure path that
     * abandons a registration. This is what keeps the session to the registration
     * step rather than leaving it resting on the device.
     */
    public static void signOutQuietly() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Throwable ignored) {
            // Signing out is best effort. The session is not an EcoWatch
            // credential, so a failure here cannot affect access to anything.
        }
    }

    private static FirebaseAuthException unwrap(Task<AuthResult> task) {
        return task.getException() instanceof FirebaseAuthException
                ? (FirebaseAuthException) task.getException()
                : null;
    }

    private static String describeSignInFailure(@Nullable Exception error) {
        if (error == null) {
            return "Could not start your registration. Please try again.";
        }

        String code = error instanceof FirebaseAuthException
                ? ((FirebaseAuthException) error).getErrorCode()
                : "";

        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
            return "An account with this email already exists.";
        }
        if ("ERROR_INVALID_EMAIL".equals(code)) {
            return "Please enter a valid email address.";
        }
        if ("ERROR_WEAK_PASSWORD".equals(code)) {
            return "Please choose a stronger password.";
        }
        if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
            return "Email sign-in is disabled for this project. Please contact "
                    + "your administrator.";
        }
        if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
            return "Network error. Please check your connection and try again.";
        }

        return error.getMessage() != null
                ? error.getMessage()
                : "Could not start your registration. Please try again.";
    }

    private static String describeVerificationFailure(@Nullable Exception error) {
        if (error == null) {
            return "Could not send the verification email. Please try again.";
        }

        String code = error instanceof FirebaseAuthException
                ? ((FirebaseAuthException) error).getErrorCode()
                : "";

        if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
            return "Too many requests. Please wait a minute before asking for "
                    + "another email.";
        }
        if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
            return "Network error. Please check your connection and try again.";
        }

        return error.getMessage() != null
                ? error.getMessage()
                : "Could not send the verification email. Please try again.";
    }

    /** Runs on the main thread; the caller decides where to use the result. */
    public interface Callback {
        void onSuccess();

        /**
         * @param keepSession true when the resident is still mid-registration and
         *                    the session must survive so the email can be re-sent
         */
        void onError(String message, boolean keepSession);

        /** The email has not been opened yet, so there is nothing to report. */
        void onNotYetVerified();

        /** Verification is proven; this token is what the backend must confirm. */
        void onVerifiedToken(String idToken);
    }
}
