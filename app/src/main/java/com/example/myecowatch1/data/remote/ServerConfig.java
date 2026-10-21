package com.example.myecowatch1.data.remote;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myecowatch1.BuildConfig;

/**
 * Where the app looks for the EcoWatch API.
 *
 * <p>The address is resolved from two places, in this order:
 *
 * <ol>
 *   <li>an override the user typed on the login screen, stored on the device</li>
 *   <li>the value compiled in at build time by Gradle (see app/build.gradle.kts)</li>
 * </ol>
 *
 * <p>The compiled value is the normal case and needs no setup. The override exists
 * because the address is not always known ahead of time: this project's server
 * runs on a laptop whose address is handed out by DHCP and has changed repeatedly.
 * Retyping one value on the phone is far quicker than rebuilding and reinstalling
 * an app, which is the difference between a working demonstration and a
 * demonstration that stops working.
 *
 * <p>Normalisation is deliberately forgiving, because someone typing an address
 * in a hurry will leave off the scheme, the port, or the /api path. All of these
 * resolve to the same server:
 *
 * <pre>
 *   192.168.1.11:5000
 *   http://192.168.1.11:5000
 *   http://192.168.1.11:5000/
 *   http://192.168.1.11:5000/api
 * </pre>
 */
public final class ServerConfig {

    private static final String PREFS_NAME = "ecowatch_server";
    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String KEY_SERVER_ORIGIN = "server_origin";

    /** Port the EcoWatch server listens on unless told otherwise. */
    private static final int DEFAULT_PORT = 5000;

    /**
     * How a new account proves it owns its email address: {@code OTP} for the
     * local 4-digit code flow, or {@code EMAIL_LINK} for the Firebase link flow.
     * Compiled in by Gradle; see app/build.gradle.kts.
     *
     * <p>Deliberately a build-time constant rather than a stored setting: the
     * verification transport is a property of the build, not something to change
     * from a login screen.
     */
    public static final boolean USES_LOCAL_OTP_REGISTRATION =
            !"EMAIL_LINK".equals(BuildConfig.REGISTRATION_METHOD);

    private ServerConfig() {
        // Utility class.
    }

    /** API root, always ending in a slash because Retrofit requires it. */
    public static String apiBaseUrl(Context context) {
        String stored = read(context, KEY_API_BASE_URL);
        return stored != null ? stored : BuildConfig.API_BASE_URL;
    }

    /** The same server with the {@code /api} path removed and a trailing slash. */
    public static String serverOrigin(Context context) {
        String stored = read(context, KEY_SERVER_ORIGIN);
        return stored != null ? stored : BuildConfig.SERVER_ORIGIN;
    }

    /** True when the user has pointed the app somewhere other than the build default. */
    public static boolean hasOverride(Context context) {
        return read(context, KEY_API_BASE_URL) != null;
    }

    /**
     * The address to show in the editor: the host and port, without the scheme or
     * the /api path, because that is the only part anyone actually has to know.
     */
    public static String editableAddress(Context context) {
        String origin = serverOrigin(context);
        String hostAndPort = origin.replaceFirst("^https?://", "");
        while (hostAndPort.endsWith("/")) {
            hostAndPort = hostAndPort.substring(0, hostAndPort.length() - 1);
        }
        return hostAndPort;
    }

    /**
     * Stores a user supplied address.
     *
     * @return null on success, or a message explaining what was wrong.
     */
    public static String setOverride(Context context, String rawInput) {
        String[] normalised = normalise(rawInput);
        if (normalised == null) {
            return "Enter an address like 192.168.1.11:5000";
        }

        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putString(KEY_API_BASE_URL, normalised[0])
                .putString(KEY_SERVER_ORIGIN, normalised[1])
                .apply();
        return null;
    }

    /** Drops the override, returning the app to the address compiled into the build. */
    public static void clearOverride(Context context) {
        prefs(context).edit()
                .remove(KEY_API_BASE_URL)
                .remove(KEY_SERVER_ORIGIN)
                .apply();
    }

    /**
     * The port in a {@code host:port} value, or the default when it is absent.
     *
     * <p>Used when scanning, so a server found on a non-default port is still
     * found. A malformed value falls back rather than throwing, because this runs
     * on a background thread where an exception would be invisible.
     */
    public static int portOf(String hostAndPort) {
        if (hostAndPort == null) {
            return DEFAULT_PORT;
        }
        int colon = hostAndPort.lastIndexOf(':');
        if (colon == -1 || colon == hostAndPort.length() - 1) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(hostAndPort.substring(colon + 1).trim());
            return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
        } catch (NumberFormatException error) {
            return DEFAULT_PORT;
        }
    }

    /**
     * Expands a stored image reference into something loadable.
     *
     * <p>The database holds site relative paths such as
     * {@code /uploads/ecowatch/reports/abc.jpg}, which resolve against whichever
     * server is configured. A value that is already absolute is returned
     * untouched, so a row still pointing at the hosted CDN keeps rendering.
     */
    public static String absoluteUrl(Context context, String storedPath) {
        if (storedPath == null) return null;
        String value = storedPath.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("/")) value = value.substring(1);
        return serverOrigin(context) + value;
    }

    /**
     * Turns user input into the API base and the server origin.
     *
     * @return {@code {apiBaseUrl, serverOrigin}}, or null when the input names no
     *         host.
     */
    private static String[] normalise(String rawInput) {
        if (rawInput == null) return null;

        String value = rawInput.trim();
        if (value.isEmpty()) return null;

        // Default to http: the local server is plain HTTP, and guessing https
        // would fail against it.
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }

        // Only the scheme, host and port are meaningful here. Anything after the
        // authority is dropped, so a pasted full API URL still works.
        String authority = value.substring(value.indexOf("://") + 3);
        int pathStart = authority.indexOf('/');
        if (pathStart != -1) {
            authority = authority.substring(0, pathStart);
        }
        if (authority.isEmpty()) return null;

        // Add the port when it was left off, which is the most common omission.
        boolean hasPort = authority.indexOf(':') > 0;
        String hostPort = hasPort ? authority : authority + ":" + DEFAULT_PORT;

        return new String[]{
                "http://" + hostPort + "/api/",
                "http://" + hostPort + "/"
        };
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String read(Context context, String key) {
        return prefs(context).getString(key, null);
    }
}
