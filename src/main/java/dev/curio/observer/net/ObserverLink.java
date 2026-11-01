package dev.curio.observer.net;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** A read-only SSE subscription. Network I/O and parsing never run on the Minecraft render thread. */
public final class ObserverLink implements AutoCloseable {
    private static final long STALE_TIMEOUT_MS = 45_000;
    private final URI endpoint;
    private final long staleTimeoutMillis;
    private final AtomicReference<BotState> snapshot = new AtomicReference<>(BotState.empty());
    private final AtomicReference<InputStream> activeBody = new AtomicReference<>();
    private final AtomicLong lastActivity = new AtomicLong();
    private final ScheduledExecutorService watchdog;
    private volatile boolean running;
    private boolean closed;
    private Thread worker;

    public ObserverLink(String endpoint) { this(validateEndpoint(URI.create(endpoint)), STALE_TIMEOUT_MS); }

    ObserverLink(URI endpoint, long staleTimeoutMillis) {
        this.endpoint = validateEndpoint(endpoint);
        this.staleTimeoutMillis = staleTimeoutMillis;
        watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Curio Observer heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Remote endpoints are explicitly configurable; redirects and URL-embedded credentials are not used. */
    public static URI validateEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.toString().length() > 2048
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getRawUserInfo() != null || endpoint.getRawFragment() != null
                || endpoint.getPort() == 0 || endpoint.getPort() > 65535 || endpoint.getPort() < -1) {
            throw new IllegalArgumentException("Observer URL must be HTTP or HTTPS without credentials or fragments");
        }
        return endpoint;
    }

    public BotState snapshot() { return snapshot.get(); }

    public synchronized void connect() {
        if (closed) throw new IllegalStateException("Observer link is closed");
        if (running) return;
        running = true;
        watchdog.scheduleAtFixedRate(() -> {
            if (running && activeBody.get() != null && System.currentTimeMillis() - lastActivity.get() > staleTimeoutMillis) {
                disconnected("Observer heartbeat timed out");
                closeBody();
            }
        }, Math.min(5000, staleTimeoutMillis), Math.min(5000, staleTimeoutMillis), TimeUnit.MILLISECONDS);
        worker = new Thread(this::run, "Curio Observer SSE");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        int retrySeconds = 1;
        HttpClient client = null;
        while (running) {
            snapshot.updateAndGet(state -> running ? state.withConnection(BotState.Connection.CONNECTING, "Connecting to observer feed") : state);
            long opened = System.currentTimeMillis();
            try {
                // Creating the selector can fail on some Windows hosts; keep that failure on this worker too.
                if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER).build();
                HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                        .header("Accept", "text/event-stream").header("Cache-Control", "no-cache").GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                InputStream body = response.body();
                lastActivity.set(System.currentTimeMillis());
                activeBody.set(body);
                try (body) {
                    if (!running) break;
                    if (response.statusCode() != 200) throw new IOException("Observer returned HTTP " + response.statusCode());
                    String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    if (!contentType.split(";", 2)[0].trim().equals("text/event-stream")) throw new IOException("Response is not an SSE stream");
                    SseParser.read(body, frame -> {
                        if (!running) return;
                        Protocol.Event event = Protocol.parse(frame.event(), frame.data());
                        if (event != null) snapshot.updateAndGet(state -> running ? state.apply(event, System.currentTimeMillis()) : state);
                    }, () -> lastActivity.set(System.currentTimeMillis()));
                    disconnected("Observer feed disconnected");
                } finally {
                    activeBody.compareAndSet(body, null);
                }
            } catch (InterruptedException exception) {
                if (!running) break;
                disconnected("Observer connection interrupted");
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof RuntimeException && !(exception instanceof IllegalArgumentException)) client = null;
                disconnected(exception instanceof IllegalArgumentException ? "Invalid observer data received" : "Observer connection failed");
            } finally {
                closeBody();
            }
            if (!running) break;
            if (System.currentTimeMillis() - opened >= 30_000) retrySeconds = 1;
            try { Thread.sleep(retrySeconds * 1000L); }
            catch (InterruptedException exception) { if (!running) break; }
            retrySeconds = Math.min(15, retrySeconds * 2);
        }
    }

    private void disconnected(String message) {
        snapshot.updateAndGet(state -> running && state.connection() != BotState.Connection.PROTOCOL_MISMATCH
                ? state.withConnection(BotState.Connection.DISCONNECTED, message) : state);
    }

    private void closeBody() {
        InputStream body = activeBody.getAndSet(null);
        if (body != null) {
            try { body.close(); } catch (IOException ignored) { /* The connection is already gone. */ }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        running = false;
        if (worker != null) worker.interrupt();
        watchdog.shutdownNow();
        snapshot.set(BotState.empty());
        // Closing a streaming HttpClient body can wait for a read. Keep cleanup off Minecraft's thread.
        Thread cleanup = new Thread(this::closeBody, "Curio Observer disconnect");
        cleanup.setDaemon(true);
        cleanup.start();
    }
}
