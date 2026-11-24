package dev.curio.observer.net;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import org.junit.jupiter.api.Test;

class ObserverProtocolTest {
    static String stateJson() {
        return "{\"health\":17,\"food\":18,\"xp\":{\"level\":2,\"progress\":0.4},\"armor\":[null,null,null,null],"
                + "\"hotbar\":[null,null,null,null,null,null,null,null,null],\"selected\":0,\"main\":["
                + "null,".repeat(26) + "null]}";
    }

    @Test void acceptsReferenceStateAndOptionalMaxHealth() {
        var state = (Protocol.State) Protocol.parse("state", stateJson());
        assertEquals(17, state.health());
        assertEquals(20, state.maxHealth());
        assertEquals(27, state.main().size());
        assertEquals(40, ((Protocol.State) Protocol.parse("state", stateJson().replace("\"health\":17", "\"health\":17,\"maxHealth\":40"))).maxHealth());
        assertNull(Protocol.parse("thought", "not parsed: optional unsupported event"));
    }

    @Test void preservesFullItemTagsAndValidatesJsonTypes() {
        var slot = (Protocol.Slot) Protocol.parse("slot", "{\"windowId\":2,\"slot\":0,\"item\":{\"id\":\"minecraft:enchanted_book\",\"nbt\":\"{StoredEnchantments:[{id:'minecraft:mending',lvl:1s}]}\"}}");
        assertEquals("{StoredEnchantments:[{id:'minecraft:mending',lvl:1s}]}", slot.item().nbt());
        assertEquals(1, slot.item().count());
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("slot", "{\"windowId\":1,\"slot\":0.5,\"item\":null}"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("slot", "{\"windowId\":1,\"slot\":256,\"item\":null}"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("state", stateJson().replace("\"food\":18", "\"food\":\"18\"")));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("state", stateJson().replace("\"armor\":[null,null,null,null]", "\"armor\":[null]")));
    }

    @Test void acceptsPartialWorkstationPropertiesAndFullTradeData() {
        assertNull(((Protocol.Furnace) Protocol.parse("furnace", "{\"windowId\":1,\"burnTime\":100}")).data().cookTimeTotal());
        var trade = (Protocol.Merchant) Protocol.parse("merchant", "{\"windowId\":3,\"offers\":[{\"input1\":{\"id\":\"minecraft:emerald\",\"count\":5},\"input2\":null,\"output\":{\"id\":\"minecraft:book\"},\"disabled\":false,\"uses\":2,\"maxUses\":12}],\"selected\":0,\"level\":2,\"experience\":15,\"regular\":true}");
        assertEquals(5, trade.data().offers().get(0).input1().count());
        assertEquals(0, trade.data().selected());
        assertEquals(15, trade.data().experience());
        assertThrows(UnsupportedOperationException.class, () -> trade.data().offers().clear());
    }

    @Test void boundsPayloadsAndKeepsUnknownContainersAsGeneric() {
        var window = (Protocol.WindowOpen) Protocol.parse("window_open", "{\"windowId\":4,\"kind\":\"custom:machine\",\"title\":\"Machine\",\"slots\":[null]}");
        assertEquals("generic", window.kind());
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("hello", " ".repeat(Protocol.MAX_JSON_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("hello", "[".repeat(33) + "]".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> new Protocol.ItemData("minecraft:book", 1, 0, 0, false, null, "x".repeat(65_537)));
    }
}
