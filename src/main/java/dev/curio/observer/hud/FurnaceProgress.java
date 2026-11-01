package dev.curio.observer.hud;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;

/** Server-timed furnace animation without recipe or fuel-duration guesses. */
public final class FurnaceProgress {
    private static final double TICK_MS = 50.0;
    private static final long MAX_PREDICTION_MS = 250;
    public record Frame(float fuelFraction, float cookFraction, boolean burning, boolean cooking,
                        float remainingCookTicks) {
        public static final Frame EMPTY = new Frame(0, 0, false, false, -1);
        public boolean cookKnown() { return remainingCookTicks >= 0; }
    }

    private FurnaceProgress() {}

    public static Frame sample(BotState.Window window, long now) {
        if (window == null || !window.open() || !("furnace".equals(window.kind()) || "blast_furnace".equals(window.kind()) || "smoker".equals(window.kind())) || window.furnace() == null) return Frame.EMPTY;
        BotState.Furnace furnace = window.furnace();
        Protocol.FurnaceData data = furnace.data();
        long elapsed = Math.max(0, now - furnace.burnReceivedAt());
        long cookElapsed = Math.max(0, now - furnace.cookReceivedAt());
        // Lit fuel burns even if the input is empty or the output is blocked. Never predict a refuel.
        double burn = data.burnTime() == null ? 0 : Math.max(0, data.burnTime() - elapsed / TICK_MS);
        float fuelFraction = data.fuelTime() == null || data.fuelTime() <= 0 ? 0 : fraction(burn, data.fuelTime());
        boolean burning = burn > 0;
        if (data.cookTime() == null || data.cookTimeTotal() == null || data.cookTimeTotal() <= 0
                || window.slots().isEmpty() || window.slots().get(0) == null) {
            return new Frame(fuelFraction, 0, burning, false, -1);
        }
        // Only observed forward motion enables a short prediction. Fuel-only snapshots retain the
        // cook timestamp, so repeated cached cook values cannot keep a blocked recipe animating.
        double fuelAtCookUpdate = data.burnTime() == null ? 0
                : Math.max(0, data.burnTime() + (furnace.burnReceivedAt() - furnace.cookReceivedAt()) / TICK_MS);
        double predictedTicks = furnace.cooking() && data.burnTime() != null
                ? Math.min(Math.min(cookElapsed, MAX_PREDICTION_MS) / TICK_MS, fuelAtCookUpdate) : 0;
        double cooked = Math.min(data.cookTimeTotal(), data.cookTime() + predictedTicks);
        boolean cooking = furnace.cooking() && burning && cookElapsed <= MAX_PREDICTION_MS && cooked < data.cookTimeTotal();
        return new Frame(fuelFraction, fraction(cooked, data.cookTimeTotal()), burning, cooking,
                (float) Math.max(0, data.cookTimeTotal() - cooked));
    }

    private static float fraction(double value, int total) {
        return (float) Math.max(0, Math.min(1, value / total));
    }
}
