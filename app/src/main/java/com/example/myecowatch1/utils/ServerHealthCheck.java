package com.example.myecowatch1.utils;

import android.content.Context;

import com.example.myecowatch1.data.remote.ServerConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Reaches the EcoWatch server's health endpoint and reports what came back.
 *
 * <p>Used by the server settings screen so an address can be confirmed before it
 * is saved, rather than discovered to be wrong by a login that times out after
 * thirty seconds.
 *
 * <p>Deliberately does not use Retrofit: the point is to test a candidate address,
 * and the shared client is built from the currently configured one. A plain
 * connection keeps the test independent of that, and of the auth token.
 */
public final class ServerHealthCheck {

    /** Generous, but far below the request timeouts a login would sit through. */
    private static final int TIMEOUT_MS = 6000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /** What a probe found, already phrased for display. */
    public static final class Result {
        private final boolean reachable;
        private final String summary;

        Result(boolean reachable, String summary) {
            this.reachable = reachable;
            this.summary = summary;
        }

        public boolean isReachable() {
            return reachable;
        }

        public String getSummary() {
            return summary;
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    private ServerHealthCheck() {
        // Utility class.
    }

    /**
     * Probes {@code hostAndPort}, for example {@code 192.168.1.11:5000}.
     *
     * <p>Always calls back on a background thread; the caller is responsible for
     * hopping back to the main thread.
     */
    public static void probe(Context context, String hostAndPort, Callback callback) {
        EXECUTOR.execute(() -> callback.onResult(probeBlocking(context, hostAndPort)));
    }

    private static Result probeBlocking(Context context, String hostAndPort) {
        String candidate = ServerConfig.editableAddress(context);
        if (hostAndPort != null && !hostAndPort.trim().isEmpty()) {
            candidate = hostAndPort.trim();
        }

        String url = "http://" + candidate + "/api/health";
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");

            int status = connection.getResponseCode();

            if (status == HttpURLConnection.HTTP_OK) {
                String body = readBody(connection);
                return new Result(true, "Connected. " + summarise(body));
            }
            return new Result(false, "Server answered with HTTP " + status);
        } catch (java.net.SocketTimeoutException error) {
            return new Result(false, "No response. Check the address and that the "
                    + "server is running, and that this phone is on the same network.");
        } catch (Exception error) {
            String message = error.getMessage();
            return new Result(false, "Could not reach the server"
                    + (message == null ? "" : ": " + message));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Picks out the fields that prove which server answered, when they are present. */
    private static String summarise(String body) {
        if (body == null) return "";
        if (body.contains("LOCAL_POSTGRES")) return "Running the local database.";
        if (body.contains("REMOTE_POSTGRES")) return "Running the hosted database.";
        return "";
    }

    private static String readBody(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
                // The health payload is small; this is only a safety bound.
                if (text.length() > 2000) break;
            }
            return text.toString();
        } catch (Exception error) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Finding the server without being told its address
    // ---------------------------------------------------------------------

    /** How many hosts to try at once. Enough to finish quickly, few enough to be polite. */
    private static final int SCAN_THREADS = 48;

    /**
     * Per-host timeout while scanning. Deliberately short: a host that has not
     * answered in this long is not the server, and waiting longer would make the
     * whole scan feel broken.
     */
    private static final int SCAN_TIMEOUT_MS = 400;

    /**
     * How long the whole scan may take before whatever was found so far is
     * reported. A /24 is 254 hosts; at this concurrency the usual case finishes in
     * a few seconds.
     */
    private static final long SCAN_BUDGET_MS = 20_000;

    /** Told every EcoWatch server found, as {@code host:port}. */
    public interface ScanCallback {
        void onScanFinished(List<String> found);
    }

    /**
     * Looks for an EcoWatch server on the same subnet as this phone.
     *
     * <p>Exists because the development machine's address is not knowable in
     * advance: it is assigned by whichever network the laptop happens to be on, so
     * it changes every time the laptop joins a different WiFi. Reading it off the
     * laptop is possible, but awkward when you are holding the phone.
     *
     * <p>Only the phone's own /24 is searched, and each host receives a single
     * read-only GET. Nothing is written and no host is contacted twice.
     *
     * <p>Always calls back, with an empty list when nothing was found. The caller
     * hops back to the main thread.
     */
    public static void scanLocalNetwork(int port, ScanCallback callback) {
        final List<String> found = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(SCAN_THREADS);
        final long deadline = System.currentTimeMillis() + SCAN_BUDGET_MS;

        final String selfIp = ownIpv4Address();

        for (int host = 1; host <= 254; host++) {
            final String candidate = selfIp == null ? null : subnetOf(selfIp, host);

            // Without a known local address there is no subnet to search, so
            // report nothing rather than probing a guessed range.
            if (candidate == null) break;
            if (candidate.equals(selfIp)) continue;
            if (System.currentTimeMillis() > deadline) break;

            pool.execute(() -> {
                if (isEcoWatchServer(candidate, port)) {
                    found.add(candidate + ":" + port);
                }
            });
        }

        pool.shutdown();
        try {
            // shutdown() does not interrupt, so wait with a ceiling and report
            // whatever has already been found.
            pool.awaitTermination(SCAN_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();

        List<String> result = new ArrayList<>(found);
        Collections.sort(result);
        callback.onScanFinished(result);
    }

    /** True only for a host that answers with this project's health payload. */
    private static boolean isEcoWatchServer(String host, int port) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://" + host + ":" + port + "/api/health")
                    .openConnection();
            connection.setConnectTimeout(SCAN_TIMEOUT_MS);
            connection.setReadTimeout(SCAN_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            // Port 5000 alone is not proof: anything could answer on it. The
            // health payload is what identifies an EcoWatch server.
            String body = readBody(connection);
            return body != null && body.contains("\"status\"") && body.contains("UP");
        } catch (Exception error) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** This phone's own non-loopback IPv4 address, or null when it has none. */
    private static String ownIpv4Address() {
        try {
            // getNetworkInterfaces() is an Enumeration rather than an Iterable.
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface nic = interfaces.nextElement();
                if (nic == null || !nic.isUp() || nic.isLoopback()) continue;

                // getInetAddresses() is an Enumeration too.
                java.util.Enumeration<java.net.InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                        String value = address.getHostAddress();
                        if (!value.startsWith("169.254.")) return value;
                    }
                }
            }
        } catch (Exception error) {
            // No usable interface, which the caller handles as "nothing to search".
        }
        return null;
    }

    /**
     * Builds a candidate address in the same /24 as this phone.
     *
     * <p>Limited to a /24 on purpose. A /16 would mean 65,000 probes, which is
     * neither fast nor acceptable on someone else's network. A /24 covers every
     * ordinary home, campus and hotspot setup, where the laptop and the phone sit
     * in the same subnet.
     */
    private static String subnetOf(String selfIp, int host) {
        String[] octets = selfIp.split("\\.");
        if (octets.length != 4) return null;
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + host;
    }
}
