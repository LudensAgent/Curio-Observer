package dev.curio.observer.config;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObserverTextTest {
    @AfterEach void restoreAutomaticLanguage() { ObserverText.setLanguage("auto"); }

    @Test void switchesModLabelsAndFormatsArgumentsWithoutChangingMinecraftLanguage() {
        Language original = Language.getInstance();
        ObserverText.setLanguage("zh_cn");
        assertEquals("完成", ObserverText.text("curio-observer.config.done").getString());
        assertEquals("自动附身观察：开启", ObserverText.text("curio-observer.config.toggle",
                ObserverText.text("curio-observer.config.auto_observe"), ObserverText.text("curio-observer.config.on")).getString());
        assertEquals("Bot 数据：已连接 Alex", ObserverText.text("curio-observer.feed.connected", Text.literal("Alex")).getString());
        assertEquals("箱子", ObserverText.text("container.chest").getString());
        ObserverText.setLanguage("en_us");
        assertEquals("Done", ObserverText.text("curio-observer.config.done").getString());
        assertEquals("Auto observe: Off", ObserverText.text("curio-observer.config.toggle",
                ObserverText.text("curio-observer.config.auto_observe"), ObserverText.text("curio-observer.config.off")).getString());
        assertEquals("Chest", ObserverText.text("container.chest").getString());
        assertSame(original, Language.getInstance());
    }

    @Test void automaticAndUnknownTranslationsDelegateToMinecraft() {
        assertInstanceOf(TranslatableTextContent.class, ObserverText.text("curio-observer.config.title").getContent());
        ObserverText.setLanguage("en_us");
        assertInstanceOf(TranslatableTextContent.class, ObserverText.text("item.minecraft.diamond").getContent());
    }
}
