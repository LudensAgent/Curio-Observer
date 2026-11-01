package dev.curio.observer.rcon;

import dev.curio.observer.model.Protocol;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal RCON client that polls {@code data get entity <name>} for the spectated player. One daemon
 * owns the socket; the render thread only reads the immutable atomic snapshot. RCON needs the server to
 * enable {@code enable-rcon=true} and a password; this is a server-side channel, not a game packet.
 */
public final class RconLink implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("curio-observer");
    private static final int AUTH = 3;
    private static final int AUTH_RESPONSE = 2;
    private static final int EXECCOMMAND = 2;
    private static final int RESPONSE_VALUE = 0;
    /** Extra time after the poll interval before a snapshot is considered stale. */
    private static final long SNAPSHOT_GRACE_MS = 5_000;

    private final String host;
    private final int port;
    private final String password;
    private final int pollMillis;
    private final Supplier<String> targetName;
    private final AtomicReference<RconSnapshot> snapshot = new AtomicReference<>(RconSnapshot.empty());
    private final Object lock = new Object();
    private volatile boolean running;
    private volatile boolean closed;
    private Thread worker;
    private int requestId = 1;

    public RconLink(String host, int port, String password, int pollMillis, Supplier<String> targetName) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.pollMillis = Math.max(250, pollMillis);
        this.targetName = targetName;
    }

    public RconSnapshot snapshot() { return snapshot.get(); }

    public void start() {
        synchronized (lock) {
            if (running || closed) return;
            running = true;
            worker = new Thread(this::run, "Curio Observer RCON");
            worker.setDaemon(true);
            worker.start();
        }
    }

    @Override
    public void close() {
        synchronized (lock) { closed = true; running = false; }
        Thread thread = worker;
        if (thread != null) thread.interrupt();
        snapshot.set(RconSnapshot.empty());
    }

    private void run() {
        while (running) {
            String name = targetName.get();
            if (name == null || name.isBlank()) { sleep(pollMillis); continue; }
            try (Socket socket = new Socket(host, port)) {
                socket.setSoTimeout(4000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                if (!auth(in, out)) {
                    LOGGER.warn("RCON authentication to {}:{} failed; check the password", host, port);
                    sleep(3000);
                    continue;
                }
                LOGGER.info("RCON connected to {}:{}", host, port);
                while (running) {
                    String current = targetName.get();
                    if (current == null || current.isBlank()) { sleep(pollMillis); continue; }
                    RconSnapshot next = poll(in, out, current);
                    if (next != null) snapshot.set(next);
                    sleep(pollMillis);
                }
            } catch (IOException | RuntimeException exception) {
                if (running) LOGGER.debug("RCON connection to {}:{} lost: {}", host, port, exception.getMessage());
            }
            if (running) sleep(1000);
        }
    }

    private RconSnapshot poll(DataInputStream in, DataOutputStream out, String name) throws IOException {
        String response = command(in, out, "data get entity " + name);
        return response == null ? null : parse(name, response, pollMillis);
    }

    private String command(DataInputStream in, DataOutputStream out, String command) throws IOException {
        int id = nextRequestId();
        send(out, id, EXECCOMMAND, command);
        return receive(in, id);
    }

    private boolean auth(DataInputStream in, DataOutputStream out) throws IOException {
        int id = nextRequestId();
        send(out, id, AUTH, password);
        while (true) {
            int length = readIntLE(in);
            int rid = readIntLE(in);
            int type = readIntLE(in);
            byte[] payload = new byte[Math.max(0, length - 8)];
            in.readFully(payload);
            if (type == AUTH_RESPONSE) return rid != -1;
        }
    }

    private String receive(DataInputStream in, int expectedId) throws IOException {
        StringBuilder response = new StringBuilder();
        while (true) {
            int length = readIntLE(in);
            int rid = readIntLE(in);
            int type = readIntLE(in);
            byte[] payload = new byte[Math.max(0, length - 10)];
            in.readFully(payload);
            if (type != RESPONSE_VALUE || rid != expectedId) return response.toString();
            String chunk = new String(payload, StandardCharsets.UTF_8);
            response.append(chunk);
            // Minecraft splits responses longer than 4096 characters into several same-id packets and
            // sends no terminator. A short chunk ends the sequence; an exactly-4096-char final chunk is
            // detected once the SNBT compound it carries is complete.
            if (chunk.length() < 4096 || completeCompound(response.toString())) return response.toString();
        }
    }

    private static boolean completeCompound(String text) {
        int brace = text.indexOf('{');
        if (brace < 0) return false;
        try {
            NbtHelper.fromNbtProviderString(text.substring(brace));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int nextRequestId() {
        int id = requestId++;
        if (requestId < 0) requestId = 1;
        return id;
    }

    private static void send(DataOutputStream out, int id, int type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
        writeIntLE(out, bytes.length + 10);
        writeIntLE(out, id);
        writeIntLE(out, type);
        out.write(bytes);
        out.writeByte(0);
        out.writeByte(0);
        out.flush();
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.writeInt(Integer.reverseBytes(value));
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        return Integer.reverseBytes(in.readInt());
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    /** Parses the {@code data get entity <name>} feedback: a message prefix followed by the SNBT compound. */
    private static RconSnapshot parse(String name, String response, long pollMillis) {
        int brace = response.indexOf('{');
        if (brace < 0) return null;
        NbtCompound root;
        try {
            root = NbtHelper.fromNbtProviderString(response.substring(brace));
        } catch (Exception exception) {
            LOGGER.debug("Cannot parse RCON entity data: {}", exception.getMessage());
            return null;
        }

        List<Protocol.ItemData> hotbar = new ArrayList<>(Protocol.emptySlots(9));
        List<Protocol.ItemData> main = new ArrayList<>(Protocol.emptySlots(27));
        Protocol.ItemData offhand = null, head = null, chest = null, legs = null, feet = null;
        NbtList inventory = root.getList("Inventory", 10);
        for (int i = 0; i < inventory.size(); i++) {
            NbtCompound item = inventory.getCompound(i);
            int slot = item.getByte("Slot");
            Protocol.ItemData data = itemData(item);
            if (slot >= 0 && slot < 9) hotbar.set(slot, data);
            else if (slot >= 9 && slot < 36) main.set(slot - 9, data);
            else if (slot == 100) feet = data;
            else if (slot == 101) legs = data;
            else if (slot == 102) chest = data;
            else if (slot == 103) head = data;
            else if (slot == -106) offhand = data;
        }

        int food = MathHelper.clamp(root.getInt("foodLevel"), 0, 20);
        float saturation = MathHelper.clamp(root.getFloat("foodSaturationLevel"), 0.0F, 20.0F);
        int xpLevel = Math.max(0, root.getInt("XpLevel"));
        float xpProgress = MathHelper.clamp(root.getFloat("XpProgress"), 0.0F, 1.0F);
        int selected = MathHelper.clamp(root.getInt("SelectedItemSlot"), 0, 8);
        return new RconSnapshot(name, System.currentTimeMillis(), Arrays.asList(head, chest, legs, feet),
                hotbar, offhand, main, selected, food, saturation, xpLevel, xpProgress, pollMillis + SNAPSHOT_GRACE_MS);
    }

    private static Protocol.ItemData itemData(NbtCompound item) {
        String id = item.getString("id");
        if (id == null || id.isBlank() || id.length() > 256) return null;
        int count = Math.max(1, item.getByte("Count"));
        NbtCompound tag = item.getCompound("tag");
        int damage = Math.max(0, tag.getInt("Damage"));
        boolean enchanted = tag.contains("Enchantments") && !tag.getList("Enchantments", 10).isEmpty();
        String name = tag.contains("display") ? plainText(tag.getCompound("display").getString("Name")) : null;
        try {
            return new Protocol.ItemData(id, count, damage, 0, enchanted, name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String plainText(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Text text = Text.Serializer.fromJson(json);
            return text == null ? json : text.getString();
        } catch (RuntimeException exception) {
            return json;
        }
    }
}
