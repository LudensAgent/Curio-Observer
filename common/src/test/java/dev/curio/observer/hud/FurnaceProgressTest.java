package dev.curio.observer.hud;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FurnaceProgressTest {
    private final Protocol.ItemData input = new Protocol.ItemData("minecraft:raw_iron", 3, 0, 0, false, null);
    private final Protocol.Hello hello = new Protocol.Hello(1, "Curio", "1.20.1", "test");

    private BotState open(Protocol.FurnaceData data) {
        return BotState.empty().apply(hello, 1).apply(new Protocol.WindowOpen(3, "furnace", "Furnace", Arrays.asList(input, null, null), data), 100);
    }

    private BotState advance(int burn, int cook, int total) {
        return open(new Protocol.FurnaceData(burn + 2, 1600, cook - 2, total))
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(burn, 1600, cook, total)), 200);
    }

    @Test void usesActualRecipeAndFuelDurationsForDifferentFurnacesAndMaterials() {
        FurnaceProgress.Frame standard = FurnaceProgress.sample(open(new Protocol.FurnaceData(800, 1600, 40, 200)).window(), 100);
        FurnaceProgress.Frame fast = FurnaceProgress.sample(open(new Protocol.FurnaceData(50, 100, 40, 100)).window(), 100);
        FurnaceProgress.Frame custom = FurnaceProgress.sample(open(new Protocol.FurnaceData(50, 100, 40, 400)).window(), 100);
        assertEquals(.5f, standard.fuelFraction());
        assertEquals(.5f, fast.fuelFraction());
        assertEquals(.2f, standard.cookFraction());
        assertEquals(.4f, fast.cookFraction());
        assertEquals(.1f, custom.cookFraction());
        assertEquals(60, fast.remainingCookTicks());
    }

    @Test void unknownPropertiesStayEmptyAndZeroTotalsNeverDivideByZero() {
        assertEquals(FurnaceProgress.Frame.EMPTY, FurnaceProgress.sample(open(null).window(), 150));
        FurnaceProgress.Frame unknown = FurnaceProgress.sample(open(new Protocol.FurnaceData(null, null, null, null)).window(), 150);
        assertEquals(FurnaceProgress.Frame.EMPTY, unknown);
        FurnaceProgress.Frame zero = FurnaceProgress.sample(open(new Protocol.FurnaceData(10, 0, 10, 0)).window(), 150);
        assertEquals(0, zero.fuelFraction());
        assertEquals(0, zero.cookFraction());
        assertFalse(zero.cookKnown());
        assertFalse(zero.cooking());
    }

    @Test void smoothsOnlyObservedForwardMotionAndStopsAfterABoundedGap() {
        BotState advancing = advance(100, 40, 200);
        FurnaceProgress.Frame smooth = FurnaceProgress.sample(advancing.window(), 300);
        assertEquals(42f / 200, smooth.cookFraction());
        assertEquals(98f / 1600, smooth.fuelFraction());
        assertTrue(smooth.cooking());
        FurnaceProgress.Frame delayed = FurnaceProgress.sample(advancing.window(), 2000);
        assertEquals(45f / 200, delayed.cookFraction());
        assertFalse(delayed.cooking());
        BotState fuelOnly = advancing.apply(new Protocol.Furnace(3, new Protocol.FurnaceData(98, 1600, 40, 200)), 300);
        assertEquals(44f / 200, FurnaceProgress.sample(fuelOnly.window(), 400).cookFraction());
        assertTrue(FurnaceProgress.sample(fuelOnly.window(), 400).cooking());
        assertEquals(45f / 200, FurnaceProgress.sample(fuelOnly.window(), 500).cookFraction());
        assertFalse(FurnaceProgress.sample(fuelOnly.window(), 500).cooking());
    }

    @Test void independentPropertyPacketsDoNotResetOrExtendCookPrediction() {
        BotState advancing = advance(100, 40, 200)
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(99, 1600, 40, 200)), 250)
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(99, 1601, 40, 200)), 251);
        FurnaceProgress.Frame smooth = FurnaceProgress.sample(advancing.window(), 300);
        assertEquals(42f / 200, smooth.cookFraction());
        assertTrue(smooth.cooking());
        BotState stalled = advancing.apply(new Protocol.Furnace(3, new Protocol.FurnaceData(94, 1601, 40, 200)), 500);
        FurnaceProgress.Frame stopped = FurnaceProgress.sample(stalled.window(), 550);
        assertEquals(45f / 200, stopped.cookFraction());
        assertFalse(stopped.cooking());
        BotState blocked = advancing.apply(new Protocol.Slot(3, 2, new Protocol.ItemData("minecraft:iron_ingot", 64, 0, 0, false, null)), 260)
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(98, 1600, 40, 200)), 300);
        assertFalse(FurnaceProgress.sample(blocked.window(), 350).cooking());
        assertEquals(.2f, FurnaceProgress.sample(blocked.window(), 350).cookFraction());
    }

    @Test void cookOnlySnapshotsCannotExtendTheObservedFuelLifetime() {
        BotState state = open(new Protocol.FurnaceData(4, 100, 10, 200))
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(4, 100, 11, 200)), 200)
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(4, 101, 12, 200)), 250);
        FurnaceProgress.Frame burning = FurnaceProgress.sample(state.window(), 250);
        assertEquals(1f / 101, burning.fuelFraction());
        FurnaceProgress.Frame burntOut = FurnaceProgress.sample(state.window(), 350);
        assertEquals(0, burntOut.fuelFraction());
        assertFalse(burntOut.burning());
        assertFalse(burntOut.cooking());
    }

    @Test void fuelSnapshotsCannotRestoreAnInvalidatedMaterialProgress() {
        BotState changed = advance(100, 40, 200)
                .apply(new Protocol.Slot(3, 0, new Protocol.ItemData("minecraft:raw_gold", 3, 0, 0, false, null)), 220)
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(99, 1600, 40, 200)), 250)
                .apply(new Protocol.Furnace(3, new Protocol.FurnaceData(99, 1601, 40, 200)), 251);
        assertFalse(FurnaceProgress.sample(changed.window(), 300).cookKnown());
        BotState reset = changed.apply(new Protocol.Furnace(3, new Protocol.FurnaceData(98, 1601, 0, 200)), 300);
        assertEquals(0, FurnaceProgress.sample(reset.window(), 350).cookFraction());
        assertTrue(FurnaceProgress.sample(reset.window(), 350).cookKnown());
        assertFalse(FurnaceProgress.sample(reset.window(), 350).cooking());
        BotState cooking = reset.apply(new Protocol.Furnace(3, new Protocol.FurnaceData(97, 1601, 1, 200)), 350);
        assertTrue(FurnaceProgress.sample(cooking.window(), 375).cooking());
    }

    @Test void doesNotPredictRefuelingOrAnotherCompletedRecipe() {
        FurnaceProgress.Frame noFuel = FurnaceProgress.sample(advance(1, 40, 200).window(), 300);
        assertEquals(41f / 200, noFuel.cookFraction());
        assertEquals(0, noFuel.fuelFraction());
        assertFalse(noFuel.burning());
        assertFalse(noFuel.cooking());
        FurnaceProgress.Frame finished = FurnaceProgress.sample(advance(100, 199, 200).window(), 300);
        assertEquals(1, finished.cookFraction());
        assertEquals(0, finished.remainingCookTicks());
        assertFalse(finished.cooking());
    }

    @Test void materialRemovalBlockedOutputAndClockRollbackCannotInventProgress() {
        BotState advancing = advance(100, 40, 200);
        BotState empty = advancing.apply(new Protocol.Slot(3, 0, null), 220);
        assertEquals(0, FurnaceProgress.sample(empty.window(), 300).cookFraction());
        BotState blocked = advancing.apply(new Protocol.Slot(3, 2, new Protocol.ItemData("minecraft:iron_ingot", 64, 0, 0, false, null)), 220);
        assertEquals(.2f, FurnaceProgress.sample(blocked.window(), 300).cookFraction());
        assertFalse(FurnaceProgress.sample(blocked.window(), 300).cooking());
        assertEquals(.2f, FurnaceProgress.sample(advancing.window(), 50).cookFraction());
        assertEquals(FurnaceProgress.Frame.EMPTY, FurnaceProgress.sample(advancing.apply(new Protocol.WindowClose(3), 250).window(), 300));
    }
}
