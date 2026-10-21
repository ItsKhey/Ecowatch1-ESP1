package com.example.myecowatch1.data.remote;

/**
 * Result of asking the backend to prepare a password reset.
 *
 * <p>The message is deliberately identical whether or not the address is
 * registered, so this object can be shown to a resident without revealing whether
 * they have an account. The boolean is what the app uses to decide whether to ask
 * Firebase to send anything, and it is not displayed.
 */
public class PasswordResetPreparation {

    private final boolean canRequestFirebaseEmail;
    private final int retryAfterSeconds;
    private final String message;

    public PasswordResetPreparation(boolean canRequestFirebaseEmail,
                                    int retryAfterSeconds,
                                    String message) {
        this.canRequestFirebaseEmail = canRequestFirebaseEmail;
        this.retryAfterSeconds = retryAfterSeconds;
        this.message = message == null ? "" : message;
    }

    /** True only when Firebase has an identity for this address and may be asked to send. */
    public boolean canRequestFirebaseEmail() {
        return canRequestFirebaseEmail;
    }

    /** Seconds to wait before another attempt, when the backend is enforcing a cooldown. */
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getMessage() {
        return message;
    }
}
