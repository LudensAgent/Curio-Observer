package dev.curio.observer.net;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SseParserTest {
    @Test void handlesUtf8BomCommentsCrlfAndMultilineData() throws IOException {
        var frames = read("\ufeff: ping\r\nevent: window_open\r\ndata: {\r\ndata: \"windowId\":1,\"kind\":\"grindstone\",\"title\":\"砂轮\",\"slots\":[]}\r\nid: ignored\r\n\r\n");
        assertEquals(1, frames.size());
        assertEquals("砂轮", ((Protocol.WindowOpen) Protocol.parse(frames.get(0).event(), frames.get(0).data())).title());
    }

    @Test void supportsCrOnlyAndDiscardsUnterminatedFrames() throws IOException {
        var frames = read("event: cursor\rdata: {\"item\":null}\r\revent: cursor\ndata: {\"item\":null}");
        assertEquals(1, frames.size());
        assertEquals("cursor", frames.get(0).event());
    }

    @Test void acceptsLargeSingleLineSnapshotsAndBoundsAggregateEvents() throws IOException {
        assertEquals(1, read("event: window_open\ndata: " + "x".repeat(100_000) + "\n\n").size());
        assertThrows(IOException.class, () -> read("data: " + "x".repeat(SseParser.MAX_LINE_LENGTH) + "\n\n"));
        assertThrows(IOException.class, () -> read(("data: " + "x".repeat(60_000) + "\n").repeat(5) + "\n"));
        assertThrows(IOException.class, () -> SseParser.read(new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28}), frame -> {}, () -> {}));
    }

    private List<SseParser.Frame> read(String data) throws IOException {
        var frames = new ArrayList<SseParser.Frame>();
        SseParser.read(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), frames::add, () -> {});
        return frames;
    }
}
