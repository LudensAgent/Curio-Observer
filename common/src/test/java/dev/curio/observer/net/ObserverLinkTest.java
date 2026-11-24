package dev.curio.observer.net;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import dev.curio.observer.state.BotState;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class ObserverLinkTest {
    private static final String HELLO = "event: hello\ndata: {\"protocol\":1,\"bot\":\"Curio\",\"mcVersion\":\"1.20.1\",\"agentVersion\":\"test\"}\n\n";

    @Test void acceptsConfiguredRemoteHttpAndHttpsButRejectsInvalidEndpoints() {
        assertEquals("example.com", ObserverLink.validateEndpoint(URI.create("https://example.com/observer")).getHost());
        assertDoesNotThrow(() -> ObserverLink.validateEndpoint(URI.create("http://127.0.0.1:3000/stream")));
        for (String url : new String[]{"file:///C:/stream", "http://user:password@localhost/", "http://localhost:0/", "http://localhost/#fragment"}) {
            assertThrows(IllegalArgumentException.class, () -> ObserverLink.validateEndpoint(URI.create(url)), url);
        }
    }

    @Test void retriesHttpFailureAndReplaysAFreshSnapshotAfterEof() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        var requests = new AtomicInteger();
        var closeFirstStream = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        server.createContext("/stream", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                String window = request == 2
                        ? "event: window_open\ndata: {\"windowId\":1,\"kind\":\"grindstone\",\"title\":\"砂轮\",\"slots\":[null,null,null]}\n\n"
                        : "";
                output.write((HELLO + "event: state\ndata: " + ObserverProtocolTest.stateJson() + "\n\n" + window).getBytes(StandardCharsets.UTF_8));
                output.flush();
                try { (request == 2 ? closeFirstStream : finish).await(); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            } finally { exchange.close(); }
        });
        server.start();
        try (var link = new ObserverLink("http://127.0.0.1:" + server.getAddress().getPort() + "/stream")) {
            link.connect();
            await(() -> link.snapshot().window() != null);
            assertTrue(link.snapshot().renderable());
            assertEquals("砂轮", link.snapshot().window().title());
            assertEquals(17, link.snapshot().health());
            assertTrue(requests.get() >= 2);
            closeFirstStream.countDown();
            await(() -> link.snapshot().connection() == BotState.Connection.DISCONNECTED);
            assertNull(link.snapshot().window());
            assertFalse(link.snapshot().hasState());
            await(() -> requests.get() >= 3 && link.snapshot().renderable());
            assertNull(link.snapshot().window(), "Reconnect must not retain a window absent from the new snapshot");
        } finally {
            closeFirstStream.countDown();
            finish.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test void aSilentOpenConnectionExpiresItsPrivateInventory() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        var finish = new CountDownLatch(1);
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write((HELLO + "event: state\ndata: " + ObserverProtocolTest.stateJson() + "\n\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
                try { finish.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            } finally { exchange.close(); }
        });
        server.start();
        try (var link = new ObserverLink(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/stream"), 500)) {
            link.connect();
            await(() -> link.snapshot().renderable());
            await(() -> !link.snapshot().hasState());
            assertFalse(link.snapshot().forPlayer("Curio"));
            assertNull(link.snapshot().cursor());
        } finally {
            finish.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for observer state");
    }
}
