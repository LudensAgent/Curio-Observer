package dev.curio.observer.config;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.text.Text;

/** A mod-only language override; Minecraft item names and the global language remain untouched. */
public final class ObserverText {
    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.of(
            "en_us", load("en_us"), "zh_cn", load("zh_cn"));
    private static String language = "auto";

    private ObserverText() {}

    /** Receives the normalized config value, or one of the settings screen's fixed choices. */
    public static void setLanguage(String value) { language = value; }

    public static Text text(String key, Object... arguments) {
        if (language.equals("auto")) return Text.translatable(key, arguments);
        Map<String, String> translations = TRANSLATIONS.get(language);
        String template = translations.get(key.startsWith("curio-observer.")
                ? key : "curio-observer.vanilla." + key);
        if (template == null) return Text.translatable(key, arguments);
        Object[] values = Arrays.stream(arguments).map(value -> value instanceof Text text ? text.getString() : value).toArray();
        return Text.literal(String.format(Locale.ROOT, template, values));
    }

    private static Map<String, String> load(String code) {
        var resource = ObserverText.class.getResourceAsStream("/assets/curio-observer/lang/" + code + ".json");
        if (resource == null) throw new IllegalStateException("Missing bundled observer language: " + code);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled observer language: " + code, exception);
        }
    }
}
