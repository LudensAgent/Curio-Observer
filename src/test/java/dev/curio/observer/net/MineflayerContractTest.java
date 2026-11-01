package dev.curio.observer.net;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import dev.curio.observer.state.ItemCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Replays events captured from the bundled JavaScript bridge's real HTTP SSE output. */
class MineflayerContractTest {
    @BeforeAll static void bootstrapRegistries() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @Test void bridgeEventsProduceUsableGrindstoneFurnaceAndTradingHudState() throws Exception {
        var fixture = JsonParser.parseString(Files.readString(Path.of(
                "integrations/mineflayer/fixtures/generated-events.json"))).getAsJsonArray();
        var observed = new HashSet<String>();
        var snapshot = BotState.empty();
        long now = 1000;
        for (var frame : fixture) {
            var envelope = frame.getAsJsonObject();
            String eventName = envelope.get("event").getAsString();
            var event = Protocol.parse(eventName, envelope.get("data").toString());
            assertNotNull(event, "The bundled bridge emitted an unsupported event: " + eventName);
            snapshot = snapshot.apply(event, now += 50);
            if (event instanceof Protocol.State) {
                assertTrue(snapshot.renderable());
                assertTrue(snapshot.forPlayer("Curio"));
                assertFalse(snapshot.forPlayer("OtherPlayer"));
                assertEquals(18, snapshot.food());
                assertEquals(12, snapshot.xp().level());
                observed.add("state");
            } else if (event instanceof Protocol.WindowOpen opened && opened.kind().equals("grindstone")) {
                var sword = ItemCodec.stack(snapshot.window().slots().get(0));
                assertTrue(sword.isOf(Items.DIAMOND_SWORD));
                assertEquals(10, sword.getDamage());
                var enchantment = sword.getNbt().getList("Enchantments", 10).getCompound(0);
                assertEquals("minecraft:sharpness", enchantment.getString("id"));
                assertEquals(3, enchantment.getShort("lvl"));
                observed.add("grindstone_input");
            } else if (event instanceof Protocol.Cursor cursor && cursor.item() != null) {
                var result = ItemCodec.stack(snapshot.cursor());
                assertEquals(2, result.getDamage());
                assertFalse(result.hasEnchantments());
                assertEquals(snapshot.window().slots().get(2), snapshot.cursor());
                observed.add("grindstone_result");
            } else if (event instanceof Protocol.Furnace) {
                assertEquals(40, snapshot.window().furnace().data().cookTime());
                assertEquals(200, snapshot.window().furnace().data().cookTimeTotal());
                observed.add("furnace");
            } else if (event instanceof Protocol.Merchant merchant && merchant.data().selected() != null) {
                var trades = snapshot.window().merchant();
                assertEquals(0, trades.selected());
                assertEquals(13, trades.offers().get(0).input1().count());
                assertEquals(25, trades.experience());
                var book = ItemCodec.stack(trades.offers().get(0).output());
                assertTrue(book.isOf(Items.ENCHANTED_BOOK));
                var enchantment = book.getNbt().getList("StoredEnchantments", 10).getCompound(0);
                assertEquals("minecraft:mending", enchantment.getString("id"));
                assertEquals(1, enchantment.getShort("lvl"));
                assertFalse(book.getNbt().contains("Enchantments"), "Real trade tags must not acquire synthetic glint enchantments");
                observed.add("merchant");
            } else if (event instanceof Protocol.WindowClose) {
                assertFalse(snapshot.window().open());
                assertNull(snapshot.cursor());
            }
        }
        assertEquals(Set.of("state", "grindstone_input", "grindstone_result", "furnace", "merchant"), observed);
        assertEquals("offline", snapshot.status());
        assertFalse(snapshot.renderable());
        assertFalse(snapshot.forPlayer("Curio"));
        assertNull(snapshot.window());
        assertNull(snapshot.cursor());
    }
}
