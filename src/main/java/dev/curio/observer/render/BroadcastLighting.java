package dev.curio.observer.render;

/** Gamma used for the client lightmap while the dashboard's "brighten dark areas" switch is on. */
public final class BroadcastLighting {
    /** Vanilla's slider tops out at 1.0 ("Bright"); larger values keep lifting caves toward daylight. */
    public static final double MAX_GAMMA = 15;

    private BroadcastLighting() {}

    /**
     * Night-vision strength reported to shader packs (Iris {@code nightVision} uniform): gamma 1 ("Bright") adds
     * nothing, each further step adds a quarter, and gamma 5 or more is full night vision.
     */
    public static float shaderNightVision(double gamma) {
        if (!Double.isFinite(gamma)) return 0;
        return (float) Math.max(0, Math.min(1, (gamma - 1) / 4));
    }

    /** The dashboard value replaces the player's own setting only while brightening is active. */
    public static double gamma(double vanilla, boolean active, double requested) {
        if (!active || !Double.isFinite(requested)) return vanilla;
        return Math.max(0, Math.min(MAX_GAMMA, requested));
    }
}
