package dev.curio.observer.config;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
        assertEquals("Bot 数据：已连接 Alex", ObserverText.text("curio-observer.feed.connected", Component.literal("Alex")).getString());
        assertEquals("箱子", ObserverText.text("container.chest").getString());
        ObserverText.setLanguage("en_us");
        assertEquals("Done", ObserverText.text("curio-observer.config.done").getString());
        assertEquals("Auto observe: Off", ObserverText.text("curio-observer.config.toggle",
                ObserverText.text("curio-observer.config.auto_observe"), ObserverText.text("curio-observer.config.off")).getString());
        assertEquals("Chest", ObserverText.text("container.chest").getString());
        assertSame(original, Language.getInstance());
    }

    @Test void automaticAndUnknownTranslationsDelegateToMinecraft() {
        assertInstanceOf(TranslatableContents.class, ObserverText.text("curio-observer.config.title").getContents());
        ObserverText.setLanguage("en_us");
        assertInstanceOf(TranslatableContents.class, ObserverText.text("item.minecraft.diamond").getContents());
    }
}
