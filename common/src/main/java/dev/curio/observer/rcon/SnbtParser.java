package dev.curio.observer.rcon;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, version-independent SNBT and Minecraft text-component parser for RCON responses.
 * Handles both pre-1.20.5 NBT ({@code tag}) and 1.20.5+ component SNBT ({@code components}).
 */
public final class SnbtParser {
    private final String input;
    private int pos;

    private SnbtParser(String input) {
        this.input = input;
    }

    public static Map<String, Object> parseCompound(String snbt) {
        SnbtParser parser = new SnbtParser(snbt);
        parser.skipWs();
        Map<String, Object> result = parser.readCompound();
        parser.skipWs();
        if (parser.pos != parser.input.length()) {
            throw new IllegalArgumentException("Trailing SNBT data at index " + parser.pos);
        }
        return result;
    }

    private void skipWs() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= input.length()) throw new IllegalArgumentException("Unexpected end of SNBT");
        return input.charAt(pos);
    }

    private Map<String, Object> readCompound() {
        if (peek() != '{') throw new IllegalArgumentException("Expected '{' at " + pos);
        pos++;
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = readKey();
            skipWs();
            if (peek() != ':') throw new IllegalArgumentException("Expected ':' at " + pos);
            pos++;
            skipWs();
            Object value = readValue();
            map.put(key, value);
            skipWs();
            char next = peek();
            if (next == '}') {
                pos++;
                return map;
            }
            if (next == ',') {
                pos++;
                skipWs();
                if (pos < input.length() && input.charAt(pos) == '}') {
                    pos++;
                    return map;
                }
                continue;
            }
            throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
        }
    }

    private List<Object> readList() {
        if (peek() != '[') throw new IllegalArgumentException("Expected '[' at " + pos);
        pos++;
        List<Object> list = new ArrayList<>();
        skipWs();
        // Skip typed array prefix like B; I; L;
        if (pos + 1 < input.length() && (input.charAt(pos) == 'B' || input.charAt(pos) == 'I' || input.charAt(pos) == 'L')
                && input.charAt(pos + 1) == ';') {
            pos += 2;
            skipWs();
        }
        if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char next = peek();
            if (next == ']') {
                pos++;
                return list;
            }
            if (next == ',') {
                pos++;
                skipWs();
                if (pos < input.length() && input.charAt(pos) == ']') {
                    pos++;
                    return list;
                }
                continue;
            }
            throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
        }
    }

    private Object readValue() {
        char c = peek();
        if (c == '{') return readCompound();
        if (c == '[') return readList();
        if (c == '"' || c == '\'') return readQuotedString();
        String token = readUnquotedToken();
        if (token.isEmpty()) throw new IllegalArgumentException("Empty SNBT value at " + pos);
        if ("true".equalsIgnoreCase(token)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(token)) return Boolean.FALSE;
        Number number = tryParseNumber(token);
        return number != null ? number : token;
    }

    private String readKey() {
        char c = peek();
        if (c == '"' || c == '\'') return readQuotedString();
        String key = readUnquotedToken();
        if (key.isEmpty()) throw new IllegalArgumentException("Empty SNBT key at " + pos);
        return key;
    }

    private String readQuotedString() {
        char quote = peek();
        pos++;
        StringBuilder out = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == '\\') {
                if (pos >= input.length()) throw new IllegalArgumentException("Unterminated escape in SNBT string");
                char esc = input.charAt(pos++);
                switch (esc) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '\\', '"', '\'' -> out.append(esc);
                    default -> out.append(esc);
                }
            } else if (c == quote) {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated SNBT string");
    }

    private String readUnquotedToken() {
        int start = pos;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '+') {
                pos++;
            } else {
                break;
            }
        }
        return input.substring(start, pos);
    }

    private static Number tryParseNumber(String token) {
        int len = token.length();
        char last = token.charAt(len - 1);
        try {
            if (len > 1 && (last == 'b' || last == 'B')) return Byte.parseByte(token.substring(0, len - 1));
            if (len > 1 && (last == 's' || last == 'S')) return Short.parseShort(token.substring(0, len - 1));
            if (len > 1 && (last == 'l' || last == 'L')) return Long.parseLong(token.substring(0, len - 1));
            if (len > 1 && (last == 'f' || last == 'F')) {
                float value = Float.parseFloat(token.substring(0, len - 1));
                return Float.isFinite(value) ? value : null;
            }
            if (len > 1 && (last == 'd' || last == 'D')) {
                double value = Double.parseDouble(token.substring(0, len - 1));
                return Double.isFinite(value) ? value : null;
            }
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                double value = Double.parseDouble(token);
                return Double.isFinite(value) ? value : null;
            }
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asCompound(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    public static String asString(Object value) {
        return value instanceof String s ? s : "";
    }

    public static int asInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof Boolean b) return b ? 1 : 0;
        return 0;
    }

    public static float asFloat(Object value) {
        if (value instanceof Number n) return n.floatValue();
        return 0.0F;
    }

    public static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return false;
    }

    /** Extracts plain text from either a JSON text component string or an SNBT text component value (1.21.5+). */
    public static String extractPlainText(Object value) {
        if (value == null) return null;
        if (value instanceof String str) {
            if (str.isBlank()) return null;
            String trimmed = str.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
                try {
                    String parsed = extractJsonText(JsonParser.parseString(trimmed));
                    return parsed.isEmpty() ? str : parsed;
                } catch (RuntimeException ignored) {
                    return str;
                }
            }
            return str;
        }
        String extracted = extractSnbtText(value);
        return extracted.isEmpty() ? null : extracted;
    }

    private static String extractJsonText(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        StringBuilder out = new StringBuilder();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) out.append(extractJsonText(child));
            return out.toString();
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) out.append(obj.get("text").getAsString());
            else if (obj.has("translate") && obj.get("translate").isJsonPrimitive()) out.append(obj.get("translate").getAsString());
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                for (JsonElement child : obj.getAsJsonArray("extra")) out.append(extractJsonText(child));
            }
        }
        return out.toString();
    }

    private static String extractSnbtText(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        StringBuilder out = new StringBuilder();
        if (value instanceof List<?> list) {
            for (Object child : list) out.append(extractSnbtText(child));
            return out.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String s) out.append(s);
            else if (map.get("translate") instanceof String s) out.append(s);
            Object extra = map.get("extra");
            if (extra instanceof List<?> list) {
                for (Object child : list) out.append(extractSnbtText(child));
            }
        }
        return out.toString();
    }
}
