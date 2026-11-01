package dev.curio.observer.net;

import dev.curio.observer.model.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** A bounded SSE decoder: comments, CR/LF line endings and multiline data follow the SSE format. */
final class SseParser {
    static final int MAX_LINE_LENGTH = Protocol.MAX_JSON_LENGTH;
    record Frame(String event, String data) {}

    private SseParser() {}

    static void read(InputStream stream, Consumer<Frame> receiver, Runnable activity) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, decoder));
        StringBuilder line = new StringBuilder();
        StringBuilder data = new StringBuilder();
        String event = "message";
        boolean hasData = false;
        boolean skipLf = false;
        boolean first = true;
        int value;
        while ((value = reader.read()) != -1) {
            activity.run();
            char c = (char) value;
            if (first) { first = false; if (c == '\ufeff') continue; }
            if (skipLf && c == '\n') { skipLf = false; continue; }
            skipLf = false;
            if (c != '\r' && c != '\n') {
                if (line.length() >= MAX_LINE_LENGTH) throw new IOException("Observer SSE line exceeds the size limit");
                line.append(c);
                continue;
            }
            skipLf = c == '\r';
            if (line.isEmpty()) {
                if (hasData) receiver.accept(new Frame(event, data.substring(0, data.length() - 1)));
                data.setLength(0);
                hasData = false;
                event = "message";
                continue;
            }
            if (line.charAt(0) != ':') {
                int colon = line.indexOf(":");
                String field = colon < 0 ? line.toString() : line.substring(0, colon);
                String content = colon < 0 ? "" : line.substring(colon + 1);
                if (content.startsWith(" ")) content = content.substring(1);
                if (field.equals("event")) {
                    if (content.length() > 64) throw new IOException("Observer SSE event name is too long");
                    event = content;
                } else if (field.equals("data")) {
                    if (data.length() + content.length() + 1 > Protocol.MAX_JSON_LENGTH) throw new IOException("Observer SSE event exceeds the size limit");
                    data.append(content).append('\n');
                    hasData = true;
                }
            }
            line.setLength(0);
        }
        // Incomplete frames at EOF are discarded rather than replaying a partially written snapshot.
    }
}
